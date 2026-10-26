<?php
declare(strict_types=1);
namespace Athar\Learning;
use Athar\Catalog\CatalogService;
use Athar\Progress\ProgressService;
use PDO;
final class LessonService
{
    private PDO $db;private CatalogService $catalog;
    public function __construct(PDO $db){$this->db=$db;$this->catalog=new CatalogService($db);}
    private function scope():string{return 'l.series_id=? AND l.is_published=1 AND (l.scholar_id IS NULL OR s.is_published=1)';}
    public function playlist(int $user,int $series,array $params=[]):array
    {
        $parent=$this->catalog->detail('series',$series,false);$page=max(1,min(10000,(int)($params['page']??1)));$size=max(1,min(50,(int)($params['per_page']??20)));$offset=isset($params['_offset'])?max(0,(int)$params['_offset']):($page-1)*$size;
        $q=$this->db->prepare('SELECT COUNT(*) FROM lessons l LEFT JOIN scholars s ON s.id=l.scholar_id WHERE '.$this->scope());$q->execute([$series]);$total=(int)$q->fetchColumn();
        $q=$this->db->prepare('SELECT l.id,l.title,l.youtube_id,l.scholar_id,l.series_id,l.sort_order,s.name scholar_name,v.position_seconds,v.duration_seconds,COALESCE(v.is_complete,0) is_complete FROM lessons l LEFT JOIN scholars s ON s.id=l.scholar_id LEFT JOIN video_progress v ON v.lesson_id=l.id AND v.user_id=? WHERE '.$this->scope().' ORDER BY l.sort_order ASC,l.id ASC LIMIT '.$size.' OFFSET '.$offset);$q->execute([$user,$series]);$items=$q->fetchAll();
        foreach($items as $i=>&$row){$row['id']=(int)$row['id'];$row['lesson_number']=$offset+$i+1;$row['is_complete']=(bool)$row['is_complete'];$row['position_seconds']=(int)($row['position_seconds']??0);$row['duration_seconds']=$row['duration_seconds']===null?null:(int)$row['duration_seconds'];$row['scholar_name']=$row['scholar_name']?:($parent['scholar_name']??null);}unset($row);
        return ['items'=>$items,'pagination'=>['page'=>$page,'per_page'=>$size,'total'=>$total,'has_more'=>$offset+$size<$total]];
    }
    public function series(int $user,int $id):array
    {
        $series=$this->catalog->detail('series',$id,false);
        $q=$this->db->prepare('SELECT COUNT(*) lesson_count,COALESCE(SUM(v.is_complete=1),0) completed_count FROM lessons l LEFT JOIN scholars s ON s.id=l.scholar_id LEFT JOIN video_progress v ON v.lesson_id=l.id AND v.user_id=? WHERE '.$this->scope());$q->execute([$user,$id]);$stats=$q->fetch();
        $q=$this->db->prepare('SELECT l.id FROM lessons l LEFT JOIN scholars s ON s.id=l.scholar_id LEFT JOIN video_progress v ON v.lesson_id=l.id AND v.user_id=? WHERE '.$this->scope().' AND COALESCE(v.is_complete,0)=0 ORDER BY (COALESCE(v.position_seconds,0)>0) DESC,v.updated_at DESC,l.sort_order ASC,l.id ASC LIMIT 1');$q->execute([$user,$id]);$resume=$q->fetchColumn();
        $q=$this->db->prepare("SELECT 1 FROM user_favorites WHERE user_id=? AND target_type='series' AND target_id=?");$q->execute([$user,$id]);$series['is_favorite']=(bool)$q->fetchColumn();
        $series['lesson_count']=(int)$stats['lesson_count'];$series['completed_count']=(int)$stats['completed_count'];$series['resume_lesson_id']=$resume===false?null:(int)$resume;return $series;
    }
    public function watch(int $user,int $id):array
    {
        $lesson=$this->catalog->detail('lessons',$id);$progress=new ProgressService($this->db,$this->catalog);$lesson['progress']=$progress->lessonProgress($user,$id);
        $q=$this->db->prepare("SELECT 1 FROM user_favorites WHERE user_id=? AND target_type='lesson' AND target_id=?");$q->execute([$user,$id]);$lesson['is_favorite']=(bool)$q->fetchColumn();$lesson['series']=null;$lesson['playlist_preview']=[];
        if($lesson['series_id']){$series=$this->series($user,(int)$lesson['series_id']);$q=$this->db->prepare('SELECT COUNT(*) FROM lessons l LEFT JOIN scholars s ON s.id=l.scholar_id WHERE '.$this->scope().' AND (l.sort_order<? OR (l.sort_order=? AND l.id<=?))');$q->execute([$lesson['series_id'],$lesson['sort_order'],$lesson['sort_order'],$id]);$lesson['lesson_number']=(int)$q->fetchColumn();$lesson['series']=$series;$lesson['playlist_preview']=$this->playlist($user,(int)$lesson['series_id'],['per_page'=>3,'_offset'=>$lesson['lesson_number']-1])['items'];}
        $lesson['discussion']=(new DiscussionService($this->db))->page($user,$id,['per_page'=>1]);return $lesson;
    }
}
