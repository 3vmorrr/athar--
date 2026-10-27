(() => {
    const body = document.body;
    const toggle = document.querySelector('[data-admin-drawer-toggle]');
    const scrim = document.querySelector('[data-admin-drawer-scrim]');
    const nav = document.querySelector('[data-admin-nav]');
    const sidebar = document.querySelector('#admin-primary-nav');
    const mobile = window.matchMedia('(max-width: 980px)');
    const background = [...document.querySelectorAll('.admin-topbar,.knowledge-heading,.admin-content,.admin-global-flash,.admin-skip-link')];
    body.classList.add('admin-js');
    const closeButton = document.createElement('button');
    closeButton.type = 'button';
    closeButton.className = 'admin-drawer-close';
    closeButton.textContent = body.dataset.closeMenu;
    sidebar?.prepend(closeButton);
    const close = (restoreFocus = false) => {
        body.classList.remove('admin-drawer-open');
        toggle?.setAttribute('aria-expanded', 'false');
        if (scrim) scrim.hidden = true;
        background.forEach((element) => { element.inert = false; });
        if (sidebar) {
            sidebar.inert = mobile.matches;
            sidebar.removeAttribute('role');
            sidebar.removeAttribute('aria-modal');
            sidebar.removeAttribute('aria-label');
        }
        if (restoreFocus) toggle?.focus();
    };
    toggle?.addEventListener('click', () => {
        const opening = !body.classList.contains('admin-drawer-open');
        if (!opening) { close(true); return; }
        body.classList.toggle('admin-drawer-open', opening);
        toggle.setAttribute('aria-expanded', String(opening));
        if (scrim) scrim.hidden = !opening;
        if (sidebar) {
            sidebar.inert = false;
            sidebar.setAttribute('role', 'dialog');
            sidebar.setAttribute('aria-modal', 'true');
            sidebar.setAttribute('aria-label', nav?.getAttribute('aria-label') || 'Administration');
        }
        background.forEach((element) => { element.inert = true; });
        closeButton.focus();
    });
    closeButton.addEventListener('click', () => close(true));
    scrim?.addEventListener('click', () => close(true));
    nav?.addEventListener('click', (event) => {
        if (event.target.closest('a[href^="#admin-"]')) close(false);
    });
    document.addEventListener('keydown', (event) => {
        if (!body.classList.contains('admin-drawer-open')) return;
        if (event.key === 'Escape') { event.preventDefault(); close(true); }
        if (event.key !== 'Tab') return;
        const items = [closeButton, ...nav.querySelectorAll('a[href]')];
        const first = items[0], last = items[items.length - 1];
        if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
        else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    });
    mobile.addEventListener('change', () => {
        const wasOpen = body.classList.contains('admin-drawer-open');
        close(false);
        if (wasOpen) {
            if (mobile.matches) toggle?.focus();
            else nav?.querySelector('[aria-current]')?.focus();
        }
    });
    close(false);

    if (!nav) return;
    const links = [...nav.querySelectorAll('a[href^="#admin-"]')];
    const panels = [...document.querySelectorAll('[data-admin-panel]')];
    const show = (hash, scroll) => {
        const active = links.find((link) => link.hash === hash) || links[0];
        const panel = panels.find((item) => '#' + item.id === active?.hash)
            || panels.find((item) => item.id === 'admin-overview')
            || panels[0];
        links.forEach((link) => {
            if (link === active) link.setAttribute('aria-current', 'location');
            else link.removeAttribute('aria-current');
        });
        panels.forEach((item) => { item.hidden = item !== panel; });
        document.querySelectorAll('[data-admin-preference]').forEach((link) => {
            link.hash = panel ? '#' + panel.id : '';
        });
        if (scroll && panel) {
            panel.tabIndex = -1;
            panel.focus({preventScroll: true});
            panel.scrollIntoView({
            block: 'start',
            behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
            });
        }
    };
    show(window.location.hash, false);
    const feedback = document.querySelector('[data-admin-form-error],.admin-global-flash');
    if (feedback) {
        feedback.tabIndex = -1;
        // Initial fragment restoration can reset focus after deferred scripts.
        // Wait for page load and the next frame so feedback stays authoritative.
        const focusFeedback = () => window.requestAnimationFrame(() => {
            feedback.focus({preventScroll: true});
            feedback.scrollIntoView({block: 'center', behavior: 'auto'});
        });
        if (document.readyState === 'complete') focusFeedback();
        else window.addEventListener('load', focusFeedback, {once: true});
    }
    nav.addEventListener('click', (event) => {
        const link = event.target.closest('a[href^="#admin-"]');
        if (link) {
            event.preventDefault();
            if (window.location.hash !== link.hash) window.location.hash = link.hash;
            else show(link.hash, true);
        }
    });
    window.addEventListener('hashchange', () => show(window.location.hash, true));

    const filter = (inputSelector, rowSelector, emptySelector) => {
        const input = document.querySelector(inputSelector);
        if (!input) return;
        const rows = [...document.querySelectorAll(rowSelector)];
        const empty = document.querySelector(emptySelector);
        input.addEventListener('input', () => {
            const query = input.value.trim().toLocaleLowerCase();
            let visible = 0;
            rows.forEach((row) => {
                const matched = !query || (row.dataset.search || '').toLocaleLowerCase().includes(query);
                row.hidden = !matched;
                if (matched) visible++;
            });
            if (empty) empty.hidden = visible !== 0;
        });
    };
    filter('[data-admin-user-search]', '[data-admin-user]', '[data-admin-user-empty]');
    filter('[data-admin-audit-search]', '[data-admin-audit]', '[data-admin-audit-empty]');

    document.querySelectorAll('[data-event-form]').forEach((form) => {
        const state = form.querySelector('[data-event-state]');
        const confirm = form.querySelector('[data-event-confirm]');
        const sync = () => {
            confirm.required = state.value === 'live';
            confirm.disabled = state.value !== 'live';
            if (confirm.disabled) confirm.checked = false;
        };
        state.addEventListener('change', sync);
        sync();
    });

    document.querySelectorAll('form[data-confirm]').forEach((form) => {
        form.addEventListener('submit', (event) => {
            if (!window.confirm(form.dataset.confirm || 'Confirm?')) event.preventDefault();
        });
    });
})();
