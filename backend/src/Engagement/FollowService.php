<?php
declare(strict_types=1);

namespace Athar\Engagement;

use Athar\Catalog\CatalogService;
use Athar\Http\ApiError;
use PDO;

final class FollowService
{
    private CatalogService $catalog;

    public function __construct(private PDO $db)
    {
        $this->catalog=new CatalogService($db);
    }

    public function list(int $userId,array $params=[]): array
    {
        $type=$params['target_type']??null;
        if($type!==null&&!in_array($type,['scholar','series'],true)){
            throw new ApiError('invalid_fields',422,['target_type'=>'invalid']);
        }
        $page=max(1,min(10000,(int)($params['page']??1)));
        $size=max(1,min(50,(int)($params['per_page']??20)));
        $rows=[];
        if($type===null||$type==='scholar'){
            $q=$this->db->prepare("SELECT 'scholar' target_type,f.scholar_id target_id,s.name title,
                NULL scholar_id,NULL scholar_name,f.created_at
                FROM scholar_follows f JOIN scholars s ON s.id=f.scholar_id
                WHERE f.user_id=? AND s.is_published=1");
            $q->execute([$userId]);$rows=array_merge($rows,$q->fetchAll());
        }
        if($type===null||$type==='series'){
            $q=$this->db->prepare("SELECT 'series' target_type,f.series_id target_id,r.title,
                r.scholar_id,s.name scholar_name,f.created_at
                FROM series_follows f JOIN series r ON r.id=f.series_id
                LEFT JOIN scholars s ON s.id=r.scholar_id
                WHERE f.user_id=? AND r.is_published=1
                AND (r.scholar_id IS NULL OR s.is_published=1)");
            $q->execute([$userId]);$rows=array_merge($rows,$q->fetchAll());
        }
        usort($rows,static function(array $a,array $b):int{
            $byTime=strcmp((string)$b['created_at'],(string)$a['created_at']);
            if($byTime!==0)return $byTime;
            $byType=strcmp((string)$a['target_type'],(string)$b['target_type']);
            return $byType!==0?$byType:((int)$b['target_id']<=> (int)$a['target_id']);
        });
        $total=count($rows);
        $items=array_slice($rows,($page-1)*$size,$size);
        return [
            'items'=>array_map([$this,'presentListItem'],$items),
            'pagination'=>[
                'page'=>$page,'per_page'=>$size,'total'=>$total,'has_more'=>$page*$size<$total,
            ],
        ];
    }

    public function state(int $userId,string $type,int $targetId): array
    {
        $this->visible($type,$targetId);
        return $this->stateWithoutVisibility($userId,$type,$targetId);
    }

    public function follow(int $userId,string $type,int $targetId): array
    {
        $this->visible($type,$targetId);
        [$table,$column]=$this->definition($type);
        $q=$this->db->prepare("INSERT IGNORE INTO {$table}(user_id,{$column},created_at) VALUES (?,?,UTC_TIMESTAMP())");
        $q->execute([$userId,$targetId]);
        if($q->rowCount()===1){
            $this->db->prepare("INSERT INTO audit_logs(actor_id,action,target_type,target_id)
                VALUES (?,'follow-created',?,?)")->execute([$userId,$type,(string)$targetId]);
        }
        return $this->stateWithoutVisibility($userId,$type,$targetId);
    }

    public function unfollow(int $userId,string $type,int $targetId): array
    {
        [$table,$column]=$this->definition($type);
        if($targetId<1)throw new ApiError('invalid_fields',422,['target_id'=>'invalid']);
        $q=$this->db->prepare("DELETE FROM {$table} WHERE user_id=? AND {$column}=?");
        $q->execute([$userId,$targetId]);
        if($q->rowCount()===1){
            $this->db->prepare("INSERT INTO audit_logs(actor_id,action,target_type,target_id)
                VALUES (?,'follow-removed',?,?)")->execute([$userId,$type,(string)$targetId]);
        }
        return [
            'target_type'=>$type,'target_id'=>$targetId,'following'=>false,'followed_at'=>null,
        ];
    }

    private function stateWithoutVisibility(int $userId,string $type,int $targetId): array
    {
        [$table,$column]=$this->definition($type);
        if($targetId<1)throw new ApiError('invalid_fields',422,['target_id'=>'invalid']);
        $q=$this->db->prepare("SELECT created_at FROM {$table} WHERE user_id=? AND {$column}=?");
        $q->execute([$userId,$targetId]);$created=$q->fetchColumn();
        return [
            'target_type'=>$type,
            'target_id'=>$targetId,
            'following'=>$created!==false,
            'followed_at'=>$created===false?null:(string)$created,
        ];
    }

    private function visible(string $type,int $targetId): void
    {
        if($targetId<1)throw new ApiError('not_found',404);
        if($type==='scholar'){$this->catalog->detail('scholars',$targetId,false);return;}
        if($type==='series'){$this->catalog->detail('series',$targetId,false);return;}
        throw new ApiError('invalid_fields',422,['target_type'=>'invalid']);
    }

    private function definition(string $type): array
    {
        return match($type){
            'scholar'=>['scholar_follows','scholar_id'],
            'series'=>['series_follows','series_id'],
            default=>throw new ApiError('invalid_fields',422,['target_type'=>'invalid']),
        };
    }

    private function presentListItem(array $row): array
    {
        return [
            'target_type'=>(string)$row['target_type'],
            'target_id'=>(int)$row['target_id'],
            'title'=>(string)$row['title'],
            'scholar_id'=>$row['scholar_id']===null?null:(int)$row['scholar_id'],
            'scholar_name'=>$row['scholar_name']===null?null:(string)$row['scholar_name'],
            'followed_at'=>(string)$row['created_at'],
        ];
    }
}
