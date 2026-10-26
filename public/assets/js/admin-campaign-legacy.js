// Preserve the existing campaign batch adapter until WP20 replaces it.
(() => {
    const forms = [...document.querySelectorAll('[data-campaign-batch]')];
    if (!forms.length) return;
    const feedback = document.querySelector('[data-campaign-feedback]');
    const stopButton = document.querySelector('[data-campaign-stop]');
    let stopRequested = false;
    let active = false;
    stopButton?.addEventListener('click', () => {
        stopRequested = true;
        stopButton.textContent = 'سيتم الإيقاف بعد الدفعة الحالية';
        stopButton.disabled = true;
    });
    forms.forEach((form) => form.addEventListener('submit', async (event) => {
        event.preventDefault();
        if (active) return;
        active = true;
        stopRequested = false;
        const button = form.querySelector('button[type="submit"]');
        const originalLabel = button?.textContent || 'بدء الإرسال';
        if (button) { button.disabled = true; button.textContent = 'جارٍ إرسال الحملة…'; }
        if (stopButton) { stopButton.hidden = false; stopButton.disabled = false; stopButton.textContent = 'إيقاف بعد الدفعة الحالية'; }
        let totals = { sent: Number(form.closest('.admin-campaign-row')?.querySelector('.admin-campaign-stats span')?.textContent.match(/\d+/)?.[0] || 0), pending: 0 };
        try {
            while (!stopRequested) {
                const payload = new FormData(form);
                payload.append('ajax_campaign_batch', '1');
                const response = await fetch('/admin', { method: 'POST', body: payload, credentials: 'same-origin', headers: { Accept: 'application/json' } });
                const result = await response.json();
                if (!response.ok || !result.ok) throw new Error(result.error || 'تعذر إرسال دفعة البريد.');
                totals = { sent: Number(result.stats?.sent || 0), pending: Number(result.stats?.pending || 0) };
                if (feedback) feedback.textContent = `تقدم الحملة: أُرسلت ${totals.sent} رسالة، وتبقى ${totals.pending}.`;
                if (result.paused || totals.pending === 0) {
                    if (result.paused && feedback) feedback.textContent += ' توقفت الحملة مؤقتًا عند حد Gmail اليومي.';
                    window.location.reload();
                    return;
                }
            }
            if (feedback) feedback.textContent = `توقف الإرسال بعد الدفعة الحالية. أُرسلت ${totals.sent} رسالة، وتبقى ${totals.pending}.`;
            window.location.reload();
        } catch (error) {
            if (feedback) feedback.textContent = error instanceof Error ? error.message : 'تعذر إكمال الإرسال.';
            if (button) { button.disabled = false; button.textContent = originalLabel; }
            if (stopButton) stopButton.hidden = true;
            active = false;
        }
    }));
})();
