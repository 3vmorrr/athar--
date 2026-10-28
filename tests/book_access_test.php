<?php
declare(strict_types=1);
require dirname(__DIR__).'/backend/bootstrap.php';
use Athar\Catalog\BookAccess;
$checks=0;
function book_check(bool $value,string $name):void{global $checks;$checks++;if(!$value)throw new RuntimeException('FAIL: '.$name);}
$allowed=['can_download'=>1,'source_url'=>'https://example.invalid/source','download_url'=>'https://cdn.example.invalid/book.pdf','rights_basis'=>'Recorded permission'];
book_check(BookAccess::downloadable($allowed),'central rights policy retains an approved public direct link');
foreach(['http://example.invalid/book.pdf','https://user:pass@example.invalid/book.pdf','https://example.invalid:8443/book.pdf','https://localhost/book.pdf','https://localhost./book.pdf','https://device.local/book.pdf','https://127.0.0.1/book.pdf','https://127.1/book.pdf','https://2130706433/book.pdf','https://[::1]/book.pdf','https://[::ffff:127.0.0.1]/book.pdf','https://100.64.0.1/book.pdf','https://198.18.0.1/book.pdf','https://192.0.2.1/book.pdf','https://[2001:db8::1]/book.pdf','https://224.0.0.1/book.pdf','https://[ff02::1]/book.pdf','https://169.254.0.1/book.pdf','https://[fc00::1]/book.pdf'] as $url){
    book_check(!BookAccess::downloadable(array_replace($allowed,['download_url'=>$url])),'unsafe direct destination rejected');
    book_check(!BookAccess::downloadable(array_replace($allowed,['source_url'=>$url])),'unsafe source does not authorize download');
}
book_check(!BookAccess::downloadable(array_replace($allowed,['rights_basis'=>'   '])),'URL never grants missing rights');
book_check(!BookAccess::downloadable(array_replace($allowed,['can_download'=>0])),'rights flag remains authoritative');
book_check(BookAccess::https('https://example.invalid:443/book.pdf'),'standard explicit TLS port retained');
require dirname(__DIR__).'/admin/books.php';
$input=['title'=>'Reviewed book','author'=>'Author','source_url'=>$allowed['source_url'],'download_url'=>$allowed['download_url'],'rights_basis'=>$allowed['rights_basis']];
book_check(admin_book_fields($input)['download_url']===$allowed['download_url'],'Admin retains an approved direct-link metadata journey');
foreach(['https://127.0.0.1/book.pdf','https://example.invalid:8443/book.pdf','https://user:pass@example.invalid/book.pdf'] as $url){
    try{admin_book_fields(array_replace($input,['download_url'=>$url]));book_check(false,'Admin rejects a destination the native client cannot use');}
    catch(RuntimeException $error){book_check(str_contains($error->getMessage(),'HTTPS'),'Admin rejects unsafe destination with actionable validation');}
}
echo 'PASS: '.$checks." centralized book-rights/public-destination assertions.\n";
