<?php
declare(strict_types=1);
namespace Athar\Identity;
use Athar\Http\ApiError;
use PDO;
use PDOException;
use Throwable;

final class AuthService
{
    private PDO $db; private OtpService $otp;
    public function __construct(PDO $db, OtpService $otp) { $this->db = $db; $this->otp = $otp; }

    public function register(array $input): array
    {
        $id = $this->createAccount($input);
        $delivered = $this->otp->issue($id, 'verify_email');
        return ['user' => $this->profile($id), 'session' => $this->newSession($id, 'verification'), 'delivery_status' => $delivered ? 'accepted' : 'unavailable'];
    }

    /** Cookie adapters may combine account creation with explicitly approved guest imports. */
    public function createAccount(array $input, bool $marketingConsent = false): int
    {
        $email = strtolower(trim((string) ($input['email'] ?? ''))); $name = trim((string) ($input['display_name'] ?? '')); $username = strtolower(trim((string) ($input['username'] ?? '')));
        $this->validateProfile($name, $username);
        if (!filter_var($email, FILTER_VALIDATE_EMAIL) || strlen($email) > 190) throw new ApiError('invalid_fields', 422, ['email' => 'invalid']);
        $password = (string) ($input['password'] ?? ''); $this->validatePassword($password, (string) ($input['password_confirmation'] ?? ''));
        $owns = !$this->db->inTransaction();
        if ($owns) $this->db->beginTransaction();
        try {
            $this->db->prepare("INSERT INTO users (email,display_name,username,password_hash,role,email_marketing_opt_in) VALUES (?,?,?,?,'user',?)")->execute([$email,$name,$username,$this->passwordHash($password),$marketingConsent?1:0]);
            $id = (int)$this->db->lastInsertId(); $this->audit($id,'account-registered');
            if ($owns) $this->db->commit();
            return $id;
        } catch (Throwable $e) {
            if ($owns && $this->db->inTransaction()) $this->db->rollBack();
            if ($e instanceof PDOException && $e->getCode()==='23000') throw new ApiError('account_conflict',409);
            throw $e;
        }
    }

    public function login(string $email, string $password, bool $issueSession = true): array
    {
        $q = $this->db->prepare('SELECT * FROM users WHERE email=? LIMIT 1'); $q->execute([strtolower(trim($email))]); $user = $q->fetch(PDO::FETCH_ASSOC);
        static $dummy;
        $dummy ??= password_hash(bin2hex(random_bytes(16)), PASSWORD_DEFAULT);
        $matches = password_verify($password, $user['password_hash'] ?? $dummy);
        if (!$user || !$matches || !empty($user['suspended_at'])) throw new ApiError('invalid_credentials', 401);
        $owns=!$this->db->inTransaction();
        if($owns)$this->db->beginTransaction();
        try {
            // Credential verification can be expensive: recheck its snapshot under the user lock
            // before rehashing or issuing a session with the current revocation version.
            $q=$this->db->prepare('SELECT * FROM users WHERE id=? FOR UPDATE');$q->execute([$user['id']]);$fresh=$q->fetch(PDO::FETCH_ASSOC);
            if(!$fresh||!empty($fresh['suspended_at'])||($fresh['password_hash']!==$user['password_hash']&&!password_verify($password,$fresh['password_hash'])))throw new ApiError('invalid_credentials',401);
            if(password_needs_rehash($fresh['password_hash'],$this->algorithm()))$this->db->prepare('UPDATE users SET password_hash=? WHERE id=?')->execute([$this->passwordHash($password),$fresh['id']]);
            $scope=empty($fresh['email_verified_at'])?'verification':'full';
            $this->audit((int)$fresh['id'],'api-login-succeeded');
            $session=$issueSession?$this->newSession((int)$fresh['id'],$scope):null;
            if($owns)$this->db->commit();
            return ['user'=>$this->publicProfile($fresh),'session'=>$session];
        }catch(Throwable $error){if($owns&&$this->db->inTransaction())$this->db->rollBack();throw $error;}
    }

    public function authenticate(string $token, bool $full = true): array
    {
        if ($token === '') throw new ApiError('unauthenticated', 401);
        $q = $this->db->prepare('SELECT s.id AS session_id,s.family_id,s.scope,s.session_version AS token_version,s.expires_at,s.revoked_at,u.* FROM api_sessions s JOIN users u ON u.id=s.user_id WHERE s.access_hash=? LIMIT 1');
        $q->execute([hash('sha256',$token)]); $actor = $q->fetch(PDO::FETCH_ASSOC);
        if (!$actor || $actor['revoked_at'] !== null || strtotime($actor['expires_at'] . ' UTC') <= time() || (int) $actor['token_version'] !== (int) $actor['session_version'] || !empty($actor['suspended_at'])) throw new ApiError('unauthenticated',401);
        if ($full && ($actor['scope'] !== 'full' || empty($actor['email_verified_at']))) throw new ApiError('email_verification_required',403);
        return $actor;
    }

    public function verify(int $userId, string $code, bool $issueSession = true): array
    {
        $this->db->beginTransaction();
        try {
            $guard=$this->db->prepare('SELECT id FROM users WHERE id=? FOR UPDATE');$guard->execute([$userId]);
            if($guard->fetchColumn()===false)throw new ApiError('unauthenticated',401);
            $valid = $this->otp->consume($userId, 'verify_email', $code);
            if (!$valid) { $this->db->commit(); throw new ApiError('invalid_or_expired_code',422); }
            $this->db->prepare('UPDATE users SET email_verified_at=UTC_TIMESTAMP() WHERE id=?')->execute([$userId]);
            $this->db->prepare("UPDATE api_sessions SET revoked_at=UTC_TIMESTAMP(),revoked_reason='verified' WHERE user_id=? AND scope='verification' AND revoked_at IS NULL")->execute([$userId]);
            $session = $issueSession ? $this->newSession($userId,'full') : null; $this->audit($userId,'api-email-verified'); $this->db->commit();
            return ['user'=>$this->profile($userId),'session'=>$session];
        } catch (Throwable $e) { if ($this->db->inTransaction()) $this->db->rollBack(); throw $e; }
    }

    public function resend(int $userId): bool { return $this->otp->issue($userId,'verify_email'); }

    public function refresh(string $refresh): array
    {
        if (!preg_match('/^[a-f0-9]{64}$/i',$refresh)) throw new ApiError('unauthenticated',401);
        $this->db->beginTransaction();
        try {
            // All session-family mutations lock the user before locking session rows.
            $lookup=$this->db->prepare('SELECT user_id FROM api_sessions WHERE refresh_hash=? LIMIT 1');$lookup->execute([hash('sha256',$refresh)]);$owner=$lookup->fetchColumn();
            if($owner===false)throw new ApiError('unauthenticated',401);
            $guard=$this->db->prepare('SELECT id FROM users WHERE id=? FOR UPDATE');$guard->execute([(int)$owner]);if($guard->fetchColumn()===false)throw new ApiError('unauthenticated',401);
            $q=$this->db->prepare('SELECT s.*,u.email_verified_at,u.suspended_at,u.session_version AS current_version FROM api_sessions s JOIN users u ON u.id=s.user_id WHERE s.refresh_hash=? LIMIT 1 FOR UPDATE'); $q->execute([hash('sha256',$refresh)]); $row=$q->fetch(PDO::FETCH_ASSOC);
            if ($row && $row['revoked_reason']==='rotated') {
                $this->db->prepare("UPDATE api_sessions SET revoked_at=UTC_TIMESTAMP(),revoked_reason='replay' WHERE family_id=? AND revoked_at IS NULL")->execute([$row['family_id']]);
                $this->audit((int)$row['user_id'],'api-refresh-replay-revoked'); $this->db->commit(); throw new ApiError('refresh_reused',401);
            }
            if (!$row || $row['revoked_at']!==null || strtotime($row['refresh_expires_at'].' UTC')<=time() || (int)$row['session_version']!==(int)$row['current_version'] || $row['suspended_at']!==null) throw new ApiError('unauthenticated',401);
            $next = $this->newSession((int)$row['user_id'], $row['scope'], $row['family_id'], $row['refresh_expires_at']);
            $this->db->prepare("UPDATE api_sessions SET revoked_at=UTC_TIMESTAMP(),revoked_reason='rotated',replaced_by=? WHERE id=?")->execute([$next['id'],$row['id']]);
            $this->db->commit(); return ['session'=>$next,'user'=>$this->profile((int)$row['user_id'])];
        } catch (Throwable $e) { if ($this->db->inTransaction()) $this->db->rollBack(); throw $e; }
    }

    public function logout(array $actor): void
    {
        $this->db->prepare("UPDATE api_sessions SET revoked_at=UTC_TIMESTAMP(),revoked_reason='logout' WHERE family_id=? AND user_id=? AND revoked_at IS NULL")->execute([$actor['family_id'],$actor['id']]);
        $this->audit((int)$actor['id'],'api-logout');
    }

    public function revokeOtherSessions(array $actor):int
    {
        $this->db->beginTransaction();
        try {
            $guard=$this->db->prepare('SELECT session_version,email_verified_at,suspended_at FROM users WHERE id=? FOR UPDATE');$guard->execute([(int)$actor['id']]);$user=$guard->fetch(PDO::FETCH_ASSOC);
            $q=$this->db->prepare('SELECT family_id,session_version,scope,expires_at,revoked_at FROM api_sessions WHERE id=? AND user_id=? FOR UPDATE');$q->execute([(string)$actor['session_id'],(int)$actor['id']]);$session=$q->fetch(PDO::FETCH_ASSOC);
            if(!$user||!$session||$user['suspended_at']!==null||empty($user['email_verified_at'])||$session['scope']!=='full'||$session['revoked_at']!==null||(int)$session['session_version']!==(int)$user['session_version']||$session['family_id']!==$actor['family_id']||strtotime($session['expires_at'].' UTC')<=time())throw new ApiError('unauthenticated',401);
            $q=$this->db->prepare("UPDATE api_sessions SET revoked_at=UTC_TIMESTAMP(),revoked_reason='user_revoked' WHERE user_id=? AND family_id<>? AND revoked_at IS NULL");$q->execute([(int)$actor['id'],$session['family_id']]);$count=$q->rowCount();
            $this->audit((int)$actor['id'],'api-other-sessions-revoked');$this->db->commit();return $count;
        }catch(Throwable $error){if($this->db->inTransaction())$this->db->rollBack();throw $error;}
    }

    public function requestReset(string $email): void
    {
        $q=$this->db->prepare('SELECT id FROM users WHERE email=? AND suspended_at IS NULL LIMIT 1'); $q->execute([strtolower(trim($email))]); $id=$q->fetchColumn();
        if ($id) $this->otp->issue((int)$id,'password_reset');
    }

    public function reset(string $email,string $code,string $password,string $confirmation): void
    {
        $this->validatePassword($password,$confirmation); $this->db->beginTransaction();
        try {
            $q=$this->db->prepare('SELECT id FROM users WHERE email=? AND suspended_at IS NULL LIMIT 1 FOR UPDATE'); $q->execute([strtolower(trim($email))]); $id=$q->fetchColumn();
            if (!$id || !$this->otp->consume((int)$id,'password_reset',$code)) { $this->db->commit(); throw new ApiError('invalid_or_expired_code',422); }
            $this->db->prepare('UPDATE users SET password_hash=?,session_version=session_version+1 WHERE id=?')->execute([$this->passwordHash($password),$id]);
            $this->revokeAll((int)$id,'password_reset'); $this->audit((int)$id,'api-password-reset'); $this->db->commit();
        } catch (Throwable $e) { if ($this->db->inTransaction()) $this->db->rollBack(); throw $e; }
    }

    public function changePassword(array $actor,string $current,string $password,string $confirmation,bool $issueSession=true): array
    {
        $this->validatePassword($password,$confirmation); $this->db->beginTransaction();
        try {
            $this->lockPasswordActor($actor,$current);
            $this->db->prepare('UPDATE users SET password_hash=?,session_version=session_version+1 WHERE id=?')->execute([$this->passwordHash($password),$actor['id']]);
            $this->revokeAll((int)$actor['id'],'password_changed'); $session=$issueSession?$this->newSession((int)$actor['id'],'full'):null; $this->audit((int)$actor['id'],'api-password-changed'); $this->db->commit(); return ['session'=>$session];
        } catch(Throwable $e) { if($this->db->inTransaction())$this->db->rollBack();throw $e; }
    }

    public function updateProfile(int $userId,string $name,string $username): array
    {
        $name=trim($name);$username=strtolower(trim($username));$this->validateProfile($name,$username);
        try { $this->db->prepare('UPDATE users SET display_name=?,username=? WHERE id=?')->execute([$name,$username,$userId]); }
        catch(PDOException $e){if($e->getCode()==='23000')throw new ApiError('account_conflict',409);throw $e;}
        $this->audit($userId,'api-profile-updated');return $this->profile($userId);
    }

    public function delete(array $actor,string $password): void
    {
        $this->db->beginTransaction();
        try {
            $this->lockPasswordActor($actor,$password);
            $this->db->prepare("UPDATE email_campaign_recipients SET user_id=NULL,email='',status=IF(status IN ('pending','processing'),'skipped',status),failure_note='Account deleted' WHERE user_id=?")->execute([$actor['id']]);
            $this->audit((int)$actor['id'],'api-account-deleted'); $this->db->prepare('DELETE FROM users WHERE id=?')->execute([$actor['id']]); $this->db->commit();
        } catch(Throwable $e){if($this->db->inTransaction())$this->db->rollBack();throw $e;}
    }

    public function profile(int $userId): array
    {
        $q=$this->db->prepare('SELECT id,email,display_name,username,role,email_verified_at,preferences_json FROM users WHERE id=?');$q->execute([$userId]);$user=$q->fetch(PDO::FETCH_ASSOC);
        if(!$user)throw new ApiError('unauthenticated',401); return $this->publicProfile($user);
    }

    /** Caller holds a transaction; use a current credential and revocation snapshot. */
    private function lockPasswordActor(array $actor,string $password):void
    {
        $q=$this->db->prepare('SELECT * FROM users WHERE id=? FOR UPDATE');$q->execute([(int)$actor['id']]);$fresh=$q->fetch(PDO::FETCH_ASSOC);
        if(!$fresh||$fresh['suspended_at']!==null||(int)$fresh['session_version']!==(int)$actor['session_version'])throw new ApiError('unauthenticated',401);
        if(isset($actor['session_id'])){
            $q=$this->db->prepare('SELECT scope,revoked_at,expires_at,session_version FROM api_sessions WHERE id=? AND user_id=? FOR UPDATE');$q->execute([$actor['session_id'],$actor['id']]);$session=$q->fetch(PDO::FETCH_ASSOC);
            if(!$session||$session['revoked_at']!==null||$session['scope']!=='full'||empty($fresh['email_verified_at'])||(int)$session['session_version']!==(int)$fresh['session_version']||strtotime($session['expires_at'].' UTC')<=time())throw new ApiError('unauthenticated',401);
        }
        if(!password_verify($password,$fresh['password_hash']))throw new ApiError('invalid_credentials',422);
    }

    private function publicProfile(array $row): array
    {
        return ['id'=>(int)$row['id'],'email'=>$row['email'],'display_name'=>$row['display_name'],'username'=>$row['username'],'role'=>$row['role'],'email_verified'=>$row['email_verified_at']!==null,'preferences'=>json_decode($row['preferences_json']??'{}',true)?:new \stdClass()];
    }

    private function newSession(int $userId,string $scope,?string $family=null,?string $refreshExpiry=null): array
    {
        $q=$this->db->prepare('SELECT session_version FROM users WHERE id=?');$q->execute([$userId]);$version=(int)$q->fetchColumn();
        $id=bin2hex(random_bytes(16));$access=bin2hex(random_bytes(32));$refresh=bin2hex(random_bytes(32));$family??=bin2hex(random_bytes(16));$refreshExpiry??=gmdate('Y-m-d H:i:s',time()+30*86400);
        $this->db->prepare('INSERT INTO api_sessions (id,user_id,family_id,access_hash,refresh_hash,scope,session_version,expires_at,refresh_expires_at,created_at) VALUES (?,?,?,?,?,?,?,DATE_ADD(UTC_TIMESTAMP(),INTERVAL 15 MINUTE),?,UTC_TIMESTAMP())')->execute([$id,$userId,$family,hash('sha256',$access),hash('sha256',$refresh),$scope,$version,$refreshExpiry]);
        return ['id'=>$id,'token_type'=>'Bearer','access_token'=>$access,'refresh_token'=>$refresh,'expires_in'=>900,'refresh_expires_at'=>str_replace(' ','T',$refreshExpiry).'Z','scope'=>$scope];
    }

    private function revokeAll(int $userId,string $reason): void { $this->db->prepare('UPDATE api_sessions SET revoked_at=UTC_TIMESTAMP(),revoked_reason=? WHERE user_id=? AND revoked_at IS NULL')->execute([$reason,$userId]); }
    private function validateProfile(string $name,string $username): void
    {
        if(mb_strlen($name,'UTF-8')<2||mb_strlen($name,'UTF-8')>80)throw new ApiError('invalid_fields',422,['display_name'=>'invalid']);
        if(!preg_match('/^[a-z0-9_]{3,24}$/',$username)||in_array($username,['admin','administrator','root','support','athar','api','www','owner'],true))throw new ApiError('invalid_fields',422,['username'=>'invalid']);
    }
    private function validatePassword(string $password,string $confirmation): void
    {
        if(mb_strlen($password,'UTF-8')<10||mb_strlen($password,'UTF-8')>200||str_contains($password,"\0"))throw new ApiError('invalid_fields',422,['password'=>'invalid']);
        if($this->algorithm()===PASSWORD_BCRYPT&&strlen($password)>72)throw new ApiError('invalid_fields',422,['password'=>'maximum_72_bytes']);
        if(!hash_equals($password,$confirmation))throw new ApiError('invalid_fields',422,['password_confirmation'=>'mismatch']);
    }
    private function algorithm(): string { return defined('PASSWORD_ARGON2ID') ? PASSWORD_ARGON2ID : PASSWORD_BCRYPT; }
    private function passwordHash(string $password): string { return password_hash($password,$this->algorithm()); }
    private function audit(int $id,string $action): void { $this->db->prepare("INSERT INTO audit_logs (actor_id,action,target_type,target_id) VALUES (?,?,'user',?)")->execute([$id,$action,(string)$id]); }
}
