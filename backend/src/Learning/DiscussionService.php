<?php
declare(strict_types=1);
namespace Athar\Learning;
use Athar\Catalog\CatalogService;
use Athar\Http\ApiError;
use PDO;
use PDOException;
use Throwable;

final class DiscussionService
{
    private PDO $db; private CatalogService $catalog;
    public function __construct(PDO $db){$this->db=$db;$this->catalog=new CatalogService($db);}
    private function visible(string $alias):string{return "($alias.status='visible' OR ($alias.user_id=? AND $alias.status='pending'))";}
    public function page(int $user,int $lesson,array $params=[]):array
    {
        $this->catalog->detail('lessons',$lesson);
        $parent=isset($params['parent_id'])?filter_var($params['parent_id'],FILTER_VALIDATE_INT):null;
        if($parent!==null&&(!$parent||$parent<1))throw new ApiError('invalid_fields',422,['parent_id'=>'invalid']);
        if($parent!==null){$q=$this->db->prepare('SELECT id FROM lesson_comments c WHERE c.id=? AND c.lesson_id=? AND c.parent_id IS NULL AND '.$this->visible('c'));$q->execute([$parent,$lesson,$user]);if(!$q->fetchColumn())throw new ApiError('not_found',404);}
        $page=max(1,min(10000,(int)($params['page']??1)));$size=max(1,min(50,(int)($params['per_page']??20)));
        $sort=(string)($params['sort']??'newest');if(!in_array($sort,['newest','oldest'],true))throw new ApiError('invalid_fields',422,['sort'=>'invalid']);
        $where='c.lesson_id=? AND '.($parent===null?'c.parent_id IS NULL':'c.parent_id=?').' AND '.$this->visible('c');
        $args=[$lesson];if($parent!==null)$args[]=$parent;$args[]=$user;
        $q=$this->db->prepare('SELECT COUNT(*) FROM lesson_comments c WHERE '.$where);$q->execute($args);$total=(int)$q->fetchColumn();
        $sql="SELECT c.id,c.parent_id,c.body,c.status,c.created_at,c.revision,c.user_id,u.display_name,u.username,(SELECT COUNT(*) FROM lesson_comments r WHERE r.parent_id=c.id AND ".$this->visible('r').") reply_count FROM lesson_comments c JOIN users u ON u.id=c.user_id WHERE $where ORDER BY c.id ".($sort==='oldest'?'ASC':'DESC').' LIMIT '.$size.' OFFSET '.(($page-1)*$size);
        $q=$this->db->prepare($sql);$q->execute(array_merge([$user],$args));$items=$q->fetchAll();
        foreach($items as &$item){foreach(['id','revision','reply_count'] as $field)$item[$field]=(int)$item[$field];$item['parent_id']=$item['parent_id']===null?null:(int)$item['parent_id'];$item['is_own']=(int)$item['user_id']===$user;unset($item['user_id']);}unset($item);
        return ['items'=>$items,'pagination'=>['page'=>$page,'per_page'=>$size,'total'=>$total,'has_more'=>$page*$size<$total],'sort'=>$sort,'parent_id'=>$parent];
    }
    public function add(int $user,int $lesson,array $input,?callable $allow=null):array
    {
        $this->catalog->detail('lessons',$lesson);$body=$input['body']??'';$client=$input['client_id']??'';$parent=$input['parent_id']??null;
        if(!is_string($body)||mb_strlen(trim($body),'UTF-8')<2||mb_strlen(trim($body),'UTF-8')>2000)throw new ApiError('invalid_fields',422,['body'=>'invalid']);
        if(!is_string($client)||!preg_match('/^[a-f0-9]{8}-[a-f0-9]{4}-[1-5][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/i',$client))throw new ApiError('invalid_fields',422,['client_id'=>'invalid']);
        if($parent!==null&&(!is_int($parent)||$parent<1))throw new ApiError('invalid_fields',422,['parent_id'=>'invalid']);
        $body=trim($body);$client=strtolower($client);$hash=hash('sha256',json_encode([$lesson,$parent,$body],JSON_UNESCAPED_UNICODE|JSON_THROW_ON_ERROR));
        $existing=$this->existing($user,$client,$hash);if($existing)return $existing;
        if($allow&&!$allow())throw new ApiError('rate_limited',429);
        $this->db->beginTransaction();
        try{
            if($parent!==null){$q=$this->db->prepare('SELECT id FROM lesson_comments c WHERE c.id=? AND c.lesson_id=? AND c.parent_id IS NULL AND '.$this->visible('c').' FOR UPDATE');$q->execute([$parent,$lesson,$user]);if(!$q->fetchColumn())throw new ApiError('not_found',404);}
            $this->db->prepare("INSERT INTO lesson_comments(lesson_id,user_id,parent_id,client_id,request_hash,body,status) VALUES (?,?,?,?,?,?,'pending')")->execute([$lesson,$user,$parent,$client,$hash,$body]);
            $id=(int)$this->db->lastInsertId();$this->db->commit();return ['id'=>$id,'status'=>'pending','parent_id'=>$parent,'replayed'=>false];
        }catch(Throwable $e){if($this->db->inTransaction())$this->db->rollBack();if($e instanceof PDOException&&$e->getCode()==='23000'){$existing=$this->existing($user,$client,$hash);if($existing)return $existing;}throw $e;}
    }
    private function existing(int $user,string $client,string $hash):?array
    {
        $q=$this->db->prepare('SELECT id,status,parent_id,request_hash FROM lesson_comments WHERE user_id=? AND client_id=?');$q->execute([$user,$client]);$row=$q->fetch();if(!$row)return null;
        if(!hash_equals((string)$row['request_hash'],$hash))throw new ApiError('conflict',409);
        return ['id'=>(int)$row['id'],'status'=>$row['status'],'parent_id'=>$row['parent_id']===null?null:(int)$row['parent_id'],'replayed'=>true];
    }
    public function moderation(array $actor,array $params=[]):array
    {
        $this->admin($actor);$status=(string)($params['status']??'pending');if(!in_array($status,['pending','visible','hidden'],true))throw new ApiError('invalid_fields',422);
        $page=max(1,min(10000,(int)($params['page']??1)));$size=30;$q=$this->db->prepare('SELECT COUNT(*) FROM lesson_comments WHERE status=?');$q->execute([$status]);$total=(int)$q->fetchColumn();
        $q=$this->db->prepare('SELECT c.id,c.lesson_id,c.parent_id,c.body,c.status,c.revision,c.created_at,u.display_name,u.username,l.title lesson_title FROM lesson_comments c JOIN users u ON u.id=c.user_id JOIN lessons l ON l.id=c.lesson_id WHERE c.status=? ORDER BY c.id ASC LIMIT 30 OFFSET '.(($page-1)*$size));$q->execute([$status]);
        return ['items'=>$q->fetchAll(),'pagination'=>['page'=>$page,'per_page'=>$size,'total'=>$total,'has_more'=>$page*$size<$total]];
    }
    public function moderate(array $actor,int $id,array $input):array
    {
        $this->admin($actor);
        $status=$input['status']??'';
        $revision=$input['expected_revision']??null;
        $reason=is_string($input['reason']??null)?trim((string)$input['reason']):'';
        if(!in_array($status,['visible','hidden'],true)||!is_int($revision)||$revision<1||mb_strlen($reason,'UTF-8')<2||mb_strlen($reason,'UTF-8')>500)throw new ApiError('invalid_fields',422);
        $this->db->beginTransaction();
        try{
            $q=$this->db->prepare('SELECT status,revision FROM lesson_comments WHERE id=? FOR UPDATE');
            $q->execute([$id]);
            $current=$q->fetch();
            if(!$current)throw new ApiError('not_found',404);
            if((int)$current['revision']!==$revision)throw new ApiError('conflict',409);
            $nextRevision=$revision+1;
            $this->db->prepare('UPDATE lesson_comments SET status=?,revision=?,moderated_by=?,moderation_reason=?,moderated_at=UTC_TIMESTAMP() WHERE id=?')
                ->execute([$status,$nextRevision,$actor['id'],$reason,$id]);
            $action=$status==='visible'?'published':'hidden';
            $this->db->prepare('INSERT INTO lesson_comment_moderation_events(comment_id,source_comment_id,revision,action,previous_status,reason,actor_id,created_at) VALUES (?,?,?,?,?,?,?,UTC_TIMESTAMP())')
                ->execute([$id,$id,$nextRevision,$action,$current['status'],$reason,$actor['id']]);
            $this->db->prepare('INSERT INTO audit_logs(actor_id,action,target_type,target_id) VALUES (?,?,?,?)')
                ->execute([$actor['id'],'comment-'.$action,'comment',(string)$id]);
            $this->db->commit();
            return ['id'=>$id,'status'=>$status,'revision'=>$nextRevision];
        }catch(Throwable $e){if($this->db->inTransaction())$this->db->rollBack();throw $e;}
    }

    public function deleteModerated(array $actor,int $id,array $input):array
    {
        $this->admin($actor);
        $revision=$input['expected_revision']??null;
        $reason=is_string($input['reason']??null)?trim((string)$input['reason']):'';
        if(!is_int($revision)||$revision<1||mb_strlen($reason,'UTF-8')<2||mb_strlen($reason,'UTF-8')>500)throw new ApiError('invalid_fields',422);
        $this->db->beginTransaction();
        try{
            $q=$this->db->prepare('SELECT status,revision FROM lesson_comments WHERE id=? FOR UPDATE');
            $q->execute([$id]);
            $current=$q->fetch();
            if(!$current)throw new ApiError('not_found',404);
            if((int)$current['revision']!==$revision)throw new ApiError('conflict',409);
            $children=$this->db->prepare('SELECT id FROM lesson_comments WHERE parent_id=? LIMIT 1 FOR UPDATE');$children->execute([$id]);
            if($children->fetchColumn())throw new ApiError('conflict',409,['comment'=>'has_replies']);
            $nextRevision=$revision+1;
            $this->db->prepare('INSERT INTO lesson_comment_moderation_events(comment_id,source_comment_id,revision,action,previous_status,reason,actor_id,created_at) VALUES (?,?,?,?,?,?,?,UTC_TIMESTAMP())')
                ->execute([$id,$id,$nextRevision,'deleted',$current['status'],$reason,$actor['id']]);
            $this->db->prepare('INSERT INTO audit_logs(actor_id,action,target_type,target_id) VALUES (?,?,?,?)')
                ->execute([$actor['id'],'comment-deleted','comment',(string)$id]);
            $this->db->prepare('DELETE FROM lesson_comments WHERE id=?')->execute([$id]);
            $this->db->commit();
            return ['id'=>$id,'deleted'=>true,'revision'=>$nextRevision];
        }catch(Throwable $e){if($this->db->inTransaction())$this->db->rollBack();throw $e;}
    }
    private function admin(array $actor):void{if(!in_array($actor['role']??'', ['admin','owner'],true)||empty($actor['email_verified_at'])||!empty($actor['suspended_at']))throw new ApiError('forbidden',403);}
}
