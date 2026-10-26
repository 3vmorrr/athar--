<?php
declare(strict_types=1);

namespace Athar\Engagement;

use PDO;
use Throwable;

final class NewContentFactService
{
    public function __construct(private PDO $db) {}

    public function observeSeries(int $seriesId): array
    {
        return $this->observe('series',$seriesId);
    }

    public function observeLesson(int $lessonId): array
    {
        return $this->observe('lesson',$lessonId);
    }

    public function scanAll(int $limit=200): array
    {
        $limit=max(1,min(1000,$limit));$observed=['series'=>0,'lesson'=>0];
        $q=$this->db->query('SELECT id FROM series ORDER BY id DESC LIMIT '.$limit);
        foreach($q->fetchAll() as $row){$this->observeSeries((int)$row['id']);$observed['series']++;}
        $q=$this->db->query('SELECT id FROM lessons ORDER BY id DESC LIMIT '.$limit);
        foreach($q->fetchAll() as $row){$this->observeLesson((int)$row['id']);$observed['lesson']++;}
        return $observed;
    }

    public function eligibleRecipients(int $factId): array
    {
        $fact=$this->fact($factId);
        if(!$fact||$fact['status']!=='active')return [];
        if(!$this->factVisible($fact)){
            $this->cancelFacts((string)$fact['target_type'],(int)$fact['target_id']);
            return [];
        }

        $q=$this->db->prepare("SELECT DISTINCT s.user_id,
            CASE
                WHEN JSON_UNQUOTE(JSON_EXTRACT(u.preferences_json,'$.language'))='en' THEN 'en'
                ELSE 'ar'
            END locale
            FROM engagement_content_fact_sources s
            JOIN users u ON u.id=s.user_id
            LEFT JOIN scholar_follows sf
                ON s.follow_type='scholar' AND sf.user_id=s.user_id AND sf.scholar_id=s.follow_id
            LEFT JOIN series_follows rf
                ON s.follow_type='series' AND rf.user_id=s.user_id AND rf.series_id=s.follow_id
            WHERE s.fact_id=? AND u.email_verified_at IS NOT NULL AND u.suspended_at IS NULL
            AND (sf.user_id IS NOT NULL OR rf.user_id IS NOT NULL)
            ORDER BY s.user_id");
        $q->execute([$factId]);
        return array_map(static fn(array $row):array=>[
            'user_id'=>(int)$row['user_id'],
            'locale'=>$row['locale']==='en'?'en':'ar',
        ],$q->fetchAll());
    }

    private function observe(string $type,int $targetId): array
    {
        if($targetId<1||!in_array($type,['lesson','series'],true))return ['created'=>[],'public'=>false,'replay'=>false];
        $owns=!$this->db->inTransaction();
        if($owns)$this->db->beginTransaction();
        try{
            $current=$type==='lesson'?$this->lessonState($targetId):$this->seriesState($targetId);
            $q=$this->db->prepare('SELECT public_visible,replay_available FROM engagement_content_publication_state WHERE target_type=? AND target_id=? FOR UPDATE');
            $q->execute([$type,$targetId]);$previous=$q->fetch()?:null;
            $created=[];

            if($current===null){
                $this->cancelFacts($type,$targetId);
                if($previous!==null)$this->db->prepare('DELETE FROM engagement_content_publication_state WHERE target_type=? AND target_id=?')->execute([$type,$targetId]);
                if($owns)$this->db->commit();
                return ['created'=>[],'public'=>false,'replay'=>false];
            }

            $public=(bool)$current['public_visible'];
            $replay=$type==='lesson'&&(bool)$current['replay_available'];
            if($previous===null){
                $this->writeState($type,$targetId,$public,$replay);
                if($public){
                    $fact=$this->recordFact(
                        $type==='series'?'series_published':'lesson_published',
                        $type,$targetId,$current
                    );
                    if($fact!==null)$created[]=$fact;
                }
            }else{
                $wasPublic=(bool)$previous['public_visible'];
                $wasReplay=(bool)$previous['replay_available'];

                if(!$wasPublic&&$public){
                    $fact=$this->recordFact(
                        $type==='series'?'series_published':'lesson_published',
                        $type,$targetId,$current
                    );
                    if($fact!==null)$created[]=$fact;
                }elseif($type==='lesson'&&$wasPublic&&$public&&!$wasReplay&&$replay){
                    $fact=$this->recordFact('lesson_replay_available','lesson',$targetId,$current);
                    if($fact!==null)$created[]=$fact;
                }

                if($wasPublic&&!$public)$this->cancelFacts($type,$targetId);
                $this->writeState($type,$targetId,$public,$replay);
            }

            if($owns)$this->db->commit();
            return ['created'=>$created,'public'=>$public,'replay'=>$replay];
        }catch(Throwable $failure){
            if($owns&&$this->db->inTransaction())$this->db->rollBack();
            throw $failure;
        }
    }

    private function recordFact(string $factType,string $targetType,int $targetId,array $state): ?int
    {
        $dedupe=hash('sha256','content-fact:'.$factType.':'.$targetType.':'.$targetId);
        $payload=[
            'kind'=>$factType,
            'target_type'=>$targetType,
            'target_id'=>$targetId,
            'scholar_id'=>$state['scholar_id'],
            'series_id'=>$state['series_id'],
        ];
        $q=$this->db->prepare("INSERT IGNORE INTO engagement_content_facts
            (fact_type,target_type,target_id,scholar_id,series_id,payload_json,dedupe_key,status,created_at,updated_at)
            VALUES (?,?,?,?,?,?,?,'active',UTC_TIMESTAMP(),UTC_TIMESTAMP())");
        $q->execute([
            $factType,$targetType,$targetId,$state['scholar_id'],$state['series_id'],
            json_encode($payload,JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES|JSON_THROW_ON_ERROR),$dedupe
        ]);
        if($q->rowCount()!==1)return null;
        $factId=(int)$this->db->lastInsertId();
        $this->captureSources($factId,$state);
        return $factId;
    }

    private function captureSources(int $factId,array $state): void
    {
        $scholar=$state['scholar_id'];
        if($scholar!==null){
            $q=$this->db->prepare("INSERT IGNORE INTO engagement_content_fact_sources(fact_id,user_id,follow_type,follow_id,created_at)
                SELECT ?,f.user_id,'scholar',f.scholar_id,UTC_TIMESTAMP()
                FROM scholar_follows f JOIN users u ON u.id=f.user_id
                WHERE f.scholar_id=? AND u.email_verified_at IS NOT NULL AND u.suspended_at IS NULL");
            $q->execute([$factId,$scholar]);
        }
        $series=$state['series_id'];
        if($series!==null){
            $q=$this->db->prepare("INSERT IGNORE INTO engagement_content_fact_sources(fact_id,user_id,follow_type,follow_id,created_at)
                SELECT ?,f.user_id,'series',f.series_id,UTC_TIMESTAMP()
                FROM series_follows f JOIN users u ON u.id=f.user_id
                WHERE f.series_id=? AND u.email_verified_at IS NOT NULL AND u.suspended_at IS NULL");
            $q->execute([$factId,$series]);
        }
    }

    private function writeState(string $type,int $id,bool $public,bool $replay): void
    {
        $this->db->prepare("INSERT INTO engagement_content_publication_state
            (target_type,target_id,public_visible,replay_available,updated_at)
            VALUES (?,?,?,?,UTC_TIMESTAMP())
            ON DUPLICATE KEY UPDATE public_visible=VALUES(public_visible),
            replay_available=VALUES(replay_available),updated_at=UTC_TIMESTAMP()")
            ->execute([$type,$id,(int)$public,(int)$replay]);
    }

    private function cancelFacts(string $type,int $targetId): void
    {
        $this->db->prepare("UPDATE engagement_content_facts SET status='cancelled',updated_at=UTC_TIMESTAMP()
            WHERE target_type=? AND target_id=? AND status='active'")->execute([$type,$targetId]);
    }

    private function fact(int $id): ?array
    {
        $q=$this->db->prepare('SELECT * FROM engagement_content_facts WHERE id=?');
        $q->execute([$id]);return $q->fetch()?:null;
    }

    private function factVisible(array $fact): bool
    {
        $state=$fact['target_type']==='lesson'
            ?$this->lessonState((int)$fact['target_id'])
            :$this->seriesState((int)$fact['target_id']);
        return $state!==null&&(bool)$state['public_visible'];
    }

    private function seriesState(int $id): ?array
    {
        $q=$this->db->prepare("SELECT r.id,r.is_published,r.scholar_id,s.is_published scholar_published
            FROM series r LEFT JOIN scholars s ON s.id=r.scholar_id WHERE r.id=?");
        $q->execute([$id]);$row=$q->fetch();
        if(!$row)return null;
        $public=(bool)$row['is_published']&&($row['scholar_id']===null||(bool)$row['scholar_published']);
        return [
            'public_visible'=>$public,
            'replay_available'=>false,
            'scholar_id'=>$row['scholar_id']===null?null:(int)$row['scholar_id'],
            'series_id'=>(int)$row['id'],
        ];
    }

    private function lessonState(int $id): ?array
    {
        $q=$this->db->prepare("SELECT l.id,l.is_published,l.scholar_id direct_scholar_id,s.is_published direct_scholar_published,
            l.series_id,r.is_published series_published,r.scholar_id series_scholar_id,
            rs.is_published series_scholar_published,e.replay_available
            FROM lessons l
            LEFT JOIN scholars s ON s.id=l.scholar_id
            LEFT JOIN series r ON r.id=l.series_id
            LEFT JOIN scholars rs ON rs.id=r.scholar_id
            LEFT JOIN lesson_events e ON e.lesson_id=l.id
            WHERE l.id=?");
        $q->execute([$id]);$row=$q->fetch();
        if(!$row)return null;
        $public=(bool)$row['is_published'];
        if($row['direct_scholar_id']!==null&&!(bool)$row['direct_scholar_published'])$public=false;
        if($row['series_id']!==null){
            if(!(bool)$row['series_published'])$public=false;
            if($row['series_scholar_id']!==null&&!(bool)$row['series_scholar_published'])$public=false;
        }
        $scholar=$row['direct_scholar_id']!==null?(int)$row['direct_scholar_id']:
            ($row['series_scholar_id']===null?null:(int)$row['series_scholar_id']);
        return [
            'public_visible'=>$public,
            'replay_available'=>$public&&(bool)($row['replay_available']??false),
            'scholar_id'=>$scholar,
            'series_id'=>$row['series_id']===null?null:(int)$row['series_id'],
        ];
    }
}
