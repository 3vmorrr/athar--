<?php
declare(strict_types=1);

namespace Athar\Engagement;

final class PushProviderConfig
{
    public function __construct(
        public readonly string $provider,
        public readonly ?string $projectId,
        public readonly ?string $serviceAccountFile,
    ) {
        if(!in_array($provider,['disabled','fcm_v1'],true))throw new \InvalidArgumentException('invalid_push_provider');
        if($provider==='fcm_v1'){
            if($projectId===null||!preg_match('/^[a-z][a-z0-9-]{4,62}$/',$projectId))throw new \InvalidArgumentException('invalid_fcm_project');
            if($serviceAccountFile===null||$serviceAccountFile===''||$serviceAccountFile[0]!=='/')throw new \InvalidArgumentException('invalid_fcm_credential_path');
        }
    }

    public static function fromEnvironment(): self
    {
        if(trim((string)(getenv('ATHAR_FCM_PRIVATE_KEY')?:''))!==''){
            throw new \RuntimeException('inline_fcm_private_key_forbidden');
        }
        $provider=trim((string)(getenv('ATHAR_PUSH_PROVIDER')?:'disabled'));
        $project=trim((string)(getenv('ATHAR_FCM_PROJECT_ID')?:''))?:null;
        $file=trim((string)(getenv('ATHAR_FCM_SERVICE_ACCOUNT_FILE')?:''))?:null;
        return new self($provider,$project,$file);
    }

    public function credentialPath(): string
    {
        if($this->provider!=='fcm_v1'||$this->serviceAccountFile===null)throw new \LogicException('fcm_not_configured');
        $real=realpath($this->serviceAccountFile);
        if($real===false||!is_file($real)||!is_readable($real))throw new \RuntimeException('fcm_credential_unreadable');
        $root=realpath(dirname(__DIR__,3));
        if($root!==false&&str_starts_with($real,rtrim($root,DIRECTORY_SEPARATOR).DIRECTORY_SEPARATOR)){
            throw new \RuntimeException('fcm_credential_must_be_outside_repo');
        }
        return $real;
    }
}
