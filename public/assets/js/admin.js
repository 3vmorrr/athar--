(() => {
    const body = document.body;
    const toggle = document.querySelector('[data-admin-drawer-toggle]');
    const scrim = document.querySelector('[data-admin-drawer-scrim]');
    const nav = document.querySelector('[data-admin-nav]');
    const close = () => {
        body.classList.remove('admin-drawer-open');
        toggle?.setAttribute('aria-expanded', 'false');
        if (scrim) scrim.hidden = true;
    };
    toggle?.addEventListener('click', () => {
        const opening = !body.classList.contains('admin-drawer-open');
        body.classList.toggle('admin-drawer-open', opening);
        toggle.setAttribute('aria-expanded', String(opening));
        if (scrim) scrim.hidden = !opening;
    });
    scrim?.addEventListener('click', close);
    nav?.addEventListener('click', (event) => {
        if (event.target.closest('a[href^="#admin-"]')) close();
    });
    window.addEventListener('resize', () => {
        if (window.innerWidth > 980) close();
    });

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
        if (scroll && panel) panel.scrollIntoView({
            block: 'start',
            behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
        });
    };
    show(window.location.hash, false);
    nav.addEventListener('click', (event) => {
        const link = event.target.closest('a[href^="#admin-"]');
        if (link) show(link.hash, true);
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

    document.querySelectorAll('form[data-confirm]').forEach((form) => {
        form.addEventListener('submit', (event) => {
            if (!window.confirm(form.dataset.confirm || 'Confirm?')) event.preventDefault();
        });
    });
})();
