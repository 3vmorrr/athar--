<?php if ($contentType === 'adhkar') { $adhkarDraft=$row; $adhkarFormMode='edit'; require dirname(__DIR__,3).'/admin/Views/adhkar-form.php'; return; } ?>
<?php if ($contentType === 'book') { $bookDraft=$row; $bookFormMode='edit'; require dirname(__DIR__,3).'/admin/Views/book-form.php'; return; } ?>
<?php if ($contentType === 'lesson') { $lessonDraft=$row; $lessonFormMode='edit'; require dirname(__DIR__,3).'/admin/Views/lesson-form.php'; return; } ?>
<?php if ($contentType === 'series') { $seriesDraft=$row; $seriesFormMode='edit'; require dirname(__DIR__,3).'/admin/Views/series-form.php'; return; } ?>
<?php if ($contentType === 'scholar') { $scholarDraft=$row; $scholarFormMode='edit'; require dirname(__DIR__,3).'/admin/Views/scholar-form.php'; return; } ?>
