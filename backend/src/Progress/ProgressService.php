<?php
declare(strict_types=1);
namespace Athar\Progress;
use Athar\Domain\QuranIndex;
use Athar\Domain\NightPrayer;
use Athar\Catalog\CatalogService;
use Athar\Http\ApiError;
use PDO;
use Throwable;

final class ProgressService
{
    private PDO $db; private CatalogService $catalog;
    public function __construct(PDO $db,CatalogService $catalog){$this->db=$db;$this->catalog=$catalog;}
    public function position(int $user): ?array
    {
        $q=$this->db->prepare('SELECT surah,ayah,revision,updated_at FROM quran_positions WHERE user_id=?');$q->execute([$user]);$row=$q->fetch();return $row?:null;
    }
    public function savePosition(int $user,array $data): array
    {
        $surah=$this->integer($data,'surah',1,114);$ayah=$this->integer($data,'ayah',1,286);QuranIndex::standard()->globalIndex($surah,$ayah);$expected=$this->integer($data,'expected_revision',0,2147483647);
        $this->db->beginTransaction();
        try{
            $q=$this->db->prepare('SELECT surah,ayah,revision FROM quran_positions WHERE user_id=? FOR UPDATE');$q->execute([$user]);$current=$q->fetch();
            if(($current?(int)$current['revision']:0)!==$expected)throw new ApiError('conflict',409,['current'=>$current?:null]);
            $this->db->prepare('INSERT INTO quran_positions (user_id,surah,ayah,revision,updated_at) VALUES (?,?,?,1,UTC_TIMESTAMP()) ON DUPLICATE KEY UPDATE surah=VALUES(surah),ayah=VALUES(ayah),revision=revision+1,updated_at=UTC_TIMESTAMP()')->execute([$user,$surah,$ayah]);$this->db->commit();return (array)$this->position($user);
        }catch(Throwable $e){if($this->db->inTransaction())$this->db->rollBack();throw $e;}
    }
    public function bookmarks(int $user,array $params=[]): array
    {
        // A canonical Quran has only 6236 possible coordinates. Legacy readers need
        // the complete set: truncation makes a saved ayah appear unsaved.
        return $this->collectionQuery('SELECT id,surah,ayah,label,created_at FROM quran_bookmarks WHERE user_id=? ORDER BY created_at DESC,id DESC', $user, $params, false);
    }
    public function addBookmark(int $user,array $data): array
    {
        $surah=$this->integer($data,'surah',1,114);$ayah=$this->integer($data,'ayah',1,286);QuranIndex::standard()->globalIndex($surah,$ayah);$label=$data['label']??'';if(!is_string($label)||mb_strlen($label,'UTF-8')>120)throw new ApiError('invalid_fields',422,['label'=>'invalid']);
        $this->db->prepare('INSERT INTO quran_bookmarks (user_id,surah,ayah,label) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE label=VALUES(label)')->execute([$user,$surah,$ayah,$label]);$q=$this->db->prepare('SELECT id,surah,ayah,label,created_at FROM quran_bookmarks WHERE user_id=? AND surah=? AND ayah=?');$q->execute([$user,$surah,$ayah]);return $q->fetch();
    }
    public function deleteBookmark(int $user,int $id): void{$q=$this->db->prepare('DELETE FROM quran_bookmarks WHERE user_id=? AND id=?');$q->execute([$user,$id]);if(!$q->rowCount())throw new ApiError('not_found',404);}
    public function deleteBookmarkAt(int $user,array $data):void
    {
        $surah=$this->integer($data,'surah',1,114);$ayah=$this->integer($data,'ayah',1,286);QuranIndex::standard()->globalIndex($surah,$ayah);
        $this->db->prepare('DELETE FROM quran_bookmarks WHERE user_id=? AND surah=? AND ayah=?')->execute([$user,$surah,$ayah]);
    }
    public function favorites(int $user,array $params=[]): array
    {
        return $this->collectionQuery("SELECT f.target_type,f.target_id,COALESCE(b.title,l.title,r.title) title,f.created_at FROM user_favorites f LEFT JOIN books b ON f.target_type='book' AND b.id=f.target_id AND b.is_published=1 LEFT JOIN lessons l ON f.target_type='lesson' AND l.id=f.target_id AND l.is_published=1 LEFT JOIN series r ON f.target_type='series' AND r.id=f.target_id AND r.is_published=1 WHERE f.user_id=? AND (b.id IS NOT NULL OR (l.id IS NOT NULL AND (l.scholar_id IS NULL OR EXISTS(SELECT 1 FROM scholars s WHERE s.id=l.scholar_id AND s.is_published=1)) AND (l.series_id IS NULL OR EXISTS(SELECT 1 FROM series lr LEFT JOIN scholars rs ON rs.id=lr.scholar_id WHERE lr.id=l.series_id AND lr.is_published=1 AND (lr.scholar_id IS NULL OR rs.is_published=1)))) OR (r.id IS NOT NULL AND (r.scholar_id IS NULL OR EXISTS(SELECT 1 FROM scholars s WHERE s.id=r.scholar_id AND s.is_published=1)))) ORDER BY f.id DESC", $user, $params);
    }

    public function isFavorite(int $user,string $type,int $id):bool
    {
        if(!in_array($type,['book','lesson','series'],true)||$id<1)throw new ApiError('invalid_fields',422);
        $q=$this->db->prepare('SELECT 1 FROM user_favorites WHERE user_id=? AND target_type=? AND target_id=?');$q->execute([$user,$type,$id]);return (bool)$q->fetchColumn();
    }
    public function favorite(int $user,string $type,int $id,bool $saved): void
    {
        $types=['book'=>'books','lesson'=>'lessons','series'=>'series'];if(!isset($types[$type]))throw new ApiError('invalid_fields',422,['target_type'=>'invalid']);
        if($saved){$this->catalog->detail($types[$type],$id);$this->db->prepare('INSERT IGNORE INTO user_favorites (user_id,target_type,target_id) VALUES (?,?,?)')->execute([$user,$type,$id]);}
        else $this->db->prepare('DELETE FROM user_favorites WHERE user_id=? AND target_type=? AND target_id=?')->execute([$user,$type,$id]);
    }
    public function activity(int $user,string $eventId,string $kind,?string $activityDate=null): void
    {
        $columns=['quran_session'=>'quran_sessions','lesson_completion'=>'lesson_completions','adhkar_completion'=>'adhkar_completions','book_download'=>'book_downloads','book_download_completed'=>'book_completions'];
        if(!preg_match('/^[a-f0-9]{8}-[a-f0-9]{4}-[1-8][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/i',$eventId)||!isset($columns[$kind]))throw new ApiError('invalid_fields',422,['event_id'=>'invalid']);
        $date=$this->activityDate($activityDate);
        $owns=!$this->db->inTransaction();
        if($owns)$this->db->beginTransaction();
        try{
            $q=$this->db->prepare('SELECT activity_type,activity_date FROM activity_events WHERE user_id=? AND event_id=? FOR UPDATE');
            $q->execute([$user,$eventId]);
            $old=$q->fetch();
            if($old&&((string)$old['activity_type']!==$kind||($activityDate!==null&&(string)$old['activity_date']!==$date)))throw new ApiError('conflict',409,['event_id'=>'reused']);
            if(!$old){
                $this->db->prepare('INSERT INTO activity_events (user_id,event_id,activity_type,activity_date,created_at) VALUES (?,?,?,?,UTC_TIMESTAMP())')->execute([$user,$eventId,$kind,$date]);
                $column=$columns[$kind];
                $this->db->prepare("INSERT INTO account_daily_stats (user_id,activity_date,{$column}) VALUES (?,?,1) ON DUPLICATE KEY UPDATE {$column}=LEAST(65535,{$column}+1)")->execute([$user,$date]);
            }
            if($owns)$this->db->commit();
        }catch(Throwable $e){
            if($owns&&$this->db->inTransaction())$this->db->rollBack();
            throw $e;
        }
    }

    /** Explicit account records; resume position is never elapsed watch/listen time. */
    public function activitySummary(int $user):array
    {
        $today=gmdate('Y-m-d');$owns=!$this->db->inTransaction();if($owns)$this->db->beginTransaction();
        try{
            $q=$this->db->prepare('SELECT COALESCE(SUM(adhkar_completions),0) adhkar_completions,COALESCE(SUM(lesson_completions),0) lesson_completions,COALESCE(SUM(book_downloads),0) legacy_book_download_events,COALESCE(SUM(book_completions),0) completed_book_downloads,COALESCE(SUM(quran_sessions),0) legacy_quran_sessions,COALESCE(SUM(CASE WHEN adhkar_completions>0 OR lesson_completions>0 OR book_completions>0 THEN 1 ELSE 0 END),0) active_days FROM account_daily_stats WHERE user_id=? AND activity_date<=?');$q->execute([$user,$today]);$totals=$q->fetch();
            $q=$this->db->prepare('SELECT COALESCE(SUM(CASE WHEN position_seconds>0 OR is_complete=1 THEN 1 ELSE 0 END),0) lessons_with_progress,COALESCE(SUM(CASE WHEN is_complete=1 THEN 1 ELSE 0 END),0) completed_lessons FROM video_progress WHERE user_id=?');$q->execute([$user]);$lessons=$q->fetch();
            $q=$this->db->prepare('SELECT activity_date,lesson_completions,adhkar_completions,book_completions FROM account_daily_stats WHERE user_id=? AND activity_date BETWEEN DATE_SUB(?,INTERVAL 13 DAY) AND ? AND (lesson_completions>0 OR adhkar_completions>0 OR book_completions>0) ORDER BY activity_date');$q->execute([$user,$today,$today]);
            $recent=array_map(static fn(array $day):array=>['date'=>(string)$day['activity_date'],'lesson_completions'=>(int)$day['lesson_completions'],'adhkar_completions'=>(int)$day['adhkar_completions'],'completed_book_downloads'=>(int)$day['book_completions']],$q->fetchAll());
            if($owns)$this->db->commit();
            return ['schema_version'=>2,'owner_id'=>$user,'generated_date'=>$today,
                'availability'=>['reading_sessions'=>false,'listening_history'=>false,'adhkar_completions'=>true,'lesson_progress'=>true,'book_downloads'=>true],
                'recorded'=>array_map('intval',array_merge($totals,$lessons)),'recent'=>$recent];
        }catch(Throwable $e){if($owns&&$this->db->inTransaction())$this->db->rollBack();throw $e;}
    }

    public function lessonProgress(int $user,int $lesson): ?array{$this->catalog->detail('lessons',$lesson);$q=$this->db->prepare('SELECT lesson_id,position_seconds,duration_seconds,is_complete,updated_at FROM video_progress WHERE user_id=? AND lesson_id=?');$q->execute([$user,$lesson]);$row=$q->fetch();return $row?:null;}
    public function saveLessonProgress(int $user,array $data): array
    {
        $id=$this->integer($data,'lesson_id',1,2147483647);$this->catalog->detail('lessons',$id);$position=$this->integer($data,'position_seconds',0,86400);$duration=$this->integer($data,'duration_seconds',0,86400);if($duration&&$position>$duration)throw new ApiError('invalid_fields',422,['position_seconds'=>'out_of_range']);$complete=($data['is_complete']??false)===true;
        $this->db->beginTransaction();
        try{$q=$this->db->prepare('SELECT is_complete FROM video_progress WHERE user_id=? AND lesson_id=? FOR UPDATE');$q->execute([$user,$id]);$was=$q->fetchColumn();$this->db->prepare('INSERT INTO video_progress (user_id,lesson_id,position_seconds,duration_seconds,is_complete,updated_at) VALUES (?,?,?,?,?,UTC_TIMESTAMP()) ON DUPLICATE KEY UPDATE position_seconds=VALUES(position_seconds),duration_seconds=VALUES(duration_seconds),is_complete=GREATEST(is_complete,VALUES(is_complete)),updated_at=UTC_TIMESTAMP()')->execute([$user,$id,$position,$duration,$complete?1:0]);
            if($complete&&!(bool)$was){$this->activity($user,(string)($data['event_id']??''),'lesson_completion');}$this->db->commit();return (array)$this->lessonProgress($user,$id);
        }catch(Throwable $e){if($this->db->inTransaction())$this->db->rollBack();throw $e;}
    }
    public function khatmah(int $user): ?array
    {
        return (new Khatmah($this->db))->get($user);
    }
    public function saveKhatmah(int $user,array $data): array
    {
        return (new Khatmah($this->db))->save($user,$data);
    }
    public function comments(int $user,int $lesson): array{$this->catalog->detail('lessons',$lesson);$q=$this->db->prepare("SELECT c.id,c.body,c.status,c.created_at,u.display_name,u.username FROM lesson_comments c JOIN users u ON u.id=c.user_id WHERE c.lesson_id=? AND c.parent_id IS NULL AND (c.status='visible' OR (c.user_id=? AND c.status='pending')) ORDER BY c.id DESC LIMIT 50");$q->execute([$lesson,$user]);return $q->fetchAll();}
    public function addComment(int $user,int $lesson,string $body): array
    {
        $this->catalog->detail('lessons',$lesson);$body=trim($body);if(mb_strlen($body,'UTF-8')<2||mb_strlen($body,'UTF-8')>2000)throw new ApiError('invalid_fields',422,['body'=>'invalid']);$this->db->prepare("INSERT INTO lesson_comments (lesson_id,user_id,body,status) VALUES (?,?,?,'pending')")->execute([$lesson,$user,$body]);return ['id'=>(int)$this->db->lastInsertId(),'status'=>'pending'];
    }
    public function preferences(int $user,?array $update=null): array
    {
        $q=$this->db->prepare('SELECT preferences_json FROM users WHERE id=?');$q->execute([$user]);$current=json_decode((string)$q->fetchColumn(),true)?:[];
        if($update!==null){$allowed=['language','theme','quran_font_size'];foreach($update as $key=>$value){if(!in_array($key,$allowed,true))throw new ApiError('invalid_fields',422,[$key=>'unsupported']);if(($key==='language'&&!in_array($value,['ar','en'],true))||($key==='theme'&&!in_array($value,['light','dark','system'],true))||($key==='quran_font_size'&&(!is_int($value)||$value<18||$value>48)))throw new ApiError('invalid_fields',422,[$key=>'invalid']);$current[$key]=$value;}$this->db->prepare('UPDATE users SET preferences_json=? WHERE id=?')->execute([json_encode($current,JSON_THROW_ON_ERROR),$user]);}
        return $current;
    }
    public function adhkarStats(int $user):array
    {
        $summary=$this->db->prepare(
            'SELECT COALESCE(SUM(adhkar_completions),0) total_completions,
                    COALESCE(SUM(CASE WHEN activity_date=UTC_DATE() THEN adhkar_completions ELSE 0 END),0) today_completions,
                    COALESCE(SUM(CASE WHEN adhkar_completions>0 THEN 1 ELSE 0 END),0) active_days
             FROM account_daily_stats
             WHERE user_id=?'
        );
        $summary->execute([$user]);
        $totals=$summary->fetch()?:[
            'total_completions'=>0,
            'today_completions'=>0,
            'active_days'=>0,
        ];

        $categories=$this->db->prepare(
            'SELECT a.category,
                    COUNT(*) item_count,
                    SUM(CASE
                        WHEN p.user_id IS NOT NULL
                         AND DATE(p.updated_at)=UTC_DATE()
                         AND p.repeat_count=a.repeat_count
                         AND p.current_count>=p.repeat_count
                        THEN 1 ELSE 0 END) completed_items,
                    SUM(CASE
                        WHEN p.user_id IS NOT NULL
                         AND DATE(p.updated_at)=UTC_DATE()
                         AND p.repeat_count=a.repeat_count
                        THEN LEAST(p.current_count,a.repeat_count)
                        ELSE 0 END) current_count,
                    SUM(CASE
                        WHEN a.repeat_count IS NOT NULL AND a.repeat_count>0
                        THEN a.repeat_count ELSE 0 END) target_count
             FROM adhkar_items a
             LEFT JOIN adhkar_progress p
               ON p.user_id=?
              AND p.item_key=CONCAT("curated:",a.id)
             WHERE a.is_published=1
               AND a.is_reviewed=1
               AND a.repeat_count IS NOT NULL
               AND a.repeat_count>0
             GROUP BY a.category
             ORDER BY a.category'
        );
        $categories->execute([$user]);

        $recent=$this->db->prepare(
            'SELECT activity_date,adhkar_completions
             FROM account_daily_stats
             WHERE user_id=?
               AND activity_date>=DATE_SUB(UTC_DATE(),INTERVAL 6 DAY)
             ORDER BY activity_date'
        );
        $recent->execute([$user]);

        return [
            'date'=>gmdate('Y-m-d'),
            'today_completions'=>(int)$totals['today_completions'],
            'total_completions'=>(int)$totals['total_completions'],
            'active_days'=>(int)$totals['active_days'],
            'categories'=>array_map(
                static fn(array $row):array=>[
                    'category'=>(string)$row['category'],
                    'item_count'=>(int)$row['item_count'],
                    'completed_items'=>(int)$row['completed_items'],
                    'current_count'=>(int)$row['current_count'],
                    'target_count'=>(int)$row['target_count'],
                ],
                $categories->fetchAll(),
            ),
            'recent'=>array_map(
                static fn(array $row):array=>[
                    'date'=>(string)$row['activity_date'],
                    'completions'=>(int)$row['adhkar_completions'],
                ],
                $recent->fetchAll(),
            ),
        ];
    }

    public function adhkar(int $user,array $params=[]):array
    {
        $projection="item_key,CASE WHEN DATE(updated_at)=UTC_DATE() THEN current_count ELSE 0 END current_count,repeat_count,revision,UTC_DATE() progress_date,updated_at";
        if(array_key_exists('item_id',$params)){
            $item=$params['item_id'];
            if((!is_int($item)&&!is_string($item))||!preg_match('/^[1-9][0-9]{0,9}$/D',(string)$item)||(int)$item>2147483647)throw new ApiError('invalid_fields',422,['item_id'=>'invalid']);
            $q=$this->db->prepare("SELECT {$projection} FROM adhkar_progress WHERE user_id=? AND item_key=?");
            $q->execute([$user,'curated:'.(int)$item]);
            return $q->fetchAll();
        }
        $q=$this->db->prepare("SELECT {$projection} FROM adhkar_progress WHERE user_id=? ORDER BY updated_at DESC LIMIT 500");
        $q->execute([$user]);
        return $q->fetchAll();
    }

    public function saveAdhkar(int $user,array $data):array
    {
        $id=$this->integer($data,'item_id',1,2147483647);
        $item=$this->catalog->detail('adhkar',$id);
        if($item['repeat_count']===null)throw new ApiError('invalid_fields',422,['repeat_count'=>'not_fixed']);
        $repeat=(int)$item['repeat_count'];
        if($repeat<1||$repeat>65535)throw new ApiError('invalid_fields',422,['repeat_count'=>'unsupported']);

        $count=$this->integer($data,'current_count',0,$repeat);
        $expected=$this->integer($data,'expected_revision',0,2147483647);
        if(array_key_exists('progress_date',$data)&&!is_string($data['progress_date']))throw new ApiError('invalid_fields',422,['progress_date'=>'invalid']);
        $progressDate=$this->activityDate($data['progress_date']??null);
        $today=gmdate('Y-m-d');
        $storedCount=$progressDate===$today?$count:0;
        $key='curated:'.$id;

        $this->db->beginTransaction();
        try{
            $q=$this->db->prepare('SELECT current_count,repeat_count,revision,DATE(updated_at) progress_date FROM adhkar_progress WHERE user_id=? AND item_key=? FOR UPDATE');
            $q->execute([$user,$key]);
            $current=$q->fetch();
            if(($current?(int)$current['revision']:0)!==$expected)throw new ApiError('conflict',409,['current'=>$current?:null]);

            $previousToday=$current&&(string)$current['progress_date']===$today
                ?(int)$current['current_count']
                :0;

            $this->db->prepare('INSERT INTO adhkar_progress (user_id,item_key,current_count,repeat_count,revision,updated_at) VALUES (?,?,?,?,1,UTC_TIMESTAMP()) ON DUPLICATE KEY UPDATE current_count=VALUES(current_count),repeat_count=VALUES(repeat_count),revision=revision+1,updated_at=UTC_TIMESTAMP()')
                ->execute([$user,$key,$storedCount,$repeat]);

            if($count===$repeat&&($progressDate!==$today||$previousToday<$repeat)){
                $this->activity(
                    $user,
                    (string)($data['event_id']??''),
                    'adhkar_completion',
                    $progressDate,
                );
            }

            $this->db->commit();
            return [
                'item_key'=>$key,
                'current_count'=>$storedCount,
                'repeat_count'=>$repeat,
                'revision'=>$expected+1,
                'progress_date'=>$today,
            ];
        }catch(Throwable $e){
            if($this->db->inTransaction())$this->db->rollBack();
            throw $e;
        }
    }

    private function activityDate(?string $date):string
    {
        $today=gmdate('Y-m-d');
        if($date===null)return $today;
        if(!preg_match('/^\d{4}-\d{2}-\d{2}$/D',$date))throw new ApiError('invalid_fields',422,['activity_date'=>'invalid']);
        [$year,$month,$day]=array_map('intval',explode('-',$date));
        if(!checkdate($month,$day,$year))throw new ApiError('invalid_fields',422,['activity_date'=>'invalid']);
        $minimum=gmdate('Y-m-d',time()-366*86400);
        if($date<$minimum||$date>$today)throw new ApiError('invalid_fields',422,['activity_date'=>'out_of_range']);
        return $date;
    }

    /** Page the entire owned, visible SQL result, never a pre-truncated array. */
    private function collectionQuery(string $sql,int $user,array $params,bool $legacyLimit=true):array
    {
        $paged=$params['paged']??'0';
        if(!is_string($paged)||!in_array($paged,['0','1'],true))throw new ApiError('invalid_fields',422,['paged'=>'invalid']);
        if($paged==='0'){
            $q=$this->db->prepare($sql.($legacyLimit?' LIMIT 500':''));$q->execute([$user]);return $q->fetchAll();
        }
        $read=function(string $key,int $default,int $max)use($params):int{
            $value=$params[$key]??(string)$default;
            if(!is_string($value)||!preg_match('/^[1-9][0-9]{0,5}$/D',$value)||(int)$value>$max)throw new ApiError('invalid_fields',422,[$key=>'invalid']);
            return (int)$value;
        };
        $page=$read('page',1,10000);$size=$read('per_page',20,50);
        $owns=!$this->db->inTransaction();if($owns)$this->db->beginTransaction();
        try{
            $count=$this->db->prepare('SELECT COUNT(*) FROM ('.$sql.') saved_collection');$count->execute([$user]);$total=(int)$count->fetchColumn();
            $offset=($page-1)*$size;
            $q=$this->db->prepare($sql.' LIMIT '.$size.' OFFSET '.$offset);$q->execute([$user]);$rows=$q->fetchAll();
            if($owns)$this->db->commit();
            return ['items'=>$rows,'pagination'=>['page'=>$page,'per_page'=>$size,'total'=>$total,'has_more'=>$offset+count($rows)<$total]];
        }catch(Throwable $e){if($owns&&$this->db->inTransaction())$this->db->rollBack();throw $e;}
    }

    private function integer(array $data,string $key,int $min,int $max): int { $value=$data[$key]??null;if(!is_int($value)||$value<$min||$value>$max)throw new ApiError('invalid_fields',422,[$key=>'invalid']);return $value; }
}
