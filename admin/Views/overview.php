<?php
$overviewText = static fn(string $ar, string $en): string => e($adminLocale === 'en' ? $en : $ar);
$overviewMetrics = [
    ['users','الحسابات','Accounts'], ['scholars','العلماء المنشورون','Published scholars'],
    ['series','السلاسل المنشورة','Published series'], ['lessons','الدروس المنشورة','Published lessons'],
    ['books','الكتب المنشورة','Published books'], ['pending_comments','تعليقات تنتظر المراجعة','Comments awaiting review'],
];
?>
<section class="admin-inventory admin-panel" id="admin-overview" data-admin-panel>
    <div class="section-heading"><div><span class="eyebrow"><?= $overviewText('مؤشرات المنصة','Platform indicators') ?></span><h2><?= $overviewText('نظرة عامة وتحليلات','Overview and analytics') ?></h2></div></div>
    <div class="inventory-grid admin-metric-grid">
        <?php foreach ($overviewMetrics as [$key,$ar,$en]): ?>
            <article><h3><?= $overviewText($ar,$en) ?><span><?= number_format((int) $dashboardMetrics[$key]) ?></span></h3></article>
        <?php endforeach; ?>
    </div>
    <div class="admin-analytics">
        <h3><?= $overviewText('إشارات تشغيلية آمنة','Privacy-bounded operational indicators') ?></h3>
        <div class="admin-analytics-grid">
            <article><span><?= $overviewText('مستخدمون نشطون خلال 30 يومًا','Active users in the last 30 days') ?></span><strong><?= (int) $dashboardMetrics['active_users_30d'] < 5 ? '&lt;5' : number_format((int) $dashboardMetrics['active_users_30d']) ?></strong></article>
            <article><span><?= $overviewText('سجل العمليات','Operational audit entries') ?></span><strong><?= number_format($auditTotalCount) ?></strong></article>
        </div>
        <p class="source-note"><?= $overviewText('لا تعرض لوحة الإدارة إحصاءات عبادة فردية أو مجمعة مثل جلسات القرآن والأذكار والختمة.','Administration does not expose individual or aggregate worship statistics, Quran positions, Adhkar or Khatmah activity.') ?></p>
    </div>
</section>
