<?php
declare(strict_types=1);
require __DIR__.'/integration_support.php';
use Athar\Catalog\CatalogService;
use Athar\Progress\ProgressService;
use Athar\Http\ApiError;
$checks=0;function assert_progress(bool $v,string $name):void{global $checks;$checks++;if(!$v)throw new RuntimeException('FAIL: '.$name);}
function reject_progress(callable $call,int $status,string $name):void{try{$call();}catch(ApiError $e){assert_progress($e->status===$status,$name);return;}catch(InvalidArgumentException $e){assert_progress($status===422,$name);return;}assert_progress(false,$name);}
isolated_database(static function(PDO $db):void{
    foreach(['one','two'] as $name)$db->prepare("INSERT INTO users (email,username,display_name,password_hash,email_verified_at) VALUES (?,?,?,'unused',UTC_TIMESTAMP())")->execute([$name.'@example.invalid',$name,$name]);
    $u=1;$other=2;$db->exec("INSERT INTO scholars (name,is_published) VALUES ('Published',1),('Draft',0)");$db->exec("INSERT INTO series (scholar_id,title,is_published) VALUES (1,'Series',1),(2,'Private Parent',1)");$db->exec("INSERT INTO lessons (scholar_id,series_id,title,youtube_id,source_url,is_published) VALUES (1,1,'Visible Lesson','abcdefghijk','https://youtu.be/abcdefghijk',1),(2,2,'Hidden Lesson','bcdefghijkl','https://youtu.be/bcdefghijkl',1)");
    $db->exec("INSERT INTO books (title,author,keywords,source_url,download_url,rights_basis,can_download,is_published) VALUES ('A Book','Author Query','index-term','https://example.invalid/source','https://example.invalid/book.pdf','Permission',1,1),('Draft Book','Author','','https://example.invalid/source',NULL,'',0,0)");
    $catalog=new CatalogService($db);$progress=new ProgressService($db,$catalog);
    assert_progress($catalog->list('scholars',[])['pagination']['total']===1,'draft scholar hidden');assert_progress($catalog->list('lessons',[])['pagination']['total']===1,'draft parent lesson hidden');reject_progress(fn()=>$catalog->detail('lessons',2),404,'direct hidden lesson unavailable');assert_progress($catalog->list('books',['q'=>'Author Query'])['pagination']['total']===1,'book author search');assert_progress($catalog->list('books',['q'=>'index-term'])['pagination']['total']===1,'book keyword search');assert_progress($catalog->detail('books',1)['can_download']===true,'rights-authorized direct book');
    $db->exec("UPDATE books SET download_url='https://127.0.0.1/private.pdf' WHERE id=1");
    assert_progress($catalog->detail('books',1)['can_download']===false,'unsafe direct URL cannot be advertised as downloadable');
    reject_progress(fn()=>$catalog->bookDownload(1),404,'download endpoint enforces the same public destination policy');
    $db->exec("UPDATE books SET download_url='https://example.invalid/book.pdf' WHERE id=1");
    assert_progress($catalog->bookDownload(1)['url']==='https://example.invalid/book.pdf','approved direct link remains available without upload');
    $mark=$progress->addBookmark($u,['surah'=>114,'ayah'=>6,'label'=>'Last']);assert_progress(count($progress->bookmarks($u))===1,'last-surah bookmark supported');reject_progress(fn()=>$progress->deleteBookmark($other,(int)$mark['id']),404,'bookmark owner enforced');assert_progress($progress->bookmarks($other)===[],'other-user bookmark list isolated');
    $progress->deleteBookmarkAt($other,['surah'=>114,'ayah'=>6]);assert_progress(count($progress->bookmarks($u))===1,'coordinate delete cannot remove another account bookmark');
    $progress->deleteBookmarkAt($u,['surah'=>114,'ayah'=>6]);$progress->deleteBookmarkAt($u,['surah'=>114,'ayah'=>6]);assert_progress($progress->bookmarks($u)===[],'coordinate delete is idempotent');
    reject_progress(fn()=>$progress->deleteBookmarkAt($u,['surah'=>114,'ayah'=>7]),422,'coordinate delete validates canonical ayah');
    $saved=$progress->savePosition($u,['surah'=>114,'ayah'=>6,'expected_revision'=>0]);assert_progress((int)$saved['revision']===1,'initial reading position revision');reject_progress(fn()=>$progress->savePosition($u,['surah'=>1,'ayah'=>1,'expected_revision'=>0]),409,'stale position rejected');$next=$progress->savePosition($u,['surah'=>1,'ayah'=>1,'expected_revision'=>1]);assert_progress((int)$next['revision']===2,'valid reading position CAS');
    $progress->favorite($u,'series',1,true);$progress->favorite($u,'series',1,true);assert_progress(count($progress->favorites($u))===1,'series favorite idempotent');assert_progress($progress->favorites($other)===[],'favorites private');reject_progress(fn()=>$progress->favorite($u,'book',2,true),404,'draft book cannot be favorited');
    $event=test_uuid();$progress->activity($u,$event,'quran_session');$progress->activity($u,$event,'quran_session');assert_progress((int)$db->query('SELECT quran_sessions FROM account_daily_stats')->fetchColumn()===1,'activity retry counted once');reject_progress(fn()=>$progress->activity($u,$event,'book_download'),409,'event ID cannot be reused for a different kind');
    $lesson=['lesson_id'=>1,'position_seconds'=>90,'duration_seconds'=>100,'is_complete'=>true,'event_id'=>test_uuid()];$progress->saveLessonProgress($u,$lesson);$progress->saveLessonProgress($u,$lesson);assert_progress((int)$db->query('SELECT lesson_completions FROM account_daily_stats')->fetchColumn()===1,'completion retry does not inflate totals');
    $progress->addComment($u,1,'A pending comment');assert_progress(count($progress->comments($u,1))===1,'author sees own pending comment');assert_progress($progress->comments($other,1)===[],'pending text hidden from other user');$db->exec("UPDATE lesson_comments SET status='visible'");assert_progress(count($progress->comments($other,1))===1,'visible comment can be read');
    $plan=$progress->saveKhatmah($u,['target_date'=>gmdate('Y-m-d',time()+86400),'completed_pages'=>600,'status'=>'active','expected_revision'=>0]);assert_progress($plan['remaining_pages']===4&&$plan['daily_pages']===2,'inclusive daily workload');reject_progress(fn()=>$progress->saveKhatmah($u,['target_date'=>gmdate('Y-m-d'),'completed_pages'=>10,'status'=>'finished','expected_revision'=>1]),422,'cannot finish incomplete plan');
    $prefs=$progress->preferences($u,['language'=>'en','theme'=>'dark']);assert_progress($prefs['language']==='en','allowlisted preference persists');reject_progress(fn()=>$progress->preferences($u,['role'=>'owner']),422,'preferences cannot escalate privilege');
    $db->exec("INSERT INTO adhkar_items(title,formula,source_reference,source_url,repeat_count,is_reviewed,is_published) VALUES ('Dhikr','Formula','Reference','https://example.invalid',3,1,1)");
    $counter=$progress->saveAdhkar($u,['item_id'=>1,'current_count'=>2,'expected_revision'=>0]);assert_progress($counter['repeat_count']===3&&$counter['revision']===1,'counter target comes from reviewed source');
    reject_progress(fn()=>$progress->saveAdhkar($u,['item_id'=>1,'current_count'=>4,'expected_revision'=>1]),422,'counter cannot exceed repeat target');
    $completion=test_uuid();$progress->saveAdhkar($u,['item_id'=>1,'current_count'=>3,'expected_revision'=>1,'event_id'=>$completion]);
    reject_progress(fn()=>$progress->saveAdhkar($u,['item_id'=>1,'current_count'=>3,'expected_revision'=>1,'event_id'=>$completion]),409,'counter replay cannot overwrite a newer revision');
    $progress->saveAdhkar($u,['item_id'=>1,'current_count'=>3,'expected_revision'=>2,'event_id'=>$completion]);assert_progress((int)$db->query('SELECT adhkar_completions FROM account_daily_stats')->fetchColumn()===1,'completed counter retries counted once');
    assert_progress($progress->adhkar($other)===[],'counter list belongs to account');
    $db->exec('UPDATE adhkar_items SET repeat_count=NULL WHERE id=1');
    reject_progress(fn()=>$progress->saveAdhkar($u,['item_id'=>1,'current_count'=>1,'expected_revision'=>3]),422,'optional source count never invents a fixed target of one');
    assert_progress((int)$progress->adhkar($u,['item_id'=>1])[0]['current_count']===3,'source target removal preserves the owned counter');
    $db->exec('UPDATE adhkar_items SET repeat_count=3 WHERE id=1');
    $db->exec("UPDATE adhkar_progress SET updated_at='2020-01-01 00:00:00' WHERE user_id=1");
    for($i=1000;$i<=1500;$i++)$db->prepare("INSERT INTO adhkar_progress(user_id,item_key,current_count,repeat_count,revision,updated_at) VALUES(1,?,0,1,1,'2026-01-01 00:00:00')")->execute(['curated:'.$i]);
    assert_progress(count($progress->adhkar($u))===500,'legacy counter overview remains bounded');
    $targeted=$progress->adhkar($u,['item_id'=>'1']);
    assert_progress(count($targeted)===1&&(int)$targeted[0]['current_count']===0&&(int)$targeted[0]['revision']===3,'targeted counter lookup finds and projects an older row beyond overview window');
    assert_progress($progress->adhkar($other,['item_id'=>'1'])===[],'targeted counter lookup stays account scoped');
    reject_progress(fn()=>$progress->adhkar($u,['item_id'=>'1 OR 1=1']),422,'targeted lookup validates canonical positive ID');
    $progress->favorite($u,'lesson',1,true);$db->exec('UPDATE scholars SET is_published=0 WHERE id=1');
    assert_progress($progress->favorites($u)===[],'unpublished ancestor removes lesson and series from favorites');
    $db->exec('UPDATE lessons SET scholar_id=NULL WHERE id=1');reject_progress(fn()=>$catalog->detail('lessons',1),404,'series ancestor remains enforced without a direct lesson scholar');
    $db->exec('UPDATE scholars SET is_published=1 WHERE id=1');
    $db->exec("INSERT INTO lessons (scholar_id,series_id,title,youtube_id,source_url,is_published) VALUES (2,1,'Hidden direct scholar','cdefghijklm','https://youtu.be/cdefghijklm',1),(1,NULL,'Standalone','defghijklmn','https://youtu.be/defghijklmn',1)");
    assert_progress((int)$catalog->detail('series',1)['lesson_count']===1,'series count excludes unpublished direct scholars');
    assert_progress($catalog->list('lessons',['scholar_id'=>1])['pagination']['total']===2,'scholar filter includes inherited series scholar');
    $standalone=$catalog->list('lessons',['scholar_id'=>1,'standalone'=>'1']);
    assert_progress($standalone['pagination']['total']===1&&$standalone['items'][0]['title']==='Standalone','standalone scholar tab excludes series lessons');
    $db->exec("INSERT INTO adhkar_items(title,formula,source_reference,source_url,category,repeat_count,is_reviewed,is_published) VALUES ('Hidden category','Formula','Ref','https://example.invalid','Private',1,0,1),('Late category','Formula','Ref','https://example.invalid','Z-last',NULL,1,1)");
    $categories=$catalog->adhkarCategories();
    assert_progress(count($categories)===2&&!in_array('Private',array_column($categories,'category'),true),'category index includes all reviewed sections and excludes unreviewed content');
    $late=$catalog->list('adhkar',['category'=>'Z-last']);
    assert_progress($late['pagination']['total']===1&&$late['items'][0]['repeat_count']===null,'category query preserves unspecified repeat count');
    $allFirst=$catalog->list('adhkar',['q'=>'Formula','per_page'=>'1','page'=>'1']);
    $allSecond=$catalog->list('adhkar',['q'=>'Formula','per_page'=>'1','page'=>'2']);
    assert_progress($allFirst['pagination']['total']===2&&$allFirst['pagination']['has_more']&&!$allSecond['pagination']['has_more'],'all-category search pages include every reviewed match');
    $matched=array_merge($allFirst['items'],$allSecond['items']);
    assert_progress(count(array_filter($matched,static fn(array $item):bool=>$item['repeat_count']===null))===1&&!in_array('Hidden category',array_column($matched,'title'),true),'search includes NULL-repeat item and excludes unreviewed item');

    // Regression: source paging sliced the first 500 rows rather than the collection.
    $index=\Athar\Domain\QuranIndex::standard();
    for($global=1;$global<=520;$global++){
        $at=$index->position($global);
        $progress->addBookmark($u,['surah'=>$at['surah'],'ayah'=>$at['ayah'],'label'=>'saved '.$global]);
    }
    assert_progress(count($progress->bookmarks($u))===520,'legacy reader receives complete canonical marker set beyond 500');
    $seen=[];
    for($page=1;$page<=11;$page++){
        $result=$progress->bookmarks($u,['paged'=>'1','page'=>(string)$page,'per_page'=>'50']);
        assert_progress($result['pagination']['total']===520&&$result['pagination']['has_more']===($page<11),'complete bookmark count and exact has_more');
        $seen=array_merge($seen,array_column($result['items'],'id'));
    }
    assert_progress(count($seen)===520&&count(array_unique($seen))===520,'same-timestamp pages have stable unique tie ordering');
    assert_progress($progress->bookmarks($other,['paged'=>'1'])['pagination']['total']===0,'paged bookmark owner isolated');
    $tail=$progress->bookmarks($u,['paged'=>'1','page'=>'12','per_page'=>'50']);
    assert_progress($tail['items']===[]&&!$tail['pagination']['has_more'],'out of range page is explicit empty');
    foreach([['paged'=>[]],['paged'=>'yes'],['paged'=>'1','page'=>'0'],['paged'=>'1','page'=>'1.5'],['paged'=>'1','page'=>[]],['paged'=>'1','per_page'=>'51'],['paged'=>'1','page'=>'10001']] as $query)
        reject_progress(fn()=>$progress->bookmarks($u,$query),422,'malformed page query rejected without coercion');
    $progress->favorite($u,'lesson',1,true);
    $fav=$progress->favorites($u,['paged'=>'1','per_page'=>'1']);
    assert_progress($fav['pagination']['total']===2&&count($fav['items'])===1&&$fav['pagination']['has_more'],'favorite pagination and complete visibility filtered count');
    $db->exec('UPDATE lessons SET is_published=0 WHERE id=1');
    assert_progress($progress->favorites($u,['paged'=>'1'])['pagination']['total']===1,'hidden favorite excluded from count and rows');
    for($i=0;$i<510;$i++){
        $db->prepare("INSERT INTO books(title,author,source_url,rights_basis,is_published) VALUES(?, 'Author','https://example.invalid/source','Permission',1)")->execute(['Saved book '.$i]);
        $progress->favorite($u,'book',(int)$db->lastInsertId(),true);
    }
    $far=$progress->favorites($u,['paged'=>'1','page'=>'11','per_page'=>'50']);
    assert_progress($far['pagination']['total']===511&&count($far['items'])===11&&!$far['pagination']['has_more'],'favorites after first 500 remain reachable');
    $auth=new \Athar\Identity\AuthService($db,new \Athar\Identity\OtpService($db,str_repeat('a',32),static fn()=>false));
    $db->prepare('UPDATE users SET password_hash=? WHERE id=?')->execute([password_hash('CollectionFixture123!',PASSWORD_BCRYPT,['cost'=>4]),$u]);
    $login=$auth->login('one@example.invalid','CollectionFixture123!');
    $api=new \Athar\Http\ApiKernel($db,$auth,new \Athar\Identity\RateLimiter($db,str_repeat('r',32)));
    $headers=['Authorization'=>'Bearer '.$login['session']['access_token']];
    foreach(['/api/v1/quran/bookmarks','/api/v1/me/favorites'] as $path){
        $response=$api->handle(new \Athar\Http\Request('GET',$path,[],$headers,['paged'=>'1','page'=>'11','per_page'=>'50']));
        assert_progress($response->status===200&&$response->body['data']['pagination']['page']===11&&count($response->body['data']['items'])>0,'API forwards full collection paging query');
        assert_progress($api->handle(new \Athar\Http\Request('GET',$path,[],$headers,['paged'=>'1','page'=>[]]))->status===422,'API rejects array query instead of silently coercing');
    }
    $oldestBook=(int)$db->query('SELECT MIN(target_id) FROM user_favorites WHERE user_id=1 AND target_type=\'book\'')->fetchColumn();
    $bookPath='/api/v1/books/'.$oldestBook;
    assert_progress($progress->isFavorite($u,'book',$oldestBook)&&!$progress->isFavorite($other,'book',$oldestBook),'exact favorite status isolated beyond first 500');
    $detail=$api->handle(new \Athar\Http\Request('GET',$bookPath,[],$headers));
    assert_progress($detail->status===200&&$detail->body['data']['is_favorite']===true,'book detail carries owned boolean beyond truncated legacy list');
    $progress->favorite($u,'book',$oldestBook,false);
    $detail=$api->handle(new \Athar\Http\Request('GET',$bookPath,[],$headers));
    assert_progress($detail->status===200&&$detail->body['data']['is_favorite']===false,'book detail reflects confirmed favorite removal');
    $db->exec('DELETE FROM account_daily_stats WHERE user_id IN(1,2)');
    $empty=$progress->activitySummary($other);
    assert_progress($empty['owner_id']===$other&&$empty['recent']===[]&&$empty['recorded']['adhkar_completions']===0,'empty summary is a real owned DB snapshot');
    $today=gmdate('Y-m-d');$yesterday=gmdate('Y-m-d',time()-86400);$older=gmdate('Y-m-d',time()-2*86400);
    $progress->activity($u,test_uuid(),'lesson_completion',$yesterday);
    $progress->activity($u,test_uuid(),'adhkar_completion',$today);$progress->activity($u,test_uuid(),'adhkar_completion',$today);
    $progress->activity($u,test_uuid(),'book_download',$older);$progress->activity($u,test_uuid(),'quran_session',$older);
    $progress->activity($other,test_uuid(),'adhkar_completion',$today);
    $db->exec('UPDATE video_progress SET position_seconds=0,is_complete=0 WHERE user_id=1');
    assert_progress($progress->activitySummary($u)['recorded']['lessons_with_progress']===0,'opening a lesson without actual progress does not count as started');
    $db->exec('UPDATE video_progress SET position_seconds=99,is_complete=1 WHERE user_id=1');
    $db->prepare('INSERT INTO account_daily_stats(user_id,activity_date,adhkar_completions) VALUES(1,?,99)')->execute([gmdate('Y-m-d',time()+86400)]);
    $summary=$progress->activitySummary($u);
    assert_progress($summary['generated_date']===$today&&$summary['recorded']['adhkar_completions']===2&&$summary['recorded']['lesson_completions']===1,'summary excludes other owner and future rows');
    assert_progress($summary['recorded']['active_days']===2&&count($summary['recent'])===2&&$summary['recent'][0]['date']===$yesterday,'legacy-only days cannot inflate genuine completion days');
    assert_progress($summary['recorded']['lessons_with_progress']===1&&$summary['recorded']['completed_lessons']===1&&!isset($summary['recorded']['watched_seconds']),'saved resume position is never labeled watched elapsed time');
    assert_progress($summary['availability']===['reading_sessions'=>false,'listening_history'=>false,'adhkar_completions'=>true,'lesson_progress'=>true,'book_downloads'=>true],'unimplemented tracking unavailable rather than fabricated zeros');
    assert_progress($summary['recorded']['legacy_book_download_events']===1&&$summary['recorded']['legacy_quran_sessions']===1,'historical unknown-provenance events preserved separately');
    $before=$db->query('SELECT SUM(book_downloads) FROM account_daily_stats WHERE user_id=1')->fetchColumn();
    for($attempt=0;$attempt<2;$attempt++){
        $download=$api->handle(new \Athar\Http\Request('GET','/api/v1/books/1/download',[],array_merge($headers,['Idempotency-Key'=>test_uuid()])));
        assert_progress($download->status===200,'public rights URL resolves without claiming a completed transfer');
    }
    assert_progress($db->query('SELECT SUM(book_downloads) FROM account_daily_stats WHERE user_id=1')->fetchColumn()===$before,'URL lookups with idempotency headers cannot count downloads');
    $response=$api->handle(new \Athar\Http\Request('GET','/api/v1/me/activity-summary',[],$headers));
    assert_progress($response->status===200&&$response->body['data']['owner_id']===1&&$response->body['data']['recorded']['adhkar_completions']===2,'activity API derives exact verified owner');
    assert_progress($api->handle(new \Athar\Http\Request('POST','/api/v1/me/activity-summary',[],$headers))->status===405,'summary read-only method');
    assert_progress($api->handle(new \Athar\Http\Request('GET','/api/v1/me/activity-summary'))->status===401,'anonymous summary denied');
    $completedId=test_uuid();$progress->activity($u,$completedId,'book_download_completed',$older);$progress->activity($u,$completedId,'book_download_completed',$older);
    $summary=$progress->activitySummary($u);
    assert_progress($summary['schema_version']===2&&$summary['availability']['book_downloads']===true&&$summary['recorded']['completed_book_downloads']===1,'new completion kind counts once in separate proven-flow metric');
    assert_progress($summary['recorded']['legacy_book_download_events']===1&&$summary['recorded']['active_days']===3&&$summary['recent'][0]['completed_book_downloads']===1,'completed downloads include original UTC day without rewriting mixed legacy counters');
    reject_progress(fn()=>$progress->activity($u,$completedId,'book_download_completed',$today),409,'completion retry never rewrites original event date');
    reject_progress(fn()=>$progress->activity($u,$completedId,'book_download',$older),409,'legacy kind cannot reuse completed event id');
    $progress->activity($other,$completedId,'book_download_completed',$today);
    assert_progress($progress->activitySummary($other)['recorded']['completed_book_downloads']===1&&$progress->activitySummary($u)['recorded']['completed_book_downloads']===1,'same event UUID remains owned independently');
    $db->beginTransaction();$progress->activitySummary($u);assert_progress($db->inTransaction(),'summary preserves caller transaction');$db->rollBack();
    $db->beginTransaction();$progress->bookmarks($u,['paged'=>'1']);assert_progress($db->inTransaction(),'caller transaction preserved by collection read');$db->rollBack();

});echo 'PASS: '.$checks." catalog/publication/ownership/progress/retry/comment/plan assertions.\n";
