<?php
declare(strict_types=1);

use Athar\Engagement\LessonReminderService;
use Athar\Infrastructure\Database;

require_once dirname(__DIR__).'/backend/bootstrap.php';

if(PHP_SAPI!=='cli'){
    fwrite(STDERR,"CLI only.\n");
    exit(2);
}
$limit=isset($argv[1])&&ctype_digit((string)$argv[1])?max(1,min(100,(int)$argv[1])):20;
try{
    $result=(new LessonReminderService(Database::connect()))->processDueBatch($limit);
    fwrite(STDOUT,json_encode($result,JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES|JSON_THROW_ON_ERROR).PHP_EOL);
    exit(0);
}catch(Throwable $failure){
    error_log('[Athar lesson reminder worker] '.$failure->getMessage());
    fwrite(STDERR,"Lesson reminder worker failed.\n");
    exit(1);
}
