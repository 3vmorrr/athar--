<?php
declare(strict_types=1);

namespace Athar\Engagement;

use Athar\Http\ApiError;
use DateInterval;
use DateTimeImmutable;
use DateTimeZone;
use PDO;

final class NotificationInboxService
{
    public function __construct(private PDO $db,private string $cursorKey)
    {
        if(strlen($cursorKey)<32)throw new \InvalidArgumentException('invalid_inbox_cursor_key');
    }

    public function page(int $userId,array $query=[]): array
    {
        $size=max(1,min(50,(int)($query['per_page']??20)));
        $before=$this->decodeCursor($userId,$query['cursor']??null);
        $sql="SELECT id,category,source_type,source_id,title,body,destination,read_at,expires_at,created_at
            FROM notification_inbox
            WHERE user_id=? AND deleted_at IS NULL AND expires_at>UTC_TIMESTAMP()";
        $args=[$userId];
        if($before!==null){$sql.=" AND id<?";$args[]=$before;}
        $sql.=" ORDER BY id DESC LIMIT ".($size+1);
        $q=$this->db->prepare($sql);$q->execute($args);$rows=$q->fetchAll();
        $hasMore=count($rows)>$size;
        if($hasMore)array_pop($rows);
        $items=array_map([$this,'present'],$rows);
        $next=$hasMore&&$rows!==[]?$this->encodeCursor($userId,(int)end($rows)['id']):null;

        $unread=$this->db->prepare("SELECT COUNT(*) FROM notification_inbox
            WHERE user_id=? AND read_at IS NULL AND deleted_at IS NULL AND expires_at>UTC_TIMESTAMP()");
        $unread->execute([$userId]);
        return [
            'items'=>$items,
            'pagination'=>[
                'per_page'=>$size,
                'next_cursor'=>$next,
                'has_more'=>$hasMore,
            ],
            'unread_count'=>(int)$unread->fetchColumn(),
        ];
    }

    public function detail(int $userId,int $id): array
    {
        return $this->present($this->visibleRow($userId,$id));
    }

    public function markRead(int $userId,int $id): array
    {
        $this->visibleRow($userId,$id);
        $this->db->prepare("UPDATE notification_inbox
            SET read_at=COALESCE(read_at,UTC_TIMESTAMP()),updated_at=UTC_TIMESTAMP()
            WHERE user_id=? AND id=? AND deleted_at IS NULL AND expires_at>UTC_TIMESTAMP()")
            ->execute([$userId,$id]);
        return $this->detail($userId,$id);
    }

    public function delete(int $userId,int $id): array
    {
        if($id<1)throw new ApiError('not_found',404);
        $q=$this->db->prepare("SELECT id,deleted_at FROM notification_inbox WHERE user_id=? AND id=?");
        $q->execute([$userId,$id]);$row=$q->fetch();
        if(!$row)throw new ApiError('not_found',404);
        if($row['deleted_at']===null){
            $this->db->prepare("UPDATE notification_inbox SET deleted_at=UTC_TIMESTAMP(),updated_at=UTC_TIMESTAMP()
                WHERE user_id=? AND id=?")->execute([$userId,$id]);
        }
        return ['id'=>$id,'deleted'=>true];
    }

    public function record(
        int $userId,
        string $category,
        string $sourceType,
        ?string $sourceId,
        string $title,
        string $body,
        string $destination,
        string $dedupeKey,
        DateTimeImmutable $expiresAt,
    ): int {
        $this->validateCategory($category);
        if(!preg_match('/^[a-z0-9_]{1,40}$/',$sourceType))throw new \InvalidArgumentException('invalid_inbox_source');
        if($sourceId!==null&&(strlen($sourceId)>120||preg_match('/[\x00-\x1F]/',$sourceId)))throw new \InvalidArgumentException('invalid_inbox_source_id');
        if(trim($title)===''||strlen($title)>640)throw new \InvalidArgumentException('invalid_inbox_title');
        if(strlen($body)>2000)throw new \InvalidArgumentException('invalid_inbox_body');
        $this->validateDestination($destination);
        if(!preg_match('/^[a-f0-9]{64}$/',$dedupeKey))throw new \InvalidArgumentException('invalid_inbox_dedupe');
        $now=new DateTimeImmutable('now',new DateTimeZone('UTC'));
        if($expiresAt<=$now||$expiresAt>$now->add(new DateInterval('P90D')))throw new \InvalidArgumentException('invalid_inbox_expiry');

        $q=$this->db->prepare("INSERT INTO notification_inbox
            (user_id,category,source_type,source_id,title,body,destination,dedupe_key,expires_at,created_at,updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(),UTC_TIMESTAMP())
            ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id)");
        $q->execute([
            $userId,$category,$sourceType,$sourceId,$title,$body,$destination,$dedupeKey,
            $expiresAt->setTimezone(new DateTimeZone('UTC'))->format('Y-m-d H:i:s')
        ]);
        return (int)$this->db->lastInsertId();
    }

    private function visibleRow(int $userId,int $id): array
    {
        if($id<1)throw new ApiError('not_found',404);
        $q=$this->db->prepare("SELECT id,category,source_type,source_id,title,body,destination,read_at,expires_at,created_at
            FROM notification_inbox
            WHERE user_id=? AND id=? AND deleted_at IS NULL AND expires_at>UTC_TIMESTAMP()");
        $q->execute([$userId,$id]);$row=$q->fetch();
        if(!$row)throw new ApiError('not_found',404);
        return $row;
    }

    private function present(array $row): array
    {
        return [
            'id'=>(int)$row['id'],
            'category'=>$row['category'],
            'source_type'=>$row['source_type'],
            'source_id'=>$row['source_id'],
            'title'=>$row['title'],
            'body'=>$row['body'],
            'destination'=>$row['destination'],
            'read'=>$row['read_at']!==null,
            'read_at'=>$this->iso($row['read_at']),
            'expires_at'=>$this->iso($row['expires_at']),
            'created_at'=>$this->iso($row['created_at']),
        ];
    }

    private function validateCategory(string $category): void
    {
        if(!in_array($category,[
            'lesson_reminders','following_updates','quran_routine','adhkar_routine',
            'salawat_friday','custom_routines','khatmah','night_prayer','campaigns'
        ],true))throw new \InvalidArgumentException('invalid_inbox_category');
    }

    private function validateDestination(string $value): void
    {
        if(in_array($value,['home','quran','adhkar','night','inbox'],true))return;
        if(preg_match('/^(lesson|series):[1-9][0-9]*$/',$value))return;
        throw new \InvalidArgumentException('invalid_inbox_destination');
    }

    private function encodeCursor(int $userId,int $id): string
    {
        $payload=$userId.':'.$id;
        $mac=hash_hmac('sha256','notification-inbox:'.$payload,$this->cursorKey);
        return rtrim(strtr(base64_encode($payload.':'.$mac),'+/','-_'),'=');
    }

    private function decodeCursor(int $userId,$cursor): ?int
    {
        if($cursor===null||$cursor==='')return null;
        if(!is_string($cursor)||strlen($cursor)>180)throw new ApiError('invalid_cursor',422);
        $raw=base64_decode(strtr($cursor,'-_','+/'),true);
        if(!is_string($raw))throw new ApiError('invalid_cursor',422);
        $parts=explode(':',$raw);
        if(count($parts)!==3||!ctype_digit($parts[0])||!ctype_digit($parts[1])||!preg_match('/^[a-f0-9]{64}$/',$parts[2])){
            throw new ApiError('invalid_cursor',422);
        }
        $owner=(int)$parts[0];$id=(int)$parts[1];
        if($owner!==$userId||$id<1)throw new ApiError('invalid_cursor',422);
        $expected=hash_hmac('sha256','notification-inbox:'.$owner.':'.$id,$this->cursorKey);
        if(!hash_equals($expected,$parts[2]))throw new ApiError('invalid_cursor',422);
        return $id;
    }

    private function iso($value): ?string
    {
        if($value===null)return null;
        return (new DateTimeImmutable((string)$value,new DateTimeZone('UTC')))->format('Y-m-d\TH:i:s\Z');
    }
}
