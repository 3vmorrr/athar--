<?php
declare(strict_types=1);

use Athar\Engagement\NewContentFactService;
use Athar\Infrastructure\Database;

require_once dirname(__DIR__).'/backend/bootstrap.php';

if(PHP_SAPI!=='cli'){
    fwrite(STDERR,"CLI only.\n");
    exit(2);
}
$limit=isset($argv[1])&&ctype_digit((string)$argv[1])?max(1,min(1000,(int)$argv[1])):200;
try{
    $result=(new NewContentFactService(Database::connect()))->scanAll($limit);
    fwrite(STDOUT,json_encode($result,JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES|JSON_THROW_ON_ERROR).PHP_EOL);
    exit(0);
}catch(Throwable $failure){
    error_log('[Athar content fact scanner] '.$failure->getMessage());
    fwrite(STDERR,"Content fact scan failed.\n");
    exit(1);
}
