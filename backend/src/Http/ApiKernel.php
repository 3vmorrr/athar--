<?php
declare(strict_types=1);
namespace Athar\Http;
use Athar\Identity\AuthService;
use Athar\Identity\RateLimiter;
use Athar\Domain\QuranIndex;
use Athar\Domain\QuranText;
use Athar\Catalog\CatalogService;
use Athar\Progress\ProgressService;
use Athar\NightPrayer\NightPrayerService;
use PDO;
use Throwable;
use InvalidArgumentException;

final class ApiKernel
{
    private PDO $db; private AuthService $auth; private RateLimiter $rates; private CatalogService $catalog; private ProgressService $progress; private ?NightPrayerService $nightPrayer;
    public function __construct(PDO $db, AuthService $auth, RateLimiter $rates, ?NightPrayerService $nightPrayer=null) { $this->db=$db;$this->auth=$auth;$this->rates=$rates;$this->catalog=new CatalogService($db);$this->progress=new ProgressService($db,$this->catalog);$this->nightPrayer=$nightPrayer; }
    public function handle(Request $r): Response
    {
        $id=bin2hex(random_bytes(8));
        try { [$data,$status]=$this->dispatch($r);return new Response(['data'=>$data,'meta'=>['request_id'=>$id]],$status); }
        catch(ApiError $e) { return $this->error($r,$e->getMessage(),$e->status,$e->fields,$id); }
        catch(InvalidArgumentException $e) { return $this->error($r,'invalid_fields',422,['input'=>$e->getMessage()],$id); }
        catch(Throwable $e) { error_log('[Athar API '.$id.'] '.get_class($e).': '.$e->getMessage());return $this->error($r,'server_error',500,[],$id); }
    }
    public function error(Request $r,string $code,int $status,array $fields=[],?string $id=null): Response
    {
        $messages=require dirname(__DIR__,2).'/resources/errors.php';$language=str_starts_with(strtolower((string)($r->headers['accept-language']??'ar')),'en')?'en':'ar';
        return new Response(['error'=>['code'=>$code,'message'=>$messages[$language][$code]??$messages[$language]['server_error'],'fields'=>(object)$fields],'meta'=>['request_id'=>$id??bin2hex(random_bytes(8))]],$status);
    }
    private function dispatch(Request $r): array
    {
        $path=substr($r->path,strlen('/api/v1'))?:'/';
        if($path==='/health'){$this->method($r,'GET');return [['api_version'=>1,'status'=>'available'],200];}
        if($path==='/auth/register'){
            $this->method($r,'POST');$email=$r->string('email',190);$this->authRate($r,'register',strtolower(trim($email)),5,3600);
            $input=[];foreach(['email','display_name','username','password','password_confirmation'] as $field)$input[$field]=$r->string($field,200);
            return [$this->auth->register($input),202];
        }
        if($path==='/auth/login'){$this->method($r,'POST');$email=$r->string('email',190);$this->authRate($r,'login',strtolower(trim($email)),10,900);return [$this->auth->login($email,$r->string('password',200)),200];}
        if($path==='/auth/refresh'){$this->method($r,'POST');$this->authRate($r,'refresh','',40,60);return [$this->auth->refresh($r->string('refresh_token',64)),200];}
        if($path==='/auth/forgot-password'){$this->method($r,'POST');$email=$r->string('email',190);$this->authRate($r,'forgot',strtolower(trim($email)),3,900);$this->auth->requestReset($email);return [['status'=>'if_registered_email_will_be_sent'],202];}
        if($path==='/auth/reset-password'){$this->method($r,'POST');$email=$r->string('email',190);$this->authRate($r,'reset',strtolower(trim($email)),5,900);$this->auth->reset($email,$r->string('code',6),$r->string('password',200),$r->string('password_confirmation',200));return [['status'=>'password_updated'],200];}
        $actor=$this->auth->authenticate($r->bearer(),false);
        if($path==='/auth/logout'){$this->method($r,'POST');$this->auth->logout($actor);return [['status'=>'logged_out'],200];}
        if($path==='/auth/verify-email'){$this->method($r,'POST');$this->authRate($r,'verify',(string)$actor['id'],5,900);return [$this->auth->verify((int)$actor['id'],$r->string('code',6)),200];}
        if($path==='/auth/resend'){$this->method($r,'POST');$this->authRate($r,'resend',(string)$actor['id'],3,900);return [['delivery_status'=>$this->auth->resend((int)$actor['id'])?'accepted':'unavailable'],202];}
        if($actor['scope']!=='full'||empty($actor['email_verified_at']))throw new ApiError('email_verification_required',403);
        if($path==='/upcoming-lessons'){
            $this->method($r,'GET');return [(new \Athar\Learning\UpcomingLessonService($this->db))->page($r->query,str_starts_with(strtolower((string)($r->headers['accept-language']??'ar')),'en')?'en':'ar'),200];
        }
        if(preg_match('#^/upcoming-lessons/([1-9][0-9]*)$#',$path,$m)){
            $this->method($r,'GET');return [(new \Athar\Learning\UpcomingLessonService($this->db))->detail((int)$m[1],str_starts_with(strtolower((string)($r->headers['accept-language']??'ar')),'en')?'en':'ar'),200];
        }
        if($path==='/admin/lesson-events'){
            $this->method($r,'GET');return [(new \Athar\Learning\UpcomingLessonService($this->db))->page($r->query,'ar',$actor),200];
        }
        if(preg_match('#^/admin/lessons/([1-9][0-9]*)/event$#',$path,$m)){
            $events=new \Athar\Learning\UpcomingLessonService($this->db);
            if($r->method==='GET')return [$events->detail((int)$m[1],'ar',$actor),200];
            $this->method($r,'PUT');return [$events->save($actor,(int)$m[1],$r->body),200];
        }
        if(in_array($path,['/quran/tafsirs','/quran/tafsir'],true)){
            $this->method($r,'GET');
            if(!$this->rates->consume('quranpedia-shared-minute','shared',50,60)||!$this->rates->consume('quranpedia-shared-day','shared',4000,86400))throw new ApiError('rate_limited',429);
            $client=new \Athar\Infrastructure\PublicProviderJson(dirname(__DIR__,3).'/storage/cache/providers');$provider=new \Athar\Providers\Quranpedia([$client,'quranpedia']);
            return [$path==='/quran/tafsirs'?$provider->books($r->query):$provider->content($r->query),200];
        }
        if(in_array($path,['/quran/reciters','/quran/audio','/quran/timing','/quran/radios'],true)){
            $this->method($r,'GET');
            if(!$this->rates->consume('media-api',(string)$actor['id'],120,60))throw new ApiError('rate_limited',429);
            $client=new \Athar\Infrastructure\PublicProviderJson(dirname(__DIR__,3).'/storage/cache/providers');
            $provider=new \Athar\Providers\Mp3Quran([$client,'mp3']);
            $params=$r->query;$params['language']??=str_starts_with(strtolower((string)($r->headers['accept-language']??'ar')),'en')?'en':'ar';
            $operation=substr($path,strrpos($path,'/')+1);return [$provider->$operation($params),200];
        }
        if(preg_match('#^/scholars/([1-9][0-9]*)/profile$#',$path,$m)){$this->method($r,'GET');return [$this->catalog->detail('scholars',(int)$m[1],false),200];}
        if(preg_match('#^/lessons/([1-9][0-9]*)/watch$#',$path,$m)){$this->method($r,'GET');return [(new \Athar\Learning\LessonService($this->db))->watch((int)$actor['id'],(int)$m[1]),200];}
        if(preg_match('#^/series/([1-9][0-9]*)/(overview|lessons)$#',$path,$m)){$this->method($r,'GET');$learning=new \Athar\Learning\LessonService($this->db);return [$m[2]==='overview'?$learning->series((int)$actor['id'],(int)$m[1]):$learning->playlist((int)$actor['id'],(int)$m[1],$r->query),200];}
        if(preg_match('#^/lessons/([1-9][0-9]*)/discussion$#',$path,$m)){
            $discussion=new \Athar\Learning\DiscussionService($this->db);$uid=(int)$actor['id'];if($r->method==='GET')return [$discussion->page($uid,(int)$m[1],$r->query),200];
            $this->method($r,'POST');return [$discussion->add($uid,(int)$m[1],$r->body,fn():bool=>$this->rates->consume('comments',(string)$uid,3,3600)),201];
        }
        if($path==='/admin/comments'){$this->method($r,'GET');return [(new \Athar\Learning\DiscussionService($this->db))->moderation($actor,$r->query),200];}
        if(preg_match('#^/admin/comments/([1-9][0-9]*)$#',$path,$m)){$this->method($r,'PUT');return [(new \Athar\Learning\DiscussionService($this->db))->moderate($actor,(int)$m[1],$r->body),200];}
        if($path==='/me'){
            if($r->method==='GET')return [$this->auth->profile((int)$actor['id']),200];
            if($r->method==='PATCH')return [$this->auth->updateProfile((int)$actor['id'],$r->string('display_name',80),$r->string('username',24)),200];
            if($r->method==='DELETE'){$this->auth->delete($actor,$r->string('password',200));return [['status'=>'deleted'],200];}
            throw new ApiError('method_not_allowed',405);
        }
        if($path==='/me/password'){$this->method($r,'PUT');return [$this->auth->changePassword($actor,$r->string('current_password',200),$r->string('password',200),$r->string('password_confirmation',200)),200];}
        if($path==='/quran/metadata'){$this->method($r,'GET');return [QuranIndex::standard()->metadata(),200];}
        if($path==='/quran/surahs'){$this->method($r,'GET');return [QuranIndex::standard()->metadata()['surahs'],200];}
        if($path==='/quran/ayahs'){$this->method($r,'GET');return [QuranText::standard()->read($r->query),200];}
        if($path==='/quran/search'){$this->method($r,'GET');return [QuranText::standard()->search((string)($r->query['q']??''),(int)($r->query['page']??1),(int)($r->query['per_page']??25)),200];}
        if($path==='/quran/daily'){$this->method($r,'GET');return [QuranText::standard()->daily((string)($r->query['date']??gmdate('Y-m-d'))),200];}
        if($path==='/quran/calculate'){
            $this->method($r,'GET');$index=QuranIndex::standard();$s=(int)($r->query['surah']??0);$a=(int)($r->query['ayah']??0);
            return [isset($r->query['target'])?$index->target($s,$a,(int)$r->query['target']):$index->range($s,$a,(int)($r->query['end_surah']??0),(int)($r->query['end_ayah']??0)),200];
        }
        $user=(int)$actor['id'];
        if($path==='/home'){$this->method($r,'GET');return [$this->catalog->home($user),200];}
        if($path==='/adhkar/categories'){$this->method($r,'GET');return [$this->catalog->adhkarCategories(),200];}
        if(preg_match('#^/(books|scholars|series|lessons|adhkar)(?:/([1-9][0-9]*))?$#',$path,$m)){$this->method($r,'GET');return [isset($m[2])?$this->catalog->detail($m[1],(int)$m[2]):$this->catalog->list($m[1],$r->query),200];}
        if($path==='/quran/position'){
            if($r->method==='GET')return [$this->progress->position($user),200];
            $this->method($r,'PUT');return [$this->progress->savePosition($user,$r->body),200];
        }
        if($path==='/quran/bookmarks'){
            if($r->method==='GET')return [$this->progress->bookmarks($user),200];
            if($r->method==='DELETE'){$this->progress->deleteBookmarkAt($user,$r->body);return [['status'=>'deleted'],200];}
            $this->method($r,'POST');return [$this->progress->addBookmark($user,$r->body),201];
        }
        if(preg_match('#^/quran/bookmarks/([1-9][0-9]*)$#',$path,$m)){$this->method($r,'DELETE');$this->progress->deleteBookmark($user,(int)$m[1]);return [['status'=>'deleted'],200];}
        if($path==='/me/favorites'){
            if($r->method==='GET')return [$this->progress->favorites($user),200];
            if(!in_array($r->method,['PUT','DELETE'],true))throw new ApiError('method_not_allowed',405);
            $id=$r->body['target_id']??null;if(!is_int($id)||$id<1)throw new ApiError('invalid_fields',422,['target_id'=>'invalid']);
            $this->progress->favorite($user,$r->string('target_type',12),$id,$r->method!=='DELETE');return [['saved'=>$r->method!=='DELETE'],200];
        }
        if($path==='/me/adhkar-progress'){
            if($r->method==='GET')return [$this->progress->adhkar($user,$r->query),200];
            $this->method($r,'PUT');return [$this->progress->saveAdhkar($user,$r->body),200];
        }
        if($path==='/me/preferences'){
            if($r->method==='GET')return [$this->progress->preferences($user),200];
            $this->method($r,'PATCH');return [$this->progress->preferences($user,$r->body),200];
        }
        if($path==='/me/engagement-preferences'){
            $engagementPreferences=new \Athar\Engagement\PreferenceService($this->db);
            if($r->method==='GET')return [$engagementPreferences->get($user),200];
            $this->method($r,'PATCH');return [$engagementPreferences->update($user,$r->body),200];
        }
        if($path==='/me/khatmah'){
            if($r->method==='GET')return [$this->progress->khatmah($user),200];
            $this->method($r,'PUT');return [$this->progress->saveKhatmah($user,$r->body),200];
        }
        if($path==='/me/lesson-progress'){
            if($r->method==='GET')return [$this->progress->lessonProgress($user,(int)($r->query['lesson_id']??0)),200];
            $this->method($r,'PUT');return [$this->progress->saveLessonProgress($user,$r->body),200];
        }
        if($path==='/me/activity'){$this->method($r,'POST');$this->progress->activity($user,$r->string('event_id',36),$r->string('activity_type',32));return [['recorded'=>true],200];}
        if(preg_match('#^/lessons/([1-9][0-9]*)/comments$#',$path,$m)){
            if($r->method==='GET')return [$this->progress->comments($user,(int)$m[1]),200];
            $this->method($r,'POST');if(!$this->rates->consume('comments',(string)$user,3,3600))throw new ApiError('rate_limited',429);return [$this->progress->addComment($user,(int)$m[1],$r->string('body',2000)),201];
        }
        if(preg_match('#^/books/([1-9][0-9]*)/download$#',$path,$m)){
            $this->method($r,'GET');$book=$this->catalog->detail('books',(int)$m[1]);if(!$book['can_download'])throw new ApiError('not_found',404);
            $q=$this->db->prepare('SELECT download_url FROM books WHERE id=?');$q->execute([(int)$m[1]]);$url=$q->fetchColumn();
            if(isset($r->headers['idempotency-key']))$this->progress->activity($user,(string)$r->headers['idempotency-key'],'book_download');return [['url'=>$url,'mode'=>'direct','title'=>$book['title']],200];
        }
        if($path==='/me/export'){
            $this->method($r,'GET');$export=['profile'=>$this->auth->profile($user)];
            foreach(['quran_bookmarks','quran_positions','khatmah_plans','user_favorites','video_progress','account_daily_stats','adhkar_progress'] as $table){$q=$this->db->prepare('SELECT * FROM '.$table.' WHERE user_id=?');$q->execute([$user]);$export[$table]=$q->fetchAll();}
            if($this->nightPrayer){$all=[];$page=1;do{$part=$this->nightPrayer->sessions($user,['page'=>$page++,'per_page'=>50]);$all=array_merge($all,$part['items']);}while($part['pagination']['has_more']);$export['night_prayer_sessions']=$all;$export['night_prayer_preferences']=$this->nightPrayer->preferences($user);}
            return [$export,200];
        }
        if(str_starts_with($path,'/night-prayer/')){
            if(!$this->nightPrayer)throw new ApiError('service_unavailable',503);
            if($path==='/night-prayer/config'){$this->method($r,'GET');return [$this->nightPrayer->publicConfig(),200];}
            if($path==='/night-prayer/sessions'){
                if($r->method==='GET')return [$this->nightPrayer->sessions($user,$r->query),200];
                $this->method($r,'POST');return [$this->nightPrayer->write($user,$r->string('id',36),$r->body),201];
            }
            if(preg_match('#^/night-prayer/sessions/([a-fA-F0-9-]{36})$#',$path,$m)){
                if($r->method==='GET')return [$this->nightPrayer->get($user,$m[1]),200];
                if(!in_array($r->method,['PUT','DELETE'],true))throw new ApiError('method_not_allowed',405);
                return [$this->nightPrayer->write($user,$m[1],$r->body,$r->method==='DELETE'),200];
            }
            if($path==='/night-prayer/sync'){$this->method($r,'POST');if(!$this->rates->consume('night-sync',(string)$user,30,60))throw new ApiError('rate_limited',429);return [$this->nightPrayer->sync($user,$r->body),200];}
            if($path==='/night-prayer/preferences'){
                if($r->method==='GET')return [$this->nightPrayer->preferences($user),200];
                $this->method($r,'PUT');return [$this->nightPrayer->preferences($user,$r->body),200];
            }
            if(in_array($path,['/night-prayer/overview','/night-prayer/analytics','/night-prayer/calendar'],true)){
                $this->method($r,'GET');$current=isset($r->query['current_night'])?(string)$r->query['current_night']:null;$active=in_array($r->query['night_in_progress']??false,[true,'true','1'],true);$analytics=$this->nightPrayer->analytics($user,$current,$active);
                if($path==='/night-prayer/overview'){$analytics['recent_sessions']=$this->nightPrayer->sessions($user,['per_page'=>5])['items'];unset($analytics['nights']);}
                if($path==='/night-prayer/calendar'){$month=(string)($r->query['month']??'');if(!preg_match('/^\d{4}-(?:0[1-9]|1[0-2])$/',$month))throw new ApiError('invalid_fields',422,['month'=>'invalid']);$analytics=['month'=>$month,'nights'=>array_values(array_filter($analytics['nights'],static fn(array $night):bool=>str_starts_with($night['night_date'],$month.'-')))];}
                return [$analytics,200];
            }
        }
        throw new ApiError('not_found',404);
    }
    private function method(Request $r,string $method): void { if($r->method!==$method)throw new ApiError('method_not_allowed',405); }
    private function authRate(Request $r,string $scope,string $identity,int $limit,int $window): void
    {
        if(!$this->rates->consume($scope.':ip',$r->ip,$limit*5,$window)||($identity!==''&&!$this->rates->consume($scope.':identity',$identity,$limit,$window)))throw new ApiError('rate_limited',429);
    }
}
