<?php
declare(strict_types=1);
namespace Athar\Engagement;

use PDO;
use RuntimeException;

/** Independent authenticated outbox key; neither settings nor private worship keys. */
final class EmailPayloadCipher
{
    public function __construct(private PDO $db,private ?string $key=null)
    {
        if($key!==null&&strlen($key)!==32)throw new RuntimeException('invalid_email_payload_key');
    }
    private function key(): string
    {
        if($this->key!==null)return $this->key;
        $hex=trim((string)(getenv('ATHAR_EMAIL_OUTBOX_KEY')?:''));
        if($hex===''){
            $directory=dirname(__DIR__,3).'/storage/secrets';$path=$directory.'/email-outbox.key';
            if(!is_file($path)){
                if((int)$this->db->query("SELECT COUNT(*) FROM email_outbox WHERE plain_body LIKE 'enc:v1:%'")->fetchColumn()>0)throw new RuntimeException('email_payload_original_key_required');
                if(!is_dir($directory)&&!@mkdir($directory,0700,true)&&!is_dir($directory))throw new RuntimeException('email_payload_key_unavailable');
                $handle=@fopen($path,'x');
                if($handle){$generated=bin2hex(random_bytes(32));$written=fwrite($handle,$generated);fflush($handle);if(function_exists('fsync'))fsync($handle);fclose($handle);@chmod($path,0600);if($written!==64)throw new RuntimeException('email_payload_key_unavailable');}
            }
            $hex=trim((string)@file_get_contents($path));
        }
        if(!preg_match('/^[a-f0-9]{64}$/iD',$hex))throw new RuntimeException('email_payload_key_unavailable');
        return $this->key=(string)hex2bin($hex);
    }
    public function seal(array $payload,string $context): string
    {
        $json=json_encode($payload,JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES|JSON_THROW_ON_ERROR);
        if(strlen($json)>1048576)throw new RuntimeException('email_payload_too_large');
        $iv=random_bytes(12);$tag='';
        $cipher=openssl_encrypt($json,'aes-256-gcm',$this->key(),OPENSSL_RAW_DATA,$iv,$tag,'athar-email:v1:'.$context);
        if($cipher===false||strlen($tag)!==16)throw new RuntimeException('email_payload_unavailable');
        return 'enc:v1:'.base64_encode($iv.$tag.$cipher);
    }
    public function open(string $value,string $context): array
    {
        if(!str_starts_with($value,'enc:v1:'))throw new RuntimeException('email_payload_unrecognized');
        $packed=base64_decode(substr($value,7),true);
        if(!is_string($packed)||strlen($packed)<29||strlen($packed)>1500000)throw new RuntimeException('email_payload_unreadable');
        $json=openssl_decrypt(substr($packed,28),'aes-256-gcm',$this->key(),OPENSSL_RAW_DATA,substr($packed,0,12),substr($packed,12,16),'athar-email:v1:'.$context);
        if(!is_string($json))throw new RuntimeException('email_payload_unreadable');
        $payload=json_decode($json,true,16,JSON_THROW_ON_ERROR);
        if(!is_array($payload))throw new RuntimeException('email_payload_unreadable');
        return $payload;
    }
}
