<?php
declare(strict_types=1);

namespace Athar\Engagement;

use Athar\Http\ApiError;
use PDO;
use Throwable;

final class PushDeviceService
{
    public function __construct(
        private PDO $db,
        private string $key,
        private string $environment,
    ) {
        if(strlen($key)!==32)throw new \InvalidArgumentException('invalid_push_data_key');
        if(!preg_match('/^[a-f0-9]{12}$/',$environment))throw new \InvalidArgumentException('invalid_push_environment');
    }

    public function list(int $userId): array
    {
        $q=$this->db->prepare("SELECT installation_id,provider,environment,status,last_seen_at,revoked_at,created_at,updated_at
            FROM push_devices WHERE user_id=? AND environment=? ORDER BY last_seen_at DESC,id DESC LIMIT 50");
        $q->execute([$userId,$this->environment]);
        return ['items'=>array_map(static fn(array $row):array=>[
            'installation_id'=>$row['installation_id'],
            'provider'=>$row['provider'],
            'environment'=>$row['environment'],
            'status'=>$row['status'],
            'last_seen_at'=>$row['last_seen_at'],
            'revoked_at'=>$row['revoked_at'],
            'created_at'=>$row['created_at'],
            'updated_at'=>$row['updated_at'],
        ],$q->fetchAll())];
    }

    public function register(int $userId,string $familyId,string $installationId,array $input): array
    {
        $installationId=$this->installation($installationId);
        if(!preg_match('/^[a-f0-9]{32}$/',$familyId))throw new ApiError('unauthenticated',401);
        if(array_diff(array_keys($input),['provider','environment','token']))throw new ApiError('invalid_fields',422,['input'=>'unsupported_field']);
        $provider=(string)($input['provider']??'');
        $environment=strtolower((string)($input['environment']??''));
        $token=(string)($input['token']??'');
        if($provider!=='fcm_v1')throw new ApiError('invalid_fields',422,['provider'=>'invalid']);
        if(!hash_equals($this->environment,$environment))throw new ApiError('push_environment_mismatch',409);
        if(!preg_match('/^[A-Za-z0-9:_-]{16,4096}$/',$token))throw new ApiError('invalid_fields',422,['token'=>'invalid']);
        $hash=hash('sha256',$token);
        $cipher=$this->seal($userId,$environment,$installationId,$token);

        $owns=!$this->db->inTransaction();
        if($owns)$this->db->beginTransaction();
        try{
            $lock=$this->db->prepare("SELECT id FROM push_devices
                WHERE (environment=? AND installation_id=?)
                   OR (provider=? AND environment=? AND token_hash=?)
                FOR UPDATE");
            $lock->execute([$environment,$installationId,$provider,$environment,$hash]);
            $lock->fetchAll();

            $this->db->prepare("UPDATE push_devices
                SET status='revoked',revocation_reason='superseded',revoked_at=UTC_TIMESTAMP(),
                    token_hash=NULL,token_cipher=NULL,updated_at=UTC_TIMESTAMP()
                WHERE status='active'
                  AND ((environment=? AND installation_id=?)
                    OR (provider=? AND environment=? AND token_hash=?))")
                ->execute([$environment,$installationId,$provider,$environment,$hash]);

            $this->db->prepare("INSERT INTO push_devices
                (user_id,environment,installation_id,provider,token_hash,token_cipher,session_family_id,
                 status,revocation_reason,last_seen_at,revoked_at,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,'active',NULL,UTC_TIMESTAMP(),NULL,UTC_TIMESTAMP(),UTC_TIMESTAMP())
                ON DUPLICATE KEY UPDATE
                    user_id=VALUES(user_id),provider=VALUES(provider),token_hash=VALUES(token_hash),
                    token_cipher=VALUES(token_cipher),session_family_id=VALUES(session_family_id),
                    status='active',revocation_reason=NULL,last_seen_at=UTC_TIMESTAMP(),revoked_at=NULL,
                    updated_at=UTC_TIMESTAMP()")
                ->execute([$userId,$environment,$installationId,$provider,$hash,$cipher,$familyId]);

            $this->audit($userId,'push-device-registered',$installationId);
            if($owns)$this->db->commit();
            return $this->state($userId,$installationId);
        }catch(Throwable $failure){
            if($owns&&$this->db->inTransaction())$this->db->rollBack();
            throw $failure;
        }
    }

    public function revokeInstallation(int $userId,string $installationId,string $reason='user_revoked'): array
    {
        $installationId=$this->installation($installationId);
        $q=$this->db->prepare("UPDATE push_devices
            SET status='revoked',revocation_reason=?,revoked_at=UTC_TIMESTAMP(),
                token_hash=NULL,token_cipher=NULL,updated_at=UTC_TIMESTAMP()
            WHERE user_id=? AND environment=? AND installation_id=? AND status='active'");
        $q->execute([$reason,$userId,$this->environment,$installationId]);
        if($q->rowCount()>0)$this->audit($userId,'push-device-revoked',$installationId);
        return ['installation_id'=>$installationId,'status'=>'revoked'];
    }

    public function revokeSessionFamily(int $userId,string $familyId,string $reason='logout'): int
    {
        if(!preg_match('/^[a-f0-9]{32}$/',$familyId))return 0;
        $q=$this->db->prepare("UPDATE push_devices
            SET status='revoked',revocation_reason=?,revoked_at=UTC_TIMESTAMP(),
                token_hash=NULL,token_cipher=NULL,updated_at=UTC_TIMESTAMP()
            WHERE user_id=? AND environment=? AND session_family_id=? AND status='active'");
        $q->execute([$reason,$userId,$this->environment,$familyId]);
        return $q->rowCount();
    }

    public function revokeOtherSessionFamilies(int $userId,string $currentFamily): int
    {
        if(!preg_match('/^[a-f0-9]{32}$/',$currentFamily))return 0;
        $q=$this->db->prepare("UPDATE push_devices
            SET status='revoked',revocation_reason='session_revoked',revoked_at=UTC_TIMESTAMP(),
                token_hash=NULL,token_cipher=NULL,updated_at=UTC_TIMESTAMP()
            WHERE user_id=? AND environment=? AND session_family_id<>? AND status='active'");
        $q->execute([$userId,$this->environment,$currentFamily]);
        return $q->rowCount();
    }

    public function activeForUser(int $userId): array
    {
        $q=$this->db->prepare("SELECT d.id,d.installation_id,d.provider,d.environment,d.token_cipher
            FROM push_devices d JOIN users u ON u.id=d.user_id
            WHERE d.user_id=? AND d.environment=? AND d.status='active'
              AND d.token_cipher IS NOT NULL AND d.token_hash IS NOT NULL
              AND EXISTS (
                SELECT 1 FROM api_sessions s
                WHERE s.user_id=d.user_id AND s.family_id=d.session_family_id
                  AND s.revoked_at IS NULL AND s.refresh_expires_at>UTC_TIMESTAMP()
                  AND s.session_version=u.session_version
              )
            ORDER BY d.id");
        $q->execute([$userId,$this->environment]);
        $result=[];
        foreach($q->fetchAll() as $row){
            $result[]=[
                'id'=>(int)$row['id'],
                'installation_id'=>$row['installation_id'],
                'provider'=>$row['provider'],
                'environment'=>$row['environment'],
                'token'=>$this->open($userId,$row['environment'],$row['installation_id'],$row['token_cipher']),
            ];
        }
        return $result;
    }

    public function markInvalid(int $deviceId,string $reason='invalid_token'): void
    {
        if($deviceId<1)return;
        $this->db->prepare("UPDATE push_devices SET status='invalid',revocation_reason=?,
            revoked_at=UTC_TIMESTAMP(),token_hash=NULL,token_cipher=NULL,updated_at=UTC_TIMESTAMP()
            WHERE id=?")->execute([$reason,$deviceId]);
    }

    private function state(int $userId,string $installationId): array
    {
        $q=$this->db->prepare("SELECT installation_id,provider,environment,status,last_seen_at,created_at,updated_at
            FROM push_devices WHERE user_id=? AND environment=? AND installation_id=?");
        $q->execute([$userId,$this->environment,$installationId]);
        $row=$q->fetch();
        if(!$row)throw new \RuntimeException('push_device_state_missing');
        return [
            'installation_id'=>$row['installation_id'],
            'provider'=>$row['provider'],
            'environment'=>$row['environment'],
            'status'=>$row['status'],
            'last_seen_at'=>$row['last_seen_at'],
            'created_at'=>$row['created_at'],
            'updated_at'=>$row['updated_at'],
        ];
    }

    private function installation(string $value): string
    {
        $value=strtolower(trim($value));
        if(!preg_match('/^[a-f0-9]{32}$/',$value))throw new ApiError('invalid_fields',422,['installation_id'=>'invalid']);
        return $value;
    }

    private function aad(int $userId,string $environment,string $installationId): string
    {
        return 'push-device:v1:'.$userId.':'.$environment.':'.$installationId;
    }

    private function seal(int $userId,string $environment,string $installationId,string $token): string
    {
        $aad=$this->aad($userId,$environment,$installationId);
        $key=hash_hmac('sha256',$aad,$this->key,true);
        $iv=random_bytes(12);$tag='';
        $cipher=openssl_encrypt($token,'aes-256-gcm',$key,OPENSSL_RAW_DATA,$iv,$tag,$aad);
        if($cipher===false)throw new \RuntimeException('push_token_encrypt_failed');
        return base64_encode($iv.$tag.$cipher);
    }

    private function open(int $userId,string $environment,string $installationId,string $sealed): string
    {
        $raw=base64_decode($sealed,true);
        if(!is_string($raw)||strlen($raw)<29)throw new \RuntimeException('push_token_unavailable');
        $aad=$this->aad($userId,$environment,$installationId);
        $key=hash_hmac('sha256',$aad,$this->key,true);
        $plain=openssl_decrypt(substr($raw,28),'aes-256-gcm',$key,OPENSSL_RAW_DATA,substr($raw,0,12),substr($raw,12,16),$aad);
        if(!is_string($plain)||!preg_match('/^[A-Za-z0-9:_-]{16,4096}$/',$plain))throw new \RuntimeException('push_token_unavailable');
        return $plain;
    }

    private function audit(int $userId,string $action,string $installationId): void
    {
        $target=substr(hash('sha256',$installationId),0,24);
        $this->db->prepare("INSERT INTO audit_logs(actor_id,action,target_type,target_id)
            VALUES (?,?,'push_device',?)")->execute([$userId,$action,$target]);
    }
}
