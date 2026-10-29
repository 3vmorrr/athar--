<?php
declare(strict_types=1);
namespace Athar\Identity;

use Athar\Http\ApiError;
use PDO;
use PDOException;

/** A random 256-bit capability proves only this deletion; it grants no account access. */
final class DeletionReceipts
{
    private PDO $db;
    public function __construct(PDO $db){$this->db=$db;}
    public static function validate(string $id,string $secret):void
    {
        if(!preg_match('/^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/D',$id)||!preg_match('/^[a-f0-9]{64}$/D',$secret))throw new ApiError('invalid_fields',422);
    }
    /** The caller's transaction also deletes the user; an unsuccessful deletion leaves no receipt. */
    public function record(int $owner,string $id,string $secret):void
    {
        self::validate($id,$secret);
        if(!$this->db->inTransaction())throw new \LogicException('Deletion receipt requires the deletion transaction.');
        try{$this->db->prepare('INSERT INTO account_deletion_receipts(request_id,owner_id,proof_hash,created_at) VALUES(?,?,?,UTC_TIMESTAMP())')->execute([$id,$owner,hash('sha256',$secret)]);}
        catch(PDOException $error){if($error->getCode()==='23000')throw new ApiError('conflict',409);throw $error;}
    }
    public function prove(string $id,string $secret):array
    {
        self::validate($id,$secret);
        $q=$this->db->prepare('SELECT owner_id,proof_hash FROM account_deletion_receipts WHERE request_id=?');$q->execute([$id]);$row=$q->fetch(PDO::FETCH_ASSOC);
        $matches=hash_equals($row['proof_hash']??str_repeat('0',64),hash('sha256',$secret));
        if(!$row||!$matches)throw new ApiError('not_found',404);
        return ['status'=>'deleted','request_id'=>$id,'owner_id'=>(int)$row['owner_id']];
    }
    public function forget(string $id,string $secret):void
    {
        self::validate($id,$secret);
        // Idempotent acknowledgement; possession cannot erase a different capability.
        $this->db->prepare('DELETE FROM account_deletion_receipts WHERE request_id=? AND proof_hash=?')->execute([$id,hash('sha256',$secret)]);
    }
}
