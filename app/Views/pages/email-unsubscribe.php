<?php declare(strict_types=1); ?>
<!doctype html><html lang="<?= e($unsubscribeLocale) ?>" dir="<?= $unsubscribeLocale==='ar'?'rtl':'ltr' ?>"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title><?= e($unsubscribeLocale==='en'?'Athar email preferences':'رسائل أثر') ?></title><link rel="stylesheet" href="/assets/css/admin.css"></head><body class="admin-app" data-theme="light"><main class="admin-main"><article class="knowledge-card">
<img src="/assets/images/athar-logo-cropped.png" alt="Athar" width="94" height="88">
<h1><?= e($unsubscribeDone?($unsubscribeLocale==='en'?'You are unsubscribed':'تم إلغاء الاشتراك'):($unsubscribeLocale==='en'?'Manage Athar updates':'إدارة رسائل أثر')) ?></h1>
<p><?= e($unsubscribeLocale==='en'?'Unsubscribing stops news and campaign emails. Essential account messages remain available.':'إلغاء الاشتراك يوقف الأخبار والحملات. رسائل الحساب الضرورية تظل متاحة.') ?></p>
<?php if(!$unsubscribeDone): ?><form class="form-stack" method="post"><?= csrf_field() ?><?php foreach(['u'=>$userId,'t'=>$token,'e'=>$expires,'v'=>$version] as $field=>$value): ?><input type="hidden" name="<?= e($field) ?>" value="<?= e((string)$value) ?>"><?php endforeach; ?><button class="button button-primary"><?= e($unsubscribeLocale==='en'?'Unsubscribe from updates':'إلغاء رسائل التحديثات') ?></button></form><?php endif; ?>
<a class="button button-secondary" href="/" rel="noreferrer"><?= e($unsubscribeLocale==='en'?'Return to Athar':'العودة إلى أثر') ?></a>
</article></main></body></html>
