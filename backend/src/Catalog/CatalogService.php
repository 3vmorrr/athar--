<?php
declare(strict_types=1);
namespace Athar\Catalog;
use Athar\Http\ApiError;
use PDO;
final class CatalogService
{
    private PDO $db;
    public function __construct(PDO $db) { $this->db=$db; }
    public function adhkarCategories(): array
    {
        $rows=$this->db->query('SELECT category,COUNT(*) item_count FROM adhkar_items WHERE is_published=1 AND is_reviewed=1 GROUP BY category ORDER BY category')->fetchAll();
        return array_map(static fn(array $row):array=>['category'=>$row['category'],'item_count'=>(int)$row['item_count']],$rows);
    }
    public function list(string $type,array $params): array
    {
        [$select,$from,$where,$order]=$this->definition($type);
        if($type==='lessons'&&($params['sort']??'')==='latest')$order='c.created_at DESC,c.id DESC';
        $page=max(1,min(10000,(int)($params['page']??1)));$size=max(1,min(50,(int)($params['per_page']??20)));$bindings=[];
        $q=trim((string)($params['q']??''));if(mb_strlen($q,'UTF-8')>100)throw new ApiError('invalid_fields',422,['q'=>'too_long']);
        if($q!==''){
            $columns=$type==='scholars'?['c.name']:($type==='books'?['c.title','c.author','c.keywords']:($type==='adhkar'?['c.title','c.formula','c.category','c.source_reference']:['c.title']));
            $where.=' AND ('.implode(' OR ',array_map(static fn(string $column):string=>$column.' LIKE ?',$columns)).')';foreach($columns as $_)$bindings[]='%'.$q.'%';
        }
        if(isset($params['scholar_id'])&&in_array($type,['series','lessons'],true)){$where.=$type==='lessons'?' AND COALESCE(c.scholar_id,r.scholar_id)=?':' AND c.scholar_id=?';$bindings[]=(int)$params['scholar_id'];}
        if($type==='lessons'&&($params['standalone']??'')==='1')$where.=' AND c.series_id IS NULL';
        if(isset($params['series_id'])&&$type==='lessons'){$where.=' AND c.series_id=?';$bindings[]=(int)$params['series_id'];}
        if($type==='adhkar'){
            if(isset($params['category'])){$where.=' AND c.category=?';$bindings[]=(string)$params['category'];}
        }
        $count=$this->db->prepare('SELECT COUNT(*) '.$from.' WHERE '.$where);$count->execute($bindings);$total=(int)$count->fetchColumn();
        $offset=($page-1)*$size;$statement=$this->db->prepare($select.' '.$from.' WHERE '.$where.' ORDER BY '.$order.' LIMIT '.$size.' OFFSET '.$offset);$statement->execute($bindings);
        return ['items'=>array_map([$this,'decorate'],$statement->fetchAll()),'pagination'=>['page'=>$page,'per_page'=>$size,'total'=>$total,'has_more'=>$page*$size<$total]];
    }
    public function detail(string $type,int $id,bool $includeRelations=true): array
    {
        if($id<1)throw new ApiError('not_found',404);
        [$select,$from,$where]=$this->definition($type);$q=$this->db->prepare($select.' '.$from.' WHERE '.$where.' AND c.id=? LIMIT 1');$q->execute([$id]);$item=$q->fetch();if(!$item)throw new ApiError('not_found',404);
        $item=$this->decorate($item);
        if($includeRelations&&$type==='scholars'){$item['series']=$this->list('series',['scholar_id'=>$id])['items'];$item['lessons']=$this->list('lessons',['scholar_id'=>$id])['items'];}
        if($includeRelations&&$type==='series')$item['lessons']=$this->list('lessons',['series_id'=>$id,'per_page'=>50])['items'];
        return $item;
    }
    public function home(int $userId): array
    {
        $q=$this->db->prepare('SELECT COALESCE(SUM(quran_sessions),0) quran_sessions,COALESCE(SUM(lesson_completions),0) lesson_completions,COALESCE(SUM(adhkar_completions),0) adhkar_completions,COALESCE(SUM(book_downloads),0) book_downloads,COUNT(*) active_days FROM account_daily_stats WHERE user_id=?');$q->execute([$userId]);
        return ['latest_lessons'=>$this->list('lessons',['per_page'=>3,'sort'=>'latest'])['items'],'books'=>$this->list('books',['per_page'=>3])['items'],'scholars'=>$this->list('scholars',['per_page'=>6])['items'],'activity'=>$q->fetch()];
    }
    private function definition(string $type): array
    {
        $definitions=[
            'scholars'=>['SELECT c.id,c.name,c.biography,c.source_url,c.image_key','FROM scholars c','c.is_published=1','c.sort_order,c.id'],
            'series'=>['SELECT c.id,c.title,c.description,c.scholar_id,s.name scholar_name,(SELECT COUNT(*) FROM lessons l LEFT JOIN scholars ls ON ls.id=l.scholar_id WHERE l.series_id=c.id AND l.is_published=1 AND (l.scholar_id IS NULL OR ls.is_published=1)) lesson_count','FROM series c LEFT JOIN scholars s ON s.id=c.scholar_id','c.is_published=1 AND (c.scholar_id IS NULL OR s.is_published=1)','c.sort_order,c.id DESC'],
            'lessons'=>['SELECT c.id,c.title,c.description,c.youtube_id,c.source_url,COALESCE(c.scholar_id,r.scholar_id) scholar_id,c.series_id,c.sort_order,COALESCE(s.name,rs.name) scholar_name,r.title series_title','FROM lessons c LEFT JOIN scholars s ON s.id=c.scholar_id LEFT JOIN series r ON r.id=c.series_id LEFT JOIN scholars rs ON rs.id=r.scholar_id','c.is_published=1 AND (c.scholar_id IS NULL OR s.is_published=1) AND (c.series_id IS NULL OR (r.is_published=1 AND (r.scholar_id IS NULL OR rs.is_published=1)))','c.sort_order,c.id DESC'],
            'books'=>['SELECT c.id,c.title,c.author,c.description,c.keywords,c.language,c.publication_year,c.rights_holder,c.source_url,c.rights_basis,c.can_download,c.download_url','FROM books c','c.is_published=1','c.id DESC'],
            'adhkar'=>['SELECT c.id,c.title,c.formula,c.source_reference,c.source_url,c.repeat_count,c.category','FROM adhkar_items c','c.is_published=1 AND c.is_reviewed=1','c.category,c.sort_order,c.id'],
        ];
        if(!isset($definitions[$type]))throw new ApiError('not_found',404);return $definitions[$type];
    }
    private function decorate(array $row): array
    {
        $row['id']=(int)$row['id'];
        if(array_key_exists('image_key',$row)){$row['image_url']=$row['image_key']?'/scholar-image?key='.rawurlencode($row['image_key']):null;unset($row['image_key']);}
        if(isset($row['can_download'])){$row['can_download']=(bool)$row['can_download']&&trim($row['rights_basis'])!==''&&is_string($row['download_url'])&&filter_var($row['download_url'],FILTER_VALIDATE_URL)&&strtolower((string)parse_url($row['download_url'],PHP_URL_SCHEME))==='https';$row['download_endpoint']=$row['can_download']?'/api/v1/books/'.$row['id'].'/download':null;unset($row['download_url']);}
        return $row;
    }
}
