(() => {
    const root = document.documentElement;
    const themeButton = document.querySelector('[data-theme-toggle]');
    const themeLabel = document.querySelector('[data-theme-label]');
    const menuButton = document.querySelector('[data-menu-toggle]');
    const nav = document.querySelector('#primary-nav');
    const moonIcon = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20.2 15.2A8.5 8.5 0 0 1 8.8 3.8 8.5 8.5 0 1 0 20.2 15.2Z"/></svg>';
    const sunIcon = '<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M4.93 4.93l1.42 1.42m11.3 11.3 1.42 1.42M2 12h2m16 0h2M4.93 19.07l1.42-1.42m11.3-11.3 1.42-1.42"/></svg>';

    const readSavedTheme = () => {
        const serverTheme = root.dataset.savedTheme;
        if (serverTheme === 'light' || serverTheme === 'dark') return serverTheme;
        try {
            const saved = window.localStorage.getItem('athar-theme');
            if (saved === 'light' || saved === 'dark') return saved;
        } catch (_) {
            // Storage may be disabled; the default remains light.
        }
        return 'light';
    };

    const setTheme = (theme) => {
        root.dataset.theme = theme;
        const nextThemeLabel = theme === 'dark' ? 'الوضع الفاتح' : 'الوضع الداكن';
        const englishThemeLabel = theme === 'dark' ? 'Light mode' : 'Dark mode';
        if (themeButton) {
            themeButton.setAttribute('aria-pressed', String(theme === 'dark'));
            themeButton.setAttribute('aria-label', root.lang === 'en' ? englishThemeLabel : nextThemeLabel);
            themeButton.title = root.lang === 'en' ? englishThemeLabel : nextThemeLabel;
            themeButton.innerHTML = theme === 'dark' ? sunIcon : moonIcon;
        }
        if (themeLabel) themeLabel.textContent = root.lang === 'en' ? englishThemeLabel : nextThemeLabel;
        const themeColor = document.querySelector('meta[name="theme-color"]');
        if (themeColor) themeColor.setAttribute('content', theme === 'dark' ? '#101815' : '#F4F3ED');
    };

    setTheme(readSavedTheme());

    const eyeIcon = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M2.5 12s3.4-6 9.5-6 9.5 6 9.5 6-3.4 6-9.5 6-9.5-6-9.5-6Z"/><circle cx="12" cy="12" r="2.7"/></svg>';
    const eyeOffIcon = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m3 3 18 18M10.6 6.2A10.6 10.6 0 0 1 12 6c6.1 0 9.5 6 9.5 6a15 15 0 0 1-3.1 3.7M6.2 6.3C3.8 7.8 2.5 12 2.5 12s3.4 6 9.5 6a10 10 0 0 0 3-.5"/><path d="M9.9 9.9a3 3 0 0 0 4.2 4.2"/></svg>';
    document.querySelectorAll('input[type="password"]').forEach((input) => {
        if (!input.id) input.id = `password-${Math.random().toString(36).slice(2, 9)}`;
        const oldLabel = input.closest('label');
        const field = document.createElement('div'); field.className = 'password-field';
        const control = document.createElement('div'); control.className = 'password-control';
        const toggle = document.createElement('button'); toggle.type = 'button'; toggle.className = 'password-toggle';
        toggle.setAttribute('aria-controls', input.id); toggle.setAttribute('aria-pressed', 'false');
        const showLabel = root.lang === 'en' ? 'Show password' : 'إظهار كلمة المرور';
        toggle.setAttribute('aria-label', showLabel); toggle.title = showLabel; toggle.innerHTML = eyeIcon;
        toggle.addEventListener('click', () => {
            const show = input.type === 'password'; input.type = show ? 'text' : 'password';
            const label = show ? (root.lang === 'en' ? 'Hide password' : 'إخفاء كلمة المرور') : showLabel;
            toggle.setAttribute('aria-pressed', String(show)); toggle.setAttribute('aria-label', label); toggle.title = label;
            toggle.innerHTML = show ? eyeOffIcon : eyeIcon; input.focus({ preventScroll: true });
        });
        if (oldLabel) {
            const label = oldLabel.cloneNode(false); label.htmlFor = input.id;
            Array.from(oldLabel.childNodes).forEach((node) => { if (node !== input) label.append(node); });
            oldLabel.replaceWith(field); field.append(label, control);
        } else {
            input.parentNode.insertBefore(field, input); field.append(control);
        }
        control.append(input, toggle);

        if (input.hasAttribute('data-password-strength-check')) {
            const strength = document.createElement('div'); strength.className = 'password-strength'; strength.setAttribute('aria-live', 'polite');
            const meter = document.createElement('div'); meter.className = 'password-strength-meter'; meter.setAttribute('role', 'meter'); meter.setAttribute('aria-label', root.lang === 'en' ? 'Password strength' : 'قوة كلمة المرور'); meter.setAttribute('aria-valuemin', '0'); meter.setAttribute('aria-valuemax', '5'); meter.setAttribute('aria-valuenow', '0');
            const result = document.createElement('span'); result.dataset.passwordStrength; result.textContent = root.lang === 'en' ? 'Use at least 10 characters.' : 'استخدم 10 أحرف على الأقل.';
            strength.append(meter, result); field.append(strength);
            input.addEventListener('input', () => {
                const value = input.value;
                const score = value ? [value.length >= 10, value.length >= 14, /[a-z]/.test(value) && /[A-Z]/.test(value), /\d/.test(value), /[^A-Za-z0-9]/.test(value)].filter(Boolean).length : 0;
                const level = score >= 4 ? (root.lang === 'en' ? 'Strong password' : 'كلمة مرور قوية') : score >= 2 ? (root.lang === 'en' ? 'Fair password' : 'كلمة مرور متوسطة') : value ? (root.lang === 'en' ? 'Weak password' : 'كلمة مرور ضعيفة') : (root.lang === 'en' ? 'Use at least 10 characters.' : 'استخدم 10 أحرف على الأقل.');
                meter.dataset.level = score >= 4 ? 'strong' : score >= 2 ? 'fair' : value ? 'weak' : 'empty';
                meter.setAttribute('aria-valuenow', String(score)); result.textContent = level;
            });
        }
    });

    document.addEventListener('submit', (event) => {
        const form = event.target;
        if (form instanceof HTMLFormElement && form.dataset.confirm && !window.confirm(form.dataset.confirm)) event.preventDefault();
    });

    const mergeCheckbox = document.querySelector('[data-merge-guest-progress]');
    const guestProgressInput = document.querySelector('[data-guest-progress-json]');
    const guestProgressSummary = document.querySelector('[data-guest-progress-summary]');
    try {
        const rawGuestProgress = window.localStorage.getItem('athar-guest-quran');
        const guestProgress = rawGuestProgress ? JSON.parse(rawGuestProgress) : null;
        const validGuestProgress = guestProgress && typeof guestProgress === 'object' ? guestProgress : null;
        const guestBookmarkCount = Array.isArray(validGuestProgress?.bookmarks) ? validGuestProgress.bookmarks.length : 0;
        if (guestProgressSummary) guestProgressSummary.textContent = guestBookmarkCount || validGuestProgress?.position ? `العناصر المتاحة: موضع قراءة ${validGuestProgress?.position ? 'واحد' : 'لا يوجد'}، وعلامات ${guestBookmarkCount}.` : 'لا توجد بيانات قرآن محفوظة محليًا.';
        document.querySelector('[data-register-form]')?.addEventListener('submit', (event) => {
            if (!mergeCheckbox?.checked) {
                if (guestProgressInput) guestProgressInput.value = '';
                return;
            }
            if (!validGuestProgress || !guestProgressInput) {
                event.preventDefault();
                if (guestProgressSummary) guestProgressSummary.textContent = 'تعذر قراءة مساحة التخزين؛ أزل اختيار الدمج لإكمال إنشاء الحساب.';
                return;
            }
            guestProgressInput.value = JSON.stringify(validGuestProgress);
        });
    } catch (_) {
        if (guestProgressSummary) guestProgressSummary.textContent = 'مساحة التخزين غير متاحة؛ أزل اختيار الدمج لإكمال إنشاء الحساب.';
    }

    if (document.querySelector('[data-clear-guest-progress]')) {
        try { window.localStorage.removeItem('athar-guest-quran'); } catch (_) { /* Keep the account flow working when storage is unavailable. */ }
    }

    themeButton?.addEventListener('click', () => {
        const next = root.dataset.theme === 'dark' ? 'light' : 'dark';
        setTheme(next);
        try {
            window.localStorage.setItem('athar-theme', next);
        } catch (_) {
            // Theme selection remains available for this page view.
        }
    });

    menuButton?.addEventListener('click', () => {
        const isOpen = menuButton.getAttribute('aria-expanded') === 'true';
        menuButton.setAttribute('aria-expanded', String(!isOpen));
        menuButton.setAttribute('aria-label', isOpen ? 'فتح القائمة' : 'إغلاق القائمة');
        nav?.setAttribute('data-open', String(!isOpen));
    });

    nav?.addEventListener('click', (event) => {
        if (event.target instanceof HTMLAnchorElement && window.matchMedia('(max-width: 820px)').matches) {
            menuButton?.setAttribute('aria-expanded', 'false');
            menuButton?.setAttribute('aria-label', 'فتح القائمة');
            nav.setAttribute('data-open', 'false');
        }
    });

    const quranReader = document.querySelector('[data-quran-reader]');
    if (quranReader?.dataset.tool === 'search') {
        const form = quranReader.querySelector('[data-quran-search-form]');
        const input = form?.querySelector('[name="q"]');
        const submit = form?.querySelector('button[type="submit"]');
        const status = quranReader.querySelector('[data-quran-search-status]');
        const results = quranReader.querySelector('[data-quran-search-results]');
        let matches = [];
        let visibleCount = 0;
        const batchSize = 24;

        const setSearchStatus = (message, state = 'ready') => {
            if (!status) return;
            status.textContent = message;
            status.dataset.state = state;
            status.hidden = !message;
        };

        const renderSearchResults = (append = false) => {
            if (!results) return;
            results.querySelector('.quran-search-more')?.remove();
            if (!append) results.replaceChildren();
            const fragment = document.createDocumentFragment();
            const end = Math.min(visibleCount + batchSize, matches.length);
            for (let index = visibleCount; index < end; index += 1) {
                const match = matches[index];
                if (!Number.isInteger(Number(match?.surah?.number)) || !Number.isInteger(Number(match?.numberInSurah)) || typeof match?.text !== 'string') continue;
                const card = document.createElement('article');
                card.className = 'knowledge-card quran-search-result';
                const text = document.createElement('p');
                text.className = 'quran-search-ayah';
                text.lang = 'ar';
                text.dir = 'rtl';
                text.textContent = match.text;
                const footer = document.createElement('div');
                footer.className = 'quran-search-result-footer';
                const reference = document.createElement('span');
                reference.className = 'source-chip';
                reference.textContent = `سورة ${match.surah.name} · الآية ${match.numberInSurah}`;
                const open = document.createElement('a');
                open.className = 'button button-secondary';
                open.href = `/quran?tool=ayah&surah=${Number(match.surah.number)}&ayah=${Number(match.numberInSurah)}`;
                open.textContent = 'افتح الآية';
                footer.append(reference, open);
                card.append(text, footer);
                fragment.append(card);
            }
            visibleCount = end;
            results.append(fragment);
            if (visibleCount < matches.length) {
                const more = document.createElement('button');
                more.type = 'button';
                more.className = 'button button-secondary quran-search-more';
                more.textContent = `عرض ${Math.min(batchSize, matches.length - visibleCount)} نتيجة أخرى`;
                more.addEventListener('click', () => renderSearchResults(true), { once: true });
                results.append(more);
            }
        };

        const searchQuran = async (query) => {
            const normalized = query.trim();
            if (normalized.length < 2) {
                setSearchStatus('اكتب كلمتين على الأقل للبحث في الآيات.', 'error');
                input?.focus();
                return;
            }
            if (!input || !submit || !results) return;
            submit.disabled = true;
            submit.setAttribute('aria-busy', 'true');
            setSearchStatus('جارٍ البحث في نص القرآن الكريم…', 'loading');
            results.replaceChildren();
            try {
                const endpoint = `https://api.alquran.cloud/v1/search/${encodeURIComponent(normalized)}/all/quran-uthmani`;
                const response = await fetch(endpoint, { headers: { Accept: 'application/json' }, signal: AbortSignal.timeout(15000) });
                if (!response.ok) throw new Error('search-unavailable');
                const payload = await response.json();
                if (payload.code !== 200 || !Array.isArray(payload.data?.matches)) throw new Error('search-response');
                matches = payload.data.matches;
                visibleCount = 0;
                if (!matches.length) {
                    setSearchStatus('لم نعثر على آيات مطابقة. جرّب كلمة أخرى أو اكتبها من دون تشكيل.', 'empty');
                    return;
                }
                renderSearchResults();
                setSearchStatus(`عُثر على ${Number(payload.data.count) || matches.length} موضعًا. تظهر النتائج على دفعات لتسهيل التصفح.`, 'success');
            } catch (_) {
                setSearchStatus('تعذر البحث الآن. تحقق من الاتصال بالإنترنت ثم حاول مرة أخرى.', 'error');
            } finally {
                submit.disabled = false;
                submit.removeAttribute('aria-busy');
            }
        };

        form?.addEventListener('submit', (event) => {
            event.preventDefault();
            const query = input?.value || '';
            const url = new URL(window.location.href);
            url.searchParams.set('q', query.trim());
            window.history.replaceState({}, '', url);
            searchQuran(query);
        });
        if (input?.value.trim()) searchQuran(input.value);
    }
    const radioPage = document.querySelector('[data-quran-radio]');
    if (radioPage) {
        const search = radioPage.querySelector('[data-radio-search]');
        const list = radioPage.querySelector('[data-radio-list]');
        const player = radioPage.querySelector('[data-radio-audio]');
        const current = radioPage.querySelector('[data-radio-current]');
        const status = radioPage.querySelector('[data-radio-status]');
        const cacheKey = 'athar-mp3quran-radios-ar-v1';
        let stations = [];
        const renderStations = () => {
            const query = (search.value || '').trim().toLocaleLowerCase('ar');
            const filtered = stations.filter((station) => station.name.toLocaleLowerCase('ar').includes(query));
            list.replaceChildren();
            if (!filtered.length) {
                const empty = document.createElement('p'); empty.className = 'radio-empty';
                empty.textContent = stations.length ? 'لا توجد محطة بهذا الاسم.' : 'تعذر تحميل قائمة الإذاعات. تحقق من الاتصال ثم أعد تحميل الصفحة.';
                list.append(empty); return;
            }
            const fragment = document.createDocumentFragment();
            filtered.forEach((station) => {
                const button = document.createElement('button'); button.type = 'button'; button.className = 'radio-station';
                button.dataset.radioId = String(station.id);
                button.setAttribute('aria-pressed', 'false');
                const name = document.createElement('strong'); name.textContent = station.name;
                const play = document.createElement('span'); play.textContent = 'استمع'; play.setAttribute('aria-hidden', 'true');
                button.append(name, play); fragment.append(button);
            });
            list.append(fragment);
        };
        const loadStations = async () => {
            try {
                const cached = JSON.parse(localStorage.getItem(cacheKey) || 'null');
                stations = Array.isArray(cached?.items) && Date.now() - cached.savedAt < 86400000 ? cached.items : [];
            } catch (_) { stations = []; }
            if (!stations.length) {
                try {
                    const response = await fetch('https://mp3quran.net/api/v3/radios?language=ar', { headers: { Accept: 'application/json' }, signal: AbortSignal.timeout(15000) });
                    if (!response.ok) throw new Error('radio-provider');
                    const payload = await response.json();
                    stations = (Array.isArray(payload.radios) ? payload.radios : []).filter((station) => Number.isInteger(Number(station.id)) && typeof station.name === 'string' && /^https:\/\//i.test(station.url)).map((station) => ({ id: Number(station.id), name: station.name.trim(), url: station.url }));
                    if (!stations.length) throw new Error('radio-shape');
                    try { localStorage.setItem(cacheKey, JSON.stringify({ items: stations, savedAt: Date.now() })); } catch (_) { /* The directory works without local storage. */ }
                } catch (_) { stations = []; }
            }
            renderStations();
        };
        search.addEventListener('input', renderStations);
        list.addEventListener('click', async (event) => {
            const button = event.target.closest('[data-radio-id]');
            if (!button) return;
            const station = stations.find((item) => String(item.id) === button.dataset.radioId);
            if (!station) return;
            player.pause(); player.src = station.url; player.load();
            current.textContent = station.name;
            status.textContent = `جارٍ الاتصال بإذاعة ${station.name}…`;
            list.querySelectorAll('.radio-station').forEach((item) => { item.classList.toggle('is-active', item === button); item.setAttribute('aria-pressed', String(item === button)); });
            try { await player.play(); status.textContent = `يعمل البث الآن: ${station.name}`; }
            catch (_) { status.textContent = 'اضغط تشغيل في مشغل الصوت لبدء البث.'; }
            if (navigator.mediaSession && window.MediaMetadata) {
                navigator.mediaSession.metadata = new MediaMetadata({ title: station.name, artist: 'إذاعة القرآن الكريم', album: 'أثر · إذاعات MP3Quran' });
                navigator.mediaSession.playbackState = player.paused ? 'paused' : 'playing';
            }
        });
        player.addEventListener('error', () => { status.textContent = 'تعذر تشغيل هذه الإذاعة الآن. جرّب محطة أخرى.'; });
        player.addEventListener('play', () => { if (navigator.mediaSession) navigator.mediaSession.playbackState = 'playing'; });
        player.addEventListener('pause', () => { if (navigator.mediaSession) navigator.mediaSession.playbackState = 'paused'; });
        if (navigator.mediaSession) {
            try { navigator.mediaSession.setActionHandler('play', () => player.play()); } catch (_) { /* Lock-screen action not supported. */ }
            try { navigator.mediaSession.setActionHandler('pause', () => player.pause()); } catch (_) { /* Lock-screen action not supported. */ }
            try { navigator.mediaSession.setActionHandler('stop', () => { player.pause(); player.removeAttribute('src'); player.load(); }); } catch (_) { /* Lock-screen action not supported. */ }
        }
        loadStations();
    }
    if (quranReader && !['search', 'radio'].includes(quranReader.dataset.tool)) {
        const form = quranReader.querySelector('[data-quran-form]');
        const modeInput = quranReader.querySelector('[data-reading-mode]');
        const surahInput = quranReader.querySelector('[name="surah"]');
        const ayahInput = quranReader.querySelector('[name="ayah"]');
        const ayahEndInput = quranReader.querySelector('[name="ayah_end"]');
        const juzInput = quranReader.querySelector('[name="juz"]');
        const hizbInput = quranReader.querySelector('[name="hizb"]');
        const pageInput = quranReader.querySelector('[name="page"]');
        const status = quranReader.querySelector('[data-quran-status]');
        const content = quranReader.querySelector('[data-quran-content]');
        const audioWrap = quranReader.querySelector('[data-quran-audio]');
        const audio = audioWrap?.querySelector('audio');
        const audioStatus = quranReader.querySelector('[data-audio-status]');
        const reciterInput = quranReader.querySelector('[data-quran-reciter]');
        const scopeInputs = Array.from(quranReader.querySelectorAll('[data-audio-scope]'));
        const scopeValue = () => scopeInputs.find((input) => input.checked)?.value || 'current';
        const repeatInput = quranReader.querySelector('[data-audio-repeat]');
        const previousAudioButton = quranReader.querySelector('[data-audio-previous]');
        const nextAudioButton = quranReader.querySelector('[data-audio-next]');
        const audioCurrentSurah = quranReader.querySelector('[data-audio-current-surah]');
        const audioCurrentVerse = quranReader.querySelector('[data-audio-current-verse]');
        const audioCurrentNumber = quranReader.querySelector('[data-audio-current-number]');
        const tafsirSection = quranReader.querySelector('[data-quran-tafsir]');
        const tafsirButton = quranReader.querySelector('[data-tafsir-toggle]');
        const tafsirFrame = quranReader.querySelector('[data-tafsir-frame]');
        const reviewPanel = quranReader.querySelector('[data-recitation-review]');
        const reviewStart = quranReader.querySelector('[data-review-start]');
        const reviewStop = quranReader.querySelector('[data-review-stop]');
        const reviewStatus = quranReader.querySelector('[data-review-status]');
        const reviewAlert = quranReader.querySelector('[data-review-alert]');
        const reviewAlertTitle = quranReader.querySelector('[data-review-alert-title]');
        const reviewAlertAudio = quranReader.querySelector('[data-review-alert-audio]');
        const reviewResult = quranReader.querySelector('[data-review-result]');
        const reviewActions = quranReader.querySelector('[data-review-actions]');
        const reviewBeep = quranReader.querySelector('[data-review-beep]');
        const reviewVibrate = quranReader.querySelector('[data-review-vibrate]');
        const reviewLines = quranReader.querySelector('[data-review-lines]');
        const reviewEmpty = quranReader.querySelector('[data-review-empty]');
        const reviewPercent = quranReader.querySelector('[data-review-percent]');
        const reviewProgress = quranReader.querySelector('[data-review-progress]');
        const reviewProgressTrack = reviewProgress?.closest('[role="progressbar"]');
        const reviewListening = quranReader.querySelector('[data-review-listening]');
        const reviewListeningLabel = quranReader.querySelector('[data-review-listening-label]');
        const reviewSurahMeta = quranReader.querySelector('[data-review-surah]');
        const reviewRangeMeta = quranReader.querySelector('[data-review-range]');
        const reviewPageNumber = quranReader.querySelector('[data-review-page-number]');
        const baseUrl = 'https://api.quranpedia.net/v1';
        const surahNames = Array.from(surahInput.options, (option) => option.textContent.replace(/^\d+\.\s*/, '').trim());
        let fontSize = 1.8;
        let loadedVerses = [];
        let activeVerses = [];
        let activeIndex = 0;
        let activeTrack = null;
        let requestVersion = 0;
        let playbackRequestVersion = 0;
        let fullSurahReciters = [];
        let timingReads = [];
        let activeTimings = [];
        let activeSegmentTiming = null;

        const getMediaSession = () => 'mediaSession' in navigator ? navigator.mediaSession : null;
        const stopPlayback = () => {
            if (audio) { audio.pause(); audio.removeAttribute('src'); audio.load(); }
            activeTrack = null;
            const session = getMediaSession();
            if (session) { session.playbackState = 'none'; session.metadata = null; }
        };

        const normalizeVerse = (verse) => {
            const globalNumber = Number(verse.id ?? verse.number);
            const surah = Number(verse.surah?.number ?? verse.surah);
            const numberInSurah = Number(verse.numberInSurah ?? verse.number ?? verse.number_in_hafs?.[0]);
            if (!Number.isInteger(globalNumber) || !Number.isInteger(surah) || !Number.isInteger(numberInSurah) || typeof verse.text !== 'string') return null;
            return { globalNumber, surah, numberInSurah, page: Number(verse.page_number ?? verse.page ?? 0), juz: Number(verse.juz ?? 0), text: verse.text };
        };

        const saveAyah = async (surah, ayah, action, button) => {
            if (quranReader.dataset.signedIn !== 'true') {
                try {
                    const saved = JSON.parse(window.localStorage.getItem('athar-guest-quran') || '{}');
                    if (action === 'bookmark') {
                        const bookmarks = Array.isArray(saved.bookmarks) ? saved.bookmarks : [];
                        if (!bookmarks.some((item) => item.surah === surah && item.ayah === ayah)) bookmarks.push({ surah, ayah });
                        saved.bookmarks = bookmarks;
                    } else saved.position = { surah, ayah, updatedAt: Date.now() };
                    window.localStorage.setItem('athar-guest-quran', JSON.stringify(saved));
                    button.textContent = action === 'bookmark' ? 'حُفظت محليًا' : 'حُفظ محليًا';
                    button.setAttribute('aria-label', 'حُفظت هذه البيانات في هذا المتصفح فقط. سجّل الدخول لدمجها باختيارك.');
                    return;
                } catch (_) { button.textContent = 'تعذر الحفظ'; return; }
            }
            const token = document.querySelector('meta[name="csrf-token"]')?.content || '';
            const body = new URLSearchParams({ surah: String(surah), ayah: String(ayah), action });
            button.disabled = true;
            try {
                const response = await fetch('/api/quran/progress', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8', 'X-CSRF-Token': token, Accept: 'application/json' }, body, credentials: 'same-origin' });
                if (!response.ok) throw new Error('save-failed');
                button.dataset.saved = 'true'; button.textContent = action === 'bookmark' ? 'حُفظت' : 'تم';
            } catch (_) { button.textContent = 'أعد المحاولة'; button.disabled = false; }
        };

        const mediaMetadata = (verse) => {
            if (!verse || !window.MediaMetadata) return;
            const surahName = surahNames[verse.surah - 1] || `السورة ${verse.surah}`;
            const reciterName = reciterInput.selectedOptions[0]?.textContent || 'قارئ القرآن';
            const session = getMediaSession();
            if (!session) return;
            const title = scopeValue() === 'surah' ? `سورة ${surahName} كاملة` : `${surahName} · الآية ${verse.numberInSurah}`;
            session.metadata = new MediaMetadata({ title, artist: reciterName, album: 'القرآن الكريم · أثر' });
        };

        const updatePosition = () => {
            const session = getMediaSession();
            if (session && audio && Number.isFinite(audio.duration) && audio.duration > 0 && Number.isFinite(audio.currentTime)) {
                try { session.setPositionState({ duration: audio.duration, playbackRate: audio.playbackRate || 1, position: Math.min(audio.currentTime, audio.duration) }); } catch (_) { /* Browser does not support position state. */ }
            }
        };

        const paintActiveVerse = () => {
            content.querySelectorAll('.verse-list li.is-playing').forEach((item) => item.classList.remove('is-playing'));
            const verse = activeVerses[activeIndex];
            if (!verse) return;
            if (previousAudioButton) { previousAudioButton.disabled = activeIndex === 0; previousAudioButton.hidden = scopeValue() === 'surah'; }
            if (nextAudioButton) { nextAudioButton.disabled = activeIndex >= activeVerses.length - 1; nextAudioButton.hidden = scopeValue() === 'surah'; }
            content.querySelector(`#ayah-${verse.surah}-${verse.numberInSurah}`)?.classList.add('is-playing');
            if (audioCurrentSurah) audioCurrentSurah.textContent = `سورة ${surahNames[verse.surah - 1]}`;
            if (audioCurrentVerse) audioCurrentVerse.textContent = verse.text;
            if (audioCurrentNumber) audioCurrentNumber.textContent = `الآية ${new Intl.NumberFormat('ar').format(verse.numberInSurah)}`;
            mediaMetadata(verse);
            if (audioStatus) audioStatus.textContent = scopeValue() === 'surah' ? `الآن: سورة ${surahNames[verse.surah - 1]} كاملة` : `الآن: ${surahNames[verse.surah - 1]} · الآية ${verse.numberInSurah}`;
        };

        const timingFor = (ayah) => activeTimings.find((timing) => Number(timing.ayah) === Number(ayah));
        const loadAyahTimings = async (readId, surah) => {
            const cacheKey = `athar-mp3quran-timing-${readId}-${surah}-v1`;
            try {
                const cached = JSON.parse(localStorage.getItem(cacheKey) || 'null');
                if (Array.isArray(cached?.items) && Date.now() - cached.savedAt < 30 * 86400000) return cached.items;
            } catch (_) { /* Continue to the public timing endpoint. */ }
            const response = await fetch(`https://mp3quran.net/api/v3/ayat_timing?surah=${surah}&read=${readId}`, { headers: { Accept: 'application/json' }, signal: AbortSignal.timeout(12000) });
            if (!response.ok) throw new Error('timing-unavailable');
            const items = await response.json();
            if (!Array.isArray(items)) throw new Error('timing-shape');
            try { localStorage.setItem(cacheKey, JSON.stringify({ items, savedAt: Date.now() })); } catch (_) { /* Timing remains usable in memory. */ }
            return items;
        };
        const selectedAudioRead = () => fullSurahReciters.find((item) => item.key === reciterInput?.value) || null;
        const setAudioPosition = (startSeconds) => {
            if (!audio || !Number.isFinite(startSeconds)) return;
            const seek = () => { audio.currentTime = Math.max(0, startSeconds); };
            if (audio.readyState >= HTMLMediaElement.HAVE_METADATA) seek();
            else audio.addEventListener('loadedmetadata', seek, { once: true });
        };
        const playVerse = async (index, shouldPlay = true) => {
            if (!audio || !activeVerses.length) return;
            const playbackRequest = ++playbackRequestVersion;
            activeIndex = Math.max(0, Math.min(index, activeVerses.length - 1));
            const verse = activeVerses[activeIndex];
            paintActiveVerse();
            const qari = selectedAudioRead();
            if (!qari) { if (audioStatus) audioStatus.textContent = 'اختر قارئًا متاحًا لهذا النطاق.'; return; }
            const url = `${qari.server}${String(Number(surahInput.value)).padStart(3, '0')}.mp3`;
            activeSegmentTiming = null;
            activeTimings = [];
            if (scopeValue() === 'current') {
                try {
                    activeTimings = await loadAyahTimings(qari.timingReadId, Number(surahInput.value));
                    if (playbackRequest !== playbackRequestVersion) return;
                    activeSegmentTiming = timingFor(verse.numberInSurah);
                    if (!activeSegmentTiming) throw new Error('timing-ayah-unavailable');
                } catch (_) {
                    if (audioStatus) audioStatus.textContent = 'تعذر تحميل توقيت هذه الآية. اختر السورة كاملة أو قارئًا آخر.';
                    return;
                }
            } else if (qari.timingReadId) {
                try { activeTimings = await loadAyahTimings(qari.timingReadId, Number(surahInput.value)); }
                catch (_) { activeTimings = []; }
                if (playbackRequest !== playbackRequestVersion) return;
            }
            if (playbackRequest !== playbackRequestVersion) return;
            if (activeTrack !== url) { audio.pause(); audio.src = url; audio.load(); activeTrack = url; }
            if (scopeValue() === 'current' && activeSegmentTiming) setAudioPosition(Number(activeSegmentTiming.start_time) / 1000);
            if (!shouldPlay) return;
            try { await audio.play(); }
            catch (_) { if (audioStatus) audioStatus.textContent = 'اضغط تشغيل من مشغل الصوت لبدء التلاوة.'; }
        };

        const configurePlayback = (shouldPlay = false) => {
            const selected = Number(ayahInput.value) || null;
            const current = loadedVerses.findIndex((verse) => verse.surah === Number(surahInput.value) && verse.numberInSurah === selected);
            const mode = scopeValue();
            const start = current >= 0 ? current : 0;
            if (mode === 'current') activeVerses = loadedVerses.slice(start, start + 1);
            else if (mode === 'surah') activeVerses = loadedVerses.filter((verse) => verse.surah === Number(surahInput.value));
            else activeVerses = loadedVerses.slice(start);
            activeIndex = 0;
            playVerse(0, shouldPlay);
        };

        const buildReading = (verses, headingText) => {
            if (quranReader.dataset.tool === 'review') { content.replaceChildren(); return; }
            const heading = document.createElement('h2'); heading.textContent = headingText;
            const memoryButton = document.createElement('button');
            memoryButton.type = 'button'; memoryButton.className = 'memorize-toggle button button-secondary';
            memoryButton.textContent = 'وضع مراجعة الحفظ'; memoryButton.setAttribute('aria-pressed', 'false');
            const verseList = document.createElement('ol'); verseList.className = 'verse-list';
            let previousSurah = 0;
            verses.forEach((verse) => {
                if (verse.surah !== previousSurah) {
                    const surahHeading = document.createElement('li');
                    surahHeading.className = 'verse-surah-heading';
                    surahHeading.textContent = `سورة ${surahNames[verse.surah - 1]}`;
                    verseList.append(surahHeading);
                    previousSurah = verse.surah;
                }
                const item = document.createElement('li'); item.id = `ayah-${verse.surah}-${verse.numberInSurah}`;
                const text = document.createElement('span'); text.className = 'verse-text';
                if (quranReader.dataset.tool === 'review') {
                    verse.text.trim().split(/\s+/u).forEach((part, index) => {
                        const word = document.createElement('span'); word.className = 'verse-word'; word.dataset.wordIndex = String(index); word.dataset.canonicalText = part;
                        text.append(word, document.createTextNode(index ? ' ' : ''));
                    });
                } else text.textContent = verse.text;
                const reveal = document.createElement('button'); reveal.type = 'button'; reveal.className = 'verse-reveal'; reveal.textContent = 'أظهر الآية'; reveal.hidden = true;
                reveal.setAttribute('aria-label', `إظهار الآية ${verse.numberInSurah} من ${surahNames[verse.surah - 1]}`);
                reveal.addEventListener('click', () => { text.hidden = false; reveal.hidden = true; });
                const marker = document.createElement('span'); marker.className = 'verse-number'; marker.setAttribute('aria-label', `الآية ${verse.numberInSurah}`); marker.textContent = String(verse.numberInSurah);
                item.append(text, reveal, marker);
                if (quranReader.dataset.signedIn === 'true') {
                    const actions = document.createElement('span'); actions.className = 'ayah-actions'; actions.setAttribute('aria-label', `أدوات الآية ${verse.numberInSurah}`);
                    for (const [action, label] of [['bookmark', 'حفظ'], ['position', 'موضعي']]) {
                        const button = document.createElement('button'); button.type = 'button'; button.textContent = label;
                        button.setAttribute('aria-label', `${label} الآية ${verse.numberInSurah}`);
                        button.addEventListener('click', () => saveAyah(verse.surah, verse.numberInSurah, action, button)); actions.append(button);
                    }
                    item.append(actions);
                }
                verseList.append(item);
            });
            memoryButton.addEventListener('click', () => {
                const enabled = memoryButton.getAttribute('aria-pressed') !== 'true';
                memoryButton.setAttribute('aria-pressed', String(enabled)); memoryButton.textContent = enabled ? 'إنهاء مراجعة الحفظ' : 'وضع مراجعة الحفظ';
                verseList.querySelectorAll('li').forEach((item) => { const text = item.querySelector('.verse-text'); const reveal = item.querySelector('.verse-reveal'); if (!text || !reveal) return; text.hidden = enabled; reveal.hidden = !enabled; });
            });
            if (quranReader.dataset.tool === 'review') content.append(heading, verseList);
            else if (quranReader.dataset.tool !== 'daily') content.append(heading, memoryButton, verseList);
            else content.append(heading, verseList);
            if (quranReader.dataset.review === 'true') memoryButton.click();
        };

        const loadReading = async ({ mode = modeInput.value, surah = Number(surahInput.value), ayah = Number(ayahInput.value) || null, ayahEnd = Number(ayahEndInput?.value) || Number(ayahInput.value) || null, juz = Number(juzInput.value), hizb = Number(hizbInput?.value || quranReader.dataset.hizb), page = Number(pageInput.value) } = {}) => {
            const thisRequest = ++requestVersion;
            status.hidden = false; status.dataset.state = 'loading'; status.textContent = 'جارٍ تحميل الآيات…';
            content.hidden = true; content.replaceChildren(); loadedVerses = []; activeVerses = [];
            if (reviewPanel) reviewPanel.hidden = true;
            stopPlayback(); if (audioStatus) audioStatus.textContent = 'جارٍ تجهيز موضع القراءة…';
            if (tafsirSection) tafsirSection.hidden = true;
            if (tafsirFrame) { tafsirFrame.hidden = true; tafsirFrame.replaceChildren(); }
            if (tafsirButton) { tafsirButton.setAttribute('aria-expanded', 'false'); tafsirButton.textContent = 'عرض التفسير'; }
            try {
                let url;
                let rawVerses;
                if (quranReader.dataset.tool === 'daily') {
                    const now = new Date();
                    const dayKey = Number(`${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}`);
                    const dailyNumber = (dayKey * 37 % 6236) + 1;
                    url = `https://api.alquran.cloud/v1/ayah/${dailyNumber}/quran-uthmani`;
                } else if (mode === 'juz') url = `https://api.alquran.cloud/v1/juz/${juz}/quran-uthmani`;
                else if (mode === 'hizb') {
                    const firstQuarter = (hizb - 1) * 4 + 1;
                    const quarterResponses = await Promise.all(Array.from({ length: 4 }, (_, index) => fetch(`https://api.alquran.cloud/v1/hizbQuarter/${firstQuarter + index}/quran-uthmani`, { headers: { Accept: 'application/json' }, signal: AbortSignal.timeout(15000) })));
                    if (quarterResponses.some((result) => !result.ok)) throw new Error('provider-response');
                    const quarterPayloads = await Promise.all(quarterResponses.map((result) => result.json()));
                    rawVerses = quarterPayloads.flatMap((part) => part?.data?.ayahs || []);
                } else if (mode === 'page') url = `https://api.alquran.cloud/v1/page/${page}/quran-uthmani`;
                else url = `${baseUrl}/mushafs/1/${surah}`;
                if (!rawVerses) {
                    const response = await fetch(url, { headers: { Accept: 'application/json' }, signal: AbortSignal.timeout(15000) });
                    if (!response.ok) throw new Error('provider-response');
                    const payload = await response.json();
                    rawVerses = quranReader.dataset.tool === 'daily' ? [payload?.data] : (mode === 'surah' ? (Array.isArray(payload) ? payload : [payload]) : payload?.data?.ayahs);
                }
                const allVerses = (Array.isArray(rawVerses) ? rawVerses : []).map(normalizeVerse).filter(Boolean);
                if (!allVerses.length) throw new Error('provider-shape');
                if (quranReader.dataset.tool === 'review' && ayahEndInput) {
                    ayahInput.max = String(allVerses.length);
                    ayahEndInput.max = String(allVerses.length);
                    ayahEndInput.min = String(ayah || 1);
                }
                if (thisRequest !== requestVersion) return;
                if (quranReader.dataset.tool === 'daily') loadedVerses = allVerses;
                else if (quranReader.dataset.tool === 'review') {
                    if (!ayah || !ayahEnd || ayahEnd < ayah) throw new Error('invalid-review-range');
                    if (ayahEnd > allVerses.length) throw new Error('invalid-review-range');
                    loadedVerses = allVerses.filter((verse) => verse.numberInSurah >= ayah && verse.numberInSurah <= ayahEnd);
                } else if (['ayah', 'tafsir'].includes(quranReader.dataset.tool)) loadedVerses = ayah ? allVerses.filter((verse) => verse.numberInSurah === ayah) : allVerses;
                else loadedVerses = mode === 'surah' && ayah && quranReader.dataset.tool !== 'listen' ? allVerses.filter((verse) => verse.numberInSurah >= ayah) : allVerses;
                if (!loadedVerses.length) throw new Error('empty-range');
                if (quranReader.dataset.tool === 'review') {
                    reviewVerseIndexes = new Map(); reviewWordOffsets = []; reviewWordCounts = [];
                    let totalWords = 0;
                    loadedVerses.forEach((verse, index) => {
                        reviewVerseIndexes.set(`${verse.surah}:${verse.numberInSurah}`, index);
                        reviewWordOffsets[index] = totalWords;
                        reviewWordCounts[index] = verse.text.trim().split(/\s+/u).length;
                        totalWords += reviewWordCounts[index];
                    });
                    reviewTotalWords = totalWords;
                    reviewPrepareTemplate();
                }
                const currentVerse = loadedVerses[0];
                const heading = quranReader.dataset.tool === 'daily' ? `آية اليوم · سورة ${surahNames[currentVerse.surah - 1]} · الآية ${currentVerse.numberInSurah}` : quranReader.dataset.tool === 'review' ? `${surahNames[surah - 1]} · من الآية ${ayah} إلى ${ayahEnd}` : mode === 'juz' ? `الجزء ${juz} · ${loadedVerses.length} آية` : mode === 'hizb' ? `الحزب ${hizb} · ${loadedVerses.length} آية` : mode === 'page' ? `صفحة ${page} · ${loadedVerses.length} آية` : `${surahNames[surah - 1]} · ${ayah ? `من الآية ${ayah}` : `${loadedVerses.length} آية`}`;
                buildReading(loadedVerses, heading);
                if (quranReader.dataset.tool === 'daily') {
                    const actions = quranReader.querySelector('[data-daily-verse-actions]');
                    if (actions) {
                        actions.replaceChildren();
                        [['/quran?tool=listen', 'استمع إلى الآية'], ['/quran?tool=tafsir', 'افتح التفسير']].forEach(([href, label]) => { const link = document.createElement('a'); link.className = 'button button-secondary'; link.textContent = label; link.href = `${href}&surah=${currentVerse.surah}&ayah=${currentVerse.numberInSurah}`; actions.append(link); });
                    }
                }
                content.hidden = ['review', 'listen'].includes(quranReader.dataset.tool);
                if (mode === 'surah' && ayah && tafsirSection && tafsirButton && tafsirFrame) {
                    tafsirSection.hidden = false;
                    tafsirButton.onclick = () => {
                        if (!tafsirFrame.childElementCount) {
                            const frame = document.createElement('iframe'); frame.src = `https://api.quranpedia.net/embed?surah=${surah}&ayah=${ayah}&type=tafsir`; frame.title = `تفسير الآية ${ayah} من سورة ${surahNames[surah - 1]}`; frame.loading = 'lazy'; frame.referrerPolicy = 'strict-origin-when-cross-origin'; frame.allowFullscreen = true; tafsirFrame.append(frame);
                        }
                        const open = tafsirFrame.hidden; tafsirFrame.hidden = !open; tafsirButton.setAttribute('aria-expanded', String(open)); tafsirButton.textContent = open ? 'إخفاء التفسير' : 'عرض التفسير';
                    };
                }
                status.hidden = true; status.dataset.state = 'ready';
                if (audioWrap) audioWrap.hidden = quranReader.dataset.tool !== 'listen';
                if (reviewPanel) reviewPanel.hidden = quranReader.dataset.tool !== 'review' || !ayah || !loadedVerses.length;
                if (reviewPanel && quranReader.dataset.tool === 'review' && loadedVerses.length && reviewStart) reviewStart.disabled = false;
                if (mode === 'surah' && ayah) content.querySelector(`#ayah-${surah}-${ayah}`)?.scrollIntoView({ block: 'center' });
                configurePlayback(false);
                if (audioStatus) audioStatus.textContent = 'الموضع جاهز. اضغط تشغيل للاستماع.';
            } catch (error) {
                if (thisRequest !== requestVersion) return;
                status.dataset.state = 'error'; status.replaceChildren();
                const message = document.createElement('span'); message.textContent = error?.message === 'invalid-review-range' ? 'تأكد أن رقم البداية والنهاية ضمن السورة، وأن النهاية ليست قبل البداية.' : 'تعذر تحميل الآيات الآن. تحقق من الاتصال ثم حاول مجددًا.';
                const retry = document.createElement('button'); retry.className = 'button button-secondary'; retry.type = 'button'; retry.textContent = 'إعادة المحاولة'; retry.addEventListener('click', () => loadReading({ mode, surah, ayah, ayahEnd, juz, hizb, page }), { once: true });
                status.append(message, retry); if (audioStatus) audioStatus.textContent = 'تعذر تجهيز التلاوة لهذا الموضع.';
            }
        };

        const updateModeControls = () => {
            quranReader.querySelectorAll('[data-mode-control]').forEach((control) => { control.hidden = control.dataset.modeControl !== modeInput.value; });
            const surahScope = scopeInputs.find((input) => input.value === 'surah');
            if (surahScope) surahScope.disabled = modeInput.value !== 'surah';
        };
        modeInput.addEventListener('change', updateModeControls); updateModeControls();
        const updateAudioScope = () => {
            const full = scopeValue() === 'surah';
            const ayahControl = quranReader.querySelector('[data-ayah-control]');
            if (quranReader.dataset.tool === 'listen' && ayahControl) {
                ayahControl.hidden = full;
                ayahInput.required = !full;
            }
            const repeatLabel = repeatInput?.closest('label');
            if (repeatLabel) repeatLabel.lastChild.textContent = full ? ' تكرار السورة' : ' تكرار التلاوة';
            const providerNote = quranReader.querySelector('[data-audio-provider-note]');
            if (providerNote) providerNote.textContent = full
                ? 'المكتبة تضم القراء والروايات المتاحة، ويظهر موضع الآية عند توفر التوقيت.'
                : 'اختر قارئًا له توقيت لهذه السورة؛ يشغّل المشغل موضع الآية فقط من ملف السورة.';
        };
        scopeInputs.forEach((input) => input.addEventListener('change', () => { updateAudioScope(); loadReciters(scopeValue(), Number(surahInput.value)).then(() => configurePlayback(false)); }));
        updateAudioScope();
        form?.addEventListener('submit', async (event) => {
            event.preventDefault();
            if (modeInput.value === 'page' && (Number(pageInput.value) < 1 || Number(pageInput.value) > 604)) { pageInput.setCustomValidity('رقم الصفحة يجب أن يكون بين 1 و604'); pageInput.reportValidity(); return; }
            pageInput.setCustomValidity('');
            const query = new URLSearchParams({ tool: quranReader.dataset.tool, mode: modeInput.value, surah: surahInput.value, juz: juzInput.value, hizb: hizbInput?.value || '1', page: pageInput.value });
            if (modeInput.value === 'surah' && ayahInput.value && (quranReader.dataset.tool !== 'listen' || scopeValue() === 'current')) query.set('ayah', ayahInput.value);
            if (quranReader.dataset.tool === 'review') query.set('ayah_end', ayahEndInput.value || ayahInput.value);
            history.replaceState(null, '', `/quran?${query}`);
            if (quranReader.dataset.tool === 'review' && reviewWorker) {
                reviewStopCapture(); reviewWorker.terminate(); reviewWorker = null; reviewReady = false; reviewStartQueued = false;
                if (reviewResult) reviewResult.hidden = true;
                reviewSetStatus(reviewText('يتم تحميل الآية المختارة.', 'Loading the selected verse.'));
            }
            if (quranReader.dataset.tool === 'listen') await loadReciters(scopeValue(), Number(surahInput.value));
            loadReading({ ayahEnd: Number(ayahEndInput?.value) || Number(ayahInput.value) || null });
        });
        quranReader.querySelectorAll('[data-font-step]').forEach((button) => button.addEventListener('click', () => { fontSize = Math.min(2.8, Math.max(1.2, fontSize + Number(button.dataset.fontStep) * 0.2)); quranReader.style.setProperty('--reading-font-size', `${fontSize}rem`); }));

        let reviewWorker = null;
        let reviewStream = null;
        let reviewContext = null;
        let reviewResampler = null;
        let reviewProcessor = null;
        let reviewPendingChunks = [];
        let reviewPendingLength = 0;
        let reviewReady = false;
        let reviewStartQueued = false;
        let reviewPaused = false;
        let reviewResumeAction = 'continue';
        let reviewIssueKey = '';
        let reviewCounted = false;
        let reviewVerseIndexes = new Map();
        let reviewWordCounts = [];
        let reviewWordOffsets = [];
        let reviewDisplayedWords = new Map();
        let reviewMatchedWords = new Map();
        let reviewWordVerdicts = new Map();
        let reviewWordNodes = new Map();
        let reviewCurrentWordNode = null;
        let reviewCurrentWordKey = '';
        let reviewVisiblePageIndex = -1;
        let reviewTotalWords = 0;
        const reviewVersesPerPage = 10;
        let reviewAudioWorkletLoaded = false;
        let reviewSignalSeconds = 0;
        let reviewAlertAudioPrimed = false;
        const reviewText = (ar, en) => root.lang === 'en' ? en : ar;
        const reviewIntro = reviewPanel?.querySelector('[data-review-intro]');
        if (reviewIntro) reviewIntro.textContent = reviewText(
            'اختر السورة والآيات ثم ابدأ التلاوة. الكلمات المعروضة نص المصحف المرجعي وليست تفريغًا حرفيًا للصوت؛ اللون يوضح حكم النموذج على المطابقة. يعمل النموذج محليًا؛ يلزم اتصال لتنزيله أول مرة، واترك الصفحة مفتوحة أثناء المراجعة.',
            'Choose a surah and verses, then recite. Displayed words are the reference Mushaf text, not a verbatim transcript; colors show the model’s match judgment. Recognition runs on this device; the model downloads once, and the page must stay open during review.',
        );
        const reviewLegend = reviewPanel?.querySelector('[data-review-verdict-legend]');
        if (reviewLegend) {
            reviewLegend.setAttribute('aria-label', reviewText('دلالات ألوان المراجعة', 'Recitation review color legend'));
            const labels = root.lang === 'en'
                ? { clear: 'Clear match', unsure: 'Uncertain or possible vowel issue', error: 'Possible mismatch' }
                : { clear: 'مطابقة واضحة', unsure: 'غير محسومة أو حركة محتملة', error: 'اختلاف محتمل' };
            reviewLegend.querySelectorAll('[data-review-legend]').forEach((item) => { item.textContent = labels[item.dataset.reviewLegend] || item.textContent; });
        }
        const reviewSetStatus = (message, state = 'info') => { if (reviewStatus) { reviewStatus.textContent = message; reviewStatus.dataset.state = state; } };
        const reviewDisconnectProcessor = () => {
            if (reviewProcessor) {
                if ('onaudioprocess' in reviewProcessor) reviewProcessor.onaudioprocess = null;
                if (reviewProcessor.port) reviewProcessor.port.onmessage = null;
                reviewProcessor.disconnect(); reviewProcessor = null;
            }
        };
        const reviewStopCapture = () => {
            reviewDisconnectProcessor();
            if (reviewStream) { reviewStream.getTracks().forEach((track) => track.stop()); reviewStream = null; }
            reviewResampler = null;
            reviewPendingChunks = []; reviewPendingLength = 0;
            if (reviewStart) reviewStart.disabled = !loadedVerses.length;
            if (reviewStop) reviewStop.disabled = true;
        };
        const createReviewResampler = (fromRate) => {
            const targetRate = 16000;
            if (fromRate === targetRate) return { process: (input) => input };
            if (fromRate < targetRate) {
                return { process: (input) => {
                    const ratio = fromRate / targetRate;
                    const output = new Float32Array(Math.max(1, Math.floor(input.length / ratio)));
                    for (let i = 0; i < output.length; i++) {
                        const at = i * ratio; const left = Math.floor(at); const fraction = at - left;
                        output[i] = input[left] * (1 - fraction) + (input[Math.min(left + 1, input.length - 1)] || 0) * fraction;
                    }
                    return output;
                } };
            }

            // Low-pass before decimation. Linear interpolation alone aliases
            // sibilants and other high-frequency consonant cues into the model band.
            const ratio = fromRate / targetRate;
            const radius = 16;
            const phaseCount = 128;
            const cutoff = 0.47 / ratio;
            const phases = Array.from({ length: phaseCount }, (_, phase) => {
                const fraction = phase / phaseCount;
                const weights = new Float32Array(radius * 2 + 1);
                let total = 0;
                for (let tap = -radius; tap <= radius; tap++) {
                    const distance = tap - fraction;
                    const windowPos = distance / (radius + 1);
                    const window = Math.abs(windowPos) >= 1 ? 0
                        : 0.42 + 0.5 * Math.cos(Math.PI * windowPos) + 0.08 * Math.cos(2 * Math.PI * windowPos);
                    const x = 2 * cutoff * distance;
                    const sinc = Math.abs(x) < 1e-8 ? 1 : Math.sin(Math.PI * x) / (Math.PI * x);
                    const weight = 2 * cutoff * sinc * window;
                    weights[tap + radius] = weight;
                    total += weight;
                }
                for (let i = 0; i < weights.length; i++) weights[i] /= total;
                return weights;
            });
            let carry = new Float32Array(0);
            let carryStart = 0;
            let received = 0;
            let outputIndex = 0;
            return {
                process(input) {
                    const source = new Float32Array(carry.length + input.length);
                    source.set(carry);
                    source.set(input, carry.length);
                    received += input.length;
                    const output = [];
                    while (outputIndex * ratio + radius < received) {
                        const position = outputIndex * ratio;
                        let center = Math.floor(position);
                        let phase = Math.round((position - center) * phaseCount);
                        if (phase === phaseCount) { center += 1; phase = 0; }
                        const weights = phases[phase];
                        let value = 0;
                        for (let tap = -radius; tap <= radius; tap++) {
                            const absolute = center + tap;
                            const local = absolute - carryStart;
                            const sample = absolute < 0 ? source[0] : (source[local] || 0);
                            value += sample * weights[tap + radius];
                        }
                        output.push(value);
                        outputIndex += 1;
                    }
                    const keepFrom = Math.max(0, Math.floor(outputIndex * ratio) - radius - 1);
                    const discard = Math.min(source.length, Math.max(0, keepFrom - carryStart));
                    carry = source.slice(discard);
                    carryStart += discard;
                    return Float32Array.from(output);
                },
            };
        };
        const reviewResample = (input, fromRate) => {
            if (!reviewResampler || reviewResampler.sampleRate !== fromRate) {
                reviewResampler = createReviewResampler(fromRate);
                reviewResampler.sampleRate = fromRate;
            }
            return reviewResampler.process(input);
        };
        const reviewSendAudio = (buffer, rms) => {
            if (!(buffer instanceof ArrayBuffer) || !reviewWorker) return;
            const samples = reviewResample(new Float32Array(buffer), reviewContext.sampleRate);
            reviewWorker.postMessage({ type: 'audio', samples }, [samples.buffer]);
            if (reviewListening) {
                const level = Math.min(1, Math.max(0, (Number(rms) || 0) * 18));
                reviewListening.querySelectorAll('i').forEach((bar, index) => { bar.style.height = `${Math.max(3, 3 + level * (9 + index % 3 * 3))}px`; });
                reviewSignalSeconds += samples.length / 16000;
                if (level >= 0.055) {
                    reviewListening.dataset.audioDetected = 'true';
                    if (reviewListeningLabel) reviewListeningLabel.textContent = reviewText('الصوت يصل بوضوح', 'Audio detected');
                } else if (reviewSignalSeconds >= 2 && reviewListening.dataset.audioDetected !== 'true') {
                    if (reviewListeningLabel) reviewListeningLabel.textContent = reviewText('لم نلتقط صوتًا واضحًا؛ تحقق من الميكروفون', 'No clear audio yet; check the microphone');
                }
            }
        };
        const reviewStartCapture = async ({ resume = false } = {}) => {
            if (!reviewContext || !reviewReady) return;
            await reviewContext.resume();
            reviewResampler = null;
            if (!reviewStream) reviewStream = await navigator.mediaDevices.getUserMedia({ audio: { channelCount: 1, echoCancellation: false, noiseSuppression: false, autoGainControl: false } });
            const expected = loadedVerses[0];
            const lastExpected = loadedVerses.at(-1);
            if (!expected || !lastExpected) { reviewSetStatus(reviewText('اختر نطاق آيات صالحًا أولًا.', 'Choose a valid verse range first.'), 'error'); return; }
            if (!resume) reviewWorker.postMessage({ type: 'start', passage: { surah: expected.surah, ayah: expected.numberInSurah, ayahEnd: lastExpected.numberInSurah } });
            const source = reviewContext.createMediaStreamSource(reviewStream);
            const muted = reviewContext.createGain(); muted.gain.value = 0;
            if (reviewContext.audioWorklet && window.AudioWorkletNode) {
                try {
                    if (!reviewAudioWorkletLoaded) {
                        await reviewContext.audioWorklet.addModule('/assets/js/recitation-audio-worklet.js');
                        reviewAudioWorkletLoaded = true;
                    }
                    reviewProcessor = new AudioWorkletNode(reviewContext, 'athar-review-capture', { numberOfInputs: 1, numberOfOutputs: 1, channelCount: 1, channelCountMode: 'explicit', outputChannelCount: [1], processorOptions: { chunkSeconds: 0.2 } });
                    reviewProcessor.port.onmessage = (event) => reviewSendAudio(event.data?.samples, event.data?.rms);
                } catch (_) { reviewProcessor = null; }
            }
            if (!reviewProcessor) {
                reviewProcessor = reviewContext.createScriptProcessor(2048, 1, 1);
                reviewProcessor.onaudioprocess = (event) => {
                    const data = event.inputBuffer.getChannelData(0);
                    reviewPendingChunks.push(new Float32Array(data)); reviewPendingLength += data.length;
                    if (reviewPendingLength < reviewContext.sampleRate * 0.2) return;
                    const merged = new Float32Array(reviewPendingLength); let offset = 0;
                    reviewPendingChunks.forEach((chunk) => { merged.set(chunk, offset); offset += chunk.length; });
                    reviewPendingChunks = []; reviewPendingLength = 0;
                    let sumSquares = 0; for (const sample of merged) sumSquares += sample * sample;
                    reviewSendAudio(merged.buffer, Math.sqrt(sumSquares / merged.length));
                };
            }
            source.connect(reviewProcessor); reviewProcessor.connect(muted); muted.connect(reviewContext.destination);
            reviewStartQueued = false; reviewStart.disabled = true; reviewStop.disabled = false;
            if (!resume) {
                reviewCounted = false; reviewIssueKey = ''; reviewDisplayedWords = new Map(); reviewMatchedWords = new Map(); reviewCurrentWordKey = '';
                reviewSignalSeconds = 0;
                reviewPrepareTemplate();
                if (reviewEmpty) reviewEmpty.hidden = false;
                if (reviewProgress) reviewProgress.style.width = '0%';
                if (reviewPercent) reviewPercent.textContent = '٠٪';
                if (reviewProgressTrack) reviewProgressTrack.setAttribute('aria-valuenow', '0');
            }
            if (reviewListening) { reviewListening.classList.add('is-listening'); delete reviewListening.dataset.audioDetected; reviewListening.querySelectorAll('i').forEach((bar) => { bar.style.height = ''; }); }
            if (reviewListeningLabel) reviewListeningLabel.textContent = reviewText('ينتظر صوتك', 'Waiting for your voice');
            reviewAlert.hidden = true; reviewActions.hidden = true;
            reviewResult.textContent = '';
            reviewResult.hidden = true;
            reviewSetStatus(reviewText(`نستمع الآن للنطاق من الآية ${expected.numberInSurah} إلى ${lastExpected.numberInSurah}.`, `Listening to verses ${expected.numberInSurah} through ${lastExpected.numberInSurah}.`), 'ready');
        };
        const reviewPrimeAlertAudio = () => {
            if (!reviewAlertAudio || reviewAlertAudioPrimed) return;
            reviewAlertAudio.muted = true;
            reviewAlertAudio.currentTime = 0;
            const priming = reviewAlertAudio.play();
            if (!priming?.then) {
                reviewAlertAudio.pause(); reviewAlertAudio.muted = false; reviewAlertAudioPrimed = true;
                return;
            }
            priming.then(() => {
                reviewAlertAudio.pause(); reviewAlertAudio.currentTime = 0;
                reviewAlertAudio.muted = false; reviewAlertAudioPrimed = true;
            }).catch(() => { reviewAlertAudio.muted = false; });
        };
        const reviewBeepOnce = async () => {
            if (!reviewBeep?.checked) return;
            if (reviewAlertAudio) {
                try {
                    reviewAlertAudio.muted = false;
                    reviewAlertAudio.volume = 1;
                    reviewAlertAudio.currentTime = 0;
                    await reviewAlertAudio.play();
                    return;
                } catch (_) { /* Fall back to Web Audio where media playback is unavailable. */ }
            }
            if (!reviewContext) return;
            try { await reviewContext.resume(); } catch { return; }
            if (reviewContext.state !== 'running') return;
            const oscillator = reviewContext.createOscillator(); const gain = reviewContext.createGain();
            const now = reviewContext.currentTime;
            oscillator.type = 'sine'; oscillator.frequency.setValueAtTime(1046, now); oscillator.frequency.setValueAtTime(784, now + 0.16);
            gain.gain.setValueAtTime(0.0001, now); gain.gain.exponentialRampToValueAtTime(0.38, now + 0.025);
            gain.gain.setValueAtTime(0.38, now + 0.14); gain.gain.exponentialRampToValueAtTime(0.0001, now + 0.4);
            oscillator.connect(gain); gain.connect(reviewContext.destination); oscillator.start(now); oscillator.stop(now + 0.42);
        };
        const reviewVibrateOnce = () => {
            if (!reviewVibrate?.checked || typeof navigator.vibrate !== 'function') return;
            try { navigator.vibrate([120, 70, 120]); } catch { /* Vibration is optional and device-dependent. */ }
        };
        if (typeof navigator.vibrate !== 'function') {
            const vibrateLabel = reviewVibrate?.closest('label');
            if (vibrateLabel) vibrateLabel.hidden = true;
            const hapticsNote = reviewPanel?.querySelector('[data-review-haptics-note]');
            if (hapticsNote) hapticsNote.hidden = false;
        }
        const reviewWordsOf = (verse) => verse.text.trim().split(/\s+/u);
        const reviewApplyVerdict = (node, verdict) => {
            if (!node) return;
            const state = verdict?.state || '';
            node.classList.toggle('is-reviewed-correct', state === 'ok');
            node.classList.toggle('is-review-uncertain', state === 'unsure' || state === 'possible_vowel');
            node.classList.toggle('is-possible-error', state === 'wrong' || state === 'skipped');
            node.dataset.reviewState = state;
            node.title = state === 'ok' ? reviewText('تطابق صوتي واضح بحسب النموذج', 'Clear acoustic match according to the model')
                : state === 'unsure' ? reviewText('النموذج غير متأكد من هذه الكلمة', 'The model is unsure about this word')
                    : state === 'possible_vowel' ? reviewText('اختلاف محتمل في الحركة؛ راجع نطق الكلمة', 'Possible short-vowel mismatch; check the word')
                    : state === 'wrong' || state === 'skipped' ? reviewText('اختلاف صوتي محتمل — راجعه بنفسك', 'Possible acoustic mismatch — please check it') : '';
        };
        const reviewStoreVerdicts = (surah, ayah, verdicts) => {
            const verseIndex = reviewVerseIndexes.get(`${surah}:${ayah}`);
            if (!Number.isInteger(verseIndex) || !Array.isArray(verdicts)) return;
            const byWord = new Map();
            verdicts.forEach((verdict) => {
                const word = Number(verdict?.word);
                if (!Number.isInteger(word) || word < 0 || word >= reviewWordCounts[verseIndex]
                    || !['ok', 'unsure', 'wrong', 'skipped', 'pending'].includes(verdict?.state)) return;
                let displayVerdict = verdict;
                // Match the SDK's own clear-word evidence gate before showing
                // green. A cursor/alignment result alone is not proof that the
                // phonemes were heard clearly enough.
                if (verdict.state === 'ok' && !(Number.isFinite(verdict.distance) && verdict.distance <= 0.15
                    && Number.isFinite(verdict.margin) && verdict.margin >= 0.55
                    && Number.isFinite(verdict.heard_ratio) && verdict.heard_ratio >= 0.75 && verdict.heard_ratio <= 1.3)) {
                    displayVerdict = { ...verdict, state: 'unsure' };
                }
                byWord.set(word, displayVerdict);
                if (Number.isInteger(word) && word >= 0 && word < reviewWordCounts[verseIndex]
                    && ['ok', 'unsure'].includes(displayVerdict.state) && Number(verdict.vowel_errors) > 0
                    && Number(verdict.vowel_margin) >= 0.05 && Number(verdict.margin) >= 0.8
                    && Number(verdict.heard_ratio) >= 0.75 && Number(verdict.heard_ratio) <= 1.3) {
                    byWord.set(word, { ...displayVerdict, state: 'possible_vowel' });
                }
            });
            reviewWordVerdicts.set(verseIndex, byWord);
            reviewMatchedWords.set(verseIndex, new Set([...byWord].filter(([, verdict]) => verdict.state === 'ok').map(([word]) => word)));
            const displayed = reviewDisplayedWords.get(verseIndex) || new Set();
            byWord.forEach((verdict, word) => { if (verdict.state !== 'pending') displayed.add(word); });
            reviewDisplayedWords.set(verseIndex, displayed);
        };
        const reviewBuildPage = (pageIndex) => {
            if (!reviewLines) return;
            const firstIndex = pageIndex * reviewVersesPerPage;
            const pageVerses = loadedVerses.slice(firstIndex, firstIndex + reviewVersesPerPage);
            const formatNumber = (value) => new Intl.NumberFormat(root.lang === 'en' ? 'en' : 'ar').format(value);
            const fragment = document.createDocumentFragment();
            reviewWordNodes = new Map(); reviewCurrentWordNode = null;
            pageVerses.forEach((verse, pageOffset) => {
                const verseIndex = firstIndex + pageOffset;
                const words = reviewWordsOf(verse);
                const ayah = document.createElement('span'); ayah.className = 'review-mushaf-ayah';
                ayah.dataset.verseIndex = String(verseIndex);
                words.forEach((word, wordIndex) => {
                    const slot = document.createElement('span');
                    slot.className = 'review-mushaf-slot';
                    slot.dataset.reviewWord = `${verse.surah}:${verse.numberInSurah}:${wordIndex}`;
                    const letters = Array.from(word.normalize('NFC').replace(/\p{M}/gu, '')).length;
                    slot.style.setProperty('--slot-units', String(Math.max(2, Math.min(10, letters))));
                    const display = reviewDisplayedWords.get(verseIndex);
                    const matched = reviewMatchedWords.get(verseIndex);
                    const verdict = reviewWordVerdicts.get(verseIndex)?.get(wordIndex);
                    if (display?.has(wordIndex) || reviewCurrentWordKey === `${verse.surah}:${verse.numberInSurah}:${wordIndex}`) {
                        slot.textContent = word; slot.classList.add('is-revealed');
                    }
                    if (matched?.has(wordIndex)) slot.classList.add('is-reviewed-correct');
                    reviewApplyVerdict(slot, verdict);
                    reviewWordNodes.set(`${verse.surah}:${verse.numberInSurah}:${wordIndex}`, slot);
                    ayah.append(slot, document.createTextNode(' '));
                });
                const marker = document.createElement('span'); marker.className = 'review-mushaf-ayah-number'; marker.textContent = `۝ ${formatNumber(verse.numberInSurah)}`;
                ayah.append(marker); fragment.append(ayah, document.createTextNode(' '));
            });
            reviewLines.replaceChildren(fragment);
            if (reviewEmpty) reviewEmpty.hidden = pageVerses.some((_, offset) => (reviewDisplayedWords.get(firstIndex + offset)?.size ?? 0) > 0);
            if (reviewPageNumber) reviewPageNumber.textContent = reviewText(`ورقة ${formatNumber(pageIndex + 1)} من ${formatNumber(Math.max(1, Math.ceil(loadedVerses.length / reviewVersesPerPage)))}`, `Sheet ${formatNumber(pageIndex + 1)} of ${formatNumber(Math.max(1, Math.ceil(loadedVerses.length / reviewVersesPerPage)))}`);
            reviewVisiblePageIndex = pageIndex;
        };
        const reviewPrepareTemplate = () => {
            reviewDisplayedWords = new Map(); reviewMatchedWords = new Map(); reviewWordVerdicts = new Map(); reviewCurrentWordKey = ''; reviewVisiblePageIndex = -1;
            reviewSurahMeta.textContent = reviewText(`سورة ${surahNames[loadedVerses[0].surah - 1]}`, `Surah ${surahNames[loadedVerses[0].surah - 1]}`);
            const formatNumber = (value) => new Intl.NumberFormat(root.lang === 'en' ? 'en' : 'ar').format(value);
            reviewRangeMeta.textContent = reviewText(`من الآية ${formatNumber(loadedVerses[0].numberInSurah)} إلى الآية ${formatNumber(loadedVerses.at(-1).numberInSurah)}`, `Verses ${formatNumber(loadedVerses[0].numberInSurah)}–${formatNumber(loadedVerses.at(-1).numberInSurah)}`);
            reviewBuildPage(0);
            if (reviewEmpty) reviewEmpty.hidden = false;
        };
        const reviewRevealThrough = (surah, ayah, wordIndex, matchedIndices = [], revealWholeVerse = false) => {
            if (!reviewLines) return;
            const verseIndex = reviewVerseIndexes.get(`${surah}:${ayah}`);
            if (!Number.isInteger(verseIndex)) return;
            const nextCurrentKey = `${surah}:${ayah}:${wordIndex}`;
            const matched = reviewMatchedWords.get(verseIndex) || new Set();
            reviewMatchedWords.set(verseIndex, matched);
            const displayed = reviewDisplayedWords.get(verseIndex) || new Set();
            matchedIndices.filter(Number.isInteger).forEach((index) => { if (index >= 0 && index < reviewWordCounts[verseIndex]) displayed.add(index); });
            matched.forEach((index) => displayed.add(index));
            reviewWordVerdicts.get(verseIndex)?.forEach((verdict, index) => { if (verdict.state !== 'pending') displayed.add(index); });
            const lastWord = Math.max(0, Math.min(reviewWordCounts[verseIndex] - 1, Number(wordIndex) || 0));
            if (revealWholeVerse) for (let index = 0; index < reviewWordCounts[verseIndex]; index++) displayed.add(index);
            else if (Number.isInteger(Number(wordIndex))) displayed.add(lastWord);
            reviewDisplayedWords.set(verseIndex, displayed);
            reviewCurrentWordKey = revealWholeVerse ? '' : nextCurrentKey;
            const pageIndex = Math.floor(verseIndex / reviewVersesPerPage);
            if (pageIndex !== reviewVisiblePageIndex) reviewBuildPage(pageIndex);
            const verse = loadedVerses[verseIndex];
            const words = reviewWordsOf(verse);
            const indexesToReveal = revealWholeVerse ? displayed : new Set([...displayed, ...matched, lastWord]);
            for (const index of indexesToReveal) {
                const node = reviewWordNodes.get(`${surah}:${ayah}:${index}`);
                if (!node) continue;
                if (!node.classList.contains('is-revealed')) { node.textContent = words[index]; node.classList.add('is-revealed'); }
                reviewApplyVerdict(node, reviewWordVerdicts.get(verseIndex)?.get(index));
            }
            reviewCurrentWordNode?.classList.remove('is-review-current');
            reviewCurrentWordNode = reviewWordNodes.get(reviewCurrentWordKey) || null;
            reviewCurrentWordNode?.classList.add('is-review-current');
            if (reviewEmpty) reviewEmpty.hidden = reviewLines.querySelector('.is-revealed') !== null;
        };
        const reviewHandleEvent = (message) => {
            if (message.type === 'word_progress') {
                reviewStoreVerdicts(message.surah, message.ayah, message.word_verdicts);
                reviewRevealThrough(message.surah, message.ayah, message.word_index, message.matched_indices || []);
                const currentIndex = reviewVerseIndexes.get(`${message.surah}:${message.ayah}`);
                if (reviewListeningLabel && Number.isInteger(currentIndex)) {
                    const wordNumber = Math.max(1, Number(message.word_index) + 1);
                    reviewListeningLabel.textContent = reviewText(`نتابع الآية ${message.ayah} · الكلمة ${wordNumber}`, `Following verse ${message.ayah} · word ${wordNumber}`);
                }
                if (reviewProgress && reviewTotalWords > 0 && Number.isInteger(currentIndex)) {
                    const wordIndex = Math.max(0, Math.min(reviewWordCounts[currentIndex] - 1, Number(message.word_index) || 0));
                    const percent = Math.min(100, Math.round(((reviewWordOffsets[currentIndex] + wordIndex + 1) / reviewTotalWords) * 100));
                    reviewProgress.style.width = `${percent}%`;
                    if (reviewPercent) reviewPercent.textContent = `${new Intl.NumberFormat(root.lang === 'en' ? 'en' : 'ar').format(percent)}٪`;
                    if (reviewProgressTrack) reviewProgressTrack.setAttribute('aria-valuenow', String(percent));
                }
            } else if (message.type === 'correction') {
                const issue = message.state?.issue;
                reviewWordNodes.forEach((node) => node.classList.remove('is-possible-error', 'is-reviewed-correct'));
                if (issue && ['possible_omission', 'possible_substitution', 'possible_vowel', 'possible_repetition', 'possible_skipped_ayah', 'unclear_ayah'].includes(issue.kind)) {
                    const verseIndex = reviewVerseIndexes.get(`${issue.surah}:${issue.ayah}`);
                    const verse = Number.isInteger(verseIndex) ? loadedVerses[verseIndex] : null;
                    const words = verse ? reviewWordsOf(verse) : [];
                    const wholeAyah = ['possible_skipped_ayah', 'unclear_ayah'].includes(issue.kind);
                    // `issue.word` is the zero-based position within this ayah.
                    // `issue.wordIndex` is the corpus-wide position, so using it
                    // against this ayah's word array silently drops highlights
                    // for most of the Quran (and can point at the wrong word).
                    const wordIndex = Number.isInteger(issue.word) ? issue.word : -1;
                    const word = wholeAyah ? null : words[wordIndex];
                    if (verse && (word || wholeAyah)) {
                        const corrected = message.state?.phase === 'corrected';
                        if (!corrected) {
                            reviewRevealThrough(issue.surah, issue.ayah, wholeAyah ? words.length - 1 : wordIndex);
                            if (wholeAyah) {
                                words.forEach((_, index) => reviewWordNodes.get(`${issue.surah}:${issue.ayah}:${index}`)?.classList.add('is-possible-error'));
                            } else reviewWordNodes.get(`${issue.surah}:${issue.ayah}:${wordIndex}`)?.classList.add('is-possible-error');
                        }
                        if (corrected) {
                            reviewIssueKey = '';
                            reviewSetStatus(reviewText('تم رصد تحسن في إعادة المحاولة.', 'The retry appears improved.'), 'ready');
                            reviewResult.textContent = reviewText('تحسّنت المحاولة بحسب النموذج. تظل النتيجة استرشادية.', 'The model detected an improved retry. This result is still advisory.');
                            reviewAlert.dataset.state = 'success';
                            reviewAlertTitle.textContent = reviewText('تحسّنت المحاولة', 'Retry improved');
                            reviewAlert.hidden = false;
                            reviewActions.hidden = false;
                            reviewActions.querySelector('[data-review-action="retry"]').hidden = true;
                            reviewActions.querySelector('[data-review-action="continue"]').hidden = false;
                            reviewActions.querySelector('[data-review-action="dismiss"]').hidden = true;
                        } else {
                            const issueKey = `${issue.surah}:${issue.ayah}:${issue.word}:${issue.kind}`;
                            if (issueKey !== reviewIssueKey) { reviewIssueKey = issueKey; void reviewBeepOnce(); reviewVibrateOnce(); }
                            reviewAlert.dataset.state = message.state?.phase === 'error' ? 'error' : 'warning';
                            reviewAlertTitle.textContent = message.state?.phase === 'error'
                                ? reviewText('توقفت المراجعة عند موضع يحتاج مراجعة', 'Review paused at a possible mistake')
                                : reviewText('تنبيه مراجعة', 'Recitation check');
                            reviewAlert.hidden = false;
                            reviewResult.textContent = wholeAyah
                                ? reviewText('لم يتمكن النموذج من متابعة الآية بثقة؛ راجعها كاملة بنفسك.', 'The model could not confidently follow this verse; please check the whole verse yourself.')
                                : reviewText(`تنبيه: قد تحتاج كلمة «${word}» إلى مراجعة. قد يخطئ النموذج؛ استمع إلى تلاوتك وتحقق بنفسك.`, `Possible issue near “${word}”. The model can be wrong; listen and check the word yourself.`);
                            reviewActions.hidden = message.state?.phase !== 'error' && message.state?.phase !== 'retrying';
                            reviewActions.querySelector('[data-review-action="retry"]').hidden = message.state?.phase === 'retrying';
                            reviewActions.querySelector('[data-review-action="continue"]').hidden = message.state?.phase === 'retrying';
                            reviewActions.querySelector('[data-review-action="dismiss"]').hidden = message.state?.phase === 'retrying';
                        }
                        reviewResult.hidden = false;
                        if (message.state?.phase === 'error') {
                            reviewDisconnectProcessor();
                            if (reviewStream) { reviewStream.getTracks().forEach((track) => track.stop()); reviewStream = null; }
                            if (reviewListening) reviewListening.classList.remove('is-listening');
                            if (reviewListeningLabel) reviewListeningLabel.textContent = reviewText('مراجعة مطلوبة', 'Review needed');
                            reviewPaused = true;
                            if (reviewStart) { reviewStart.disabled = false; reviewStart.textContent = reviewText('استأنف بعد المراجعة', 'Resume after review'); }
                            if (reviewStop) reviewStop.disabled = false;
                            reviewSetStatus(reviewText('أوقفنا الالتقاط عند موضع يحتاج مراجعة محتملة. أعده أو تابعه؛ قد يخطئ النموذج.', 'Capture paused at a possible issue. Repeat it or continue; the model can be wrong.'), 'error');
                        }
                    }
                } else {
                    reviewActions.hidden = true;
                    reviewAlert.hidden = true;
                }
            } else if (message.type === 'correction_note') {
                reviewSetStatus(reviewText('ملاحظة استرشادية: ربما تكررت كلمة. راجع الآية بنفسك.', 'Advisory: a word may have been repeated. Please check the verse.'), 'info');
            } else if (message.type === 'verse_match') {
                reviewSetStatus(reviewText('تمت مطابقة الآية مع موضع القراءة.', 'The recitation matched the selected verse.'), 'ready');
            } else if (message.type === 'loading') {
                reviewSetStatus(reviewText(`جارٍ تنزيل النموذج وحفظه على جهازك: ${message.percent}%`, `Downloading and caching the on-device model: ${message.percent}%`), 'loading');
            } else if (message.type === 'loading_status') {
                reviewSetStatus(reviewText('جارٍ تجهيز أداة المراجعة…', 'Preparing the review engine…'), 'loading');
            } else if (message.type === 'ready') {
                reviewReady = true;
                if (reviewStartQueued) reviewStartCapture().catch(() => { reviewStopCapture(); reviewSetStatus(reviewText('تعذر فتح الميكروفون. تحقق من الإذن ثم حاول مجددًا.', 'Microphone access failed. Check permission and try again.'), 'error'); });
                else reviewSetStatus(reviewText('النموذج جاهز على هذا الجهاز. اضغط «ابدأ المراجعة».', 'The model is ready on this device. Press “Start review”.'), 'ready');
            } else if (message.type === 'stopped') {
                if (reviewListening) reviewListening.classList.remove('is-listening');
                if (reviewListeningLabel) reviewListeningLabel.textContent = reviewText('اكتملت المحاولة', 'Review complete');
                if (reviewActions) reviewActions.hidden = true;
                reviewSetStatus(reviewText('اكتملت هذه المحاولة. التنبيهات استرشادية؛ تحقّق من الكلمات بنفسك.', 'This review is complete. Alerts are advisory; check each word yourself.'), 'ready');
            } else if (message.type === 'error') {
                reviewReady = false; reviewStartQueued = false; reviewStopCapture();
                reviewWorker?.terminate(); reviewWorker = null;
                reviewSetStatus(reviewText(`تعذر تشغيل المراجعة: ${message.message}`, `Review could not start: ${message.message}`), 'error');
            }
        };
        const reviewEnsureWorker = () => {
            if (!reviewWorker) {
                const workerUrl = new URL('/assets/js/recitation-worker.js', window.location.origin);
                reviewWorker = new Worker(workerUrl, { type: 'module' });
                reviewWorker.addEventListener('message', (event) => reviewHandleEvent(event.data));
                reviewWorker.addEventListener('error', () => { reviewStartQueued = false; reviewStopCapture(); reviewWorker?.terminate(); reviewWorker = null; reviewReady = false; reviewSetStatus(reviewText('تعذر تحميل محرك المراجعة. تحقق من الاتصال وحاول مجددًا.', 'The review engine failed to load. Check your connection and try again.'), 'error'); });
                reviewWorker.postMessage({ type: 'init', quran: loadedVerses.map((item) => ({ surah: item.surah, ayah: item.numberInSurah, text_uthmani: item.text, surah_name: surahNames[item.surah - 1], surah_name_en: `Surah ${item.surah}` })) });
            } else if (reviewReady) return;
            reviewStartQueued = true;
            reviewSetStatus(reviewText('يتم تجهيز النموذج القرآني المجاني على جهازك. التنزيل الأول نحو 75 ميجابايت.', 'Preparing the free Quran model on your device. The first download is about 75 MB.'), 'loading');
        };
        reviewStart?.addEventListener('click', async () => {
            reviewPrimeAlertAudio();
            if (!loadedVerses.length || !Number(ayahInput.value)) { reviewSetStatus(reviewText('حدد السورة وبداية ونهاية النطاق ثم اضغط «تجهيز النطاق».', 'Choose a surah and range, then press “Prepare range”.'), 'error'); return; }
            reviewStart.disabled = true;
            try {
                if (!navigator.mediaDevices?.getUserMedia) throw new Error('microphone-unavailable');
                const AudioContextClass = window.AudioContext || window.webkitAudioContext;
                reviewContext ||= new AudioContextClass({ latencyHint: 'interactive' });
                await reviewContext.resume();
                if (reviewPaused) {
                    if (!reviewStream) reviewStream = await navigator.mediaDevices.getUserMedia({ audio: { channelCount: 1, echoCancellation: false, noiseSuppression: false, autoGainControl: false } });
                    if (reviewWorker) reviewWorker.postMessage({ type: 'correct', action: reviewResumeAction });
                    reviewPaused = false;
                    await reviewStartCapture({ resume: true });
                } else {
                    if (!reviewStream) reviewStream = await navigator.mediaDevices.getUserMedia({ audio: { channelCount: 1, echoCancellation: false, noiseSuppression: false, autoGainControl: false } });
                    reviewEnsureWorker();
                    if (reviewReady) await reviewStartCapture();
                }
            } catch (_) {
                reviewStopCapture();
                reviewSetStatus(reviewText('لم نتمكن من فتح الميكروفون. اسمح به وتأكد من استخدام HTTPS أو localhost.', 'Microphone access failed. Allow access and use HTTPS or localhost.'), 'error');
            }
        });
        reviewStop?.addEventListener('click', () => {
            if (reviewPendingLength && reviewWorker) {
                const merged = new Float32Array(reviewPendingLength); let offset = 0;
                reviewPendingChunks.forEach((chunk) => { merged.set(chunk, offset); offset += chunk.length; });
                const samples = reviewResample(merged, reviewContext.sampleRate);
                reviewWorker.postMessage({ type: 'audio', samples }, [samples.buffer]);
            }
            reviewStopCapture(); reviewStartQueued = false; reviewPaused = false; reviewResumeAction = 'continue';
            if (reviewStart) reviewStart.textContent = reviewText('ابدأ المراجعة', 'Start review');
            if (reviewWorker && reviewReady) reviewWorker.postMessage({ type: 'stop' });
            reviewSetStatus(reviewText('انتهى التسجيل. جارٍ إنهاء المراجعة…', 'Recording stopped. Finishing the review…'), 'loading');
            if (!reviewCounted && quranReader.dataset.signedIn === 'true') {
                reviewCounted = true;
                const token = document.querySelector('meta[name="csrf-token"]')?.content || '';
                const body = new URLSearchParams({ surah: surahInput.value, ayah: ayahInput.value, action: 'review' });
                fetch('/api/quran/progress', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8', 'X-CSRF-Token': token, Accept: 'application/json' }, body, credentials: 'same-origin' }).catch(() => {});
            }
        });
        reviewActions?.addEventListener('click', (event) => {
            const button = event.target.closest('[data-review-action]');
            if (!button || !reviewWorker) return;
            reviewActions.hidden = true;
            if (['retry', 'continue', 'dismiss'].includes(button.dataset.reviewAction)) {
                reviewPaused = true;
                reviewResumeAction = button.dataset.reviewAction;
                reviewAlert.hidden = true;
                reviewResult.hidden = true;
                content.querySelectorAll('.verse-word.is-possible-error, .review-mushaf-slot.is-possible-error').forEach((word) => word.classList.remove('is-possible-error'));
                reviewStart.disabled = false;
                reviewStart.textContent = button.dataset.reviewAction === 'retry' ? reviewText('أعد القراءة', 'Retry recitation') : reviewText('استأنف المراجعة', 'Resume review');
                reviewSetStatus(reviewText(button.dataset.reviewAction === 'retry' ? 'أعد قراءة الآية المحددة ثم سيكمل النموذج من نفس الموضع.' : 'اضغط استأنف للمتابعة من الموضع الحالي.', button.dataset.reviewAction === 'retry' ? 'Repeat the flagged verse, then the model will continue from this point.' : 'Press Resume to continue from this point.'), 'ready');
            }
        });
        ayahInput?.addEventListener('change', () => {
            if (!ayahEndInput) return;
            ayahEndInput.min = String(Number(ayahInput.value) || 1);
            if (Number(ayahEndInput.value) < Number(ayahInput.value)) ayahEndInput.value = ayahInput.value;
        });

        const loadReciters = async (scope = scopeValue(), selectedSurah = Number(surahInput.value)) => {
            if (!reciterInput) return;
            try {
                const recitersCacheKey = 'athar-mp3quran-reciter-catalog-ar-v1';
                let cached = null;
                try { cached = JSON.parse(localStorage.getItem(recitersCacheKey) || 'null'); } catch (_) { /* Fetch fresh catalog. */ }
                let catalog = Array.isArray(cached?.items) && Date.now() - cached.savedAt < 86400000 ? cached.items : null;
                if (!catalog) {
                    const [recitersResponse, timingResponse] = await Promise.all([
                        fetch('https://mp3quran.net/api/v3/reciters?language=ar', { headers: { Accept: 'application/json' }, signal: AbortSignal.timeout(15000) }),
                        fetch('https://mp3quran.net/api/v3/ayat_timing/reads', { headers: { Accept: 'application/json' }, signal: AbortSignal.timeout(15000) }),
                    ]);
                    if (!recitersResponse.ok) throw new Error('reciters-unavailable');
                    const [recitersPayload, timingPayload] = await Promise.all([recitersResponse.json(), timingResponse.ok ? timingResponse.json() : Promise.resolve([])]);
                    timingReads = Array.isArray(timingPayload) ? timingPayload : [];
                    const timingByFolder = new Map(timingReads.filter((entry) => typeof entry.folder_url === 'string').map((entry) => [entry.folder_url.replace(/\/$/, ''), Number(entry.id)]));
                    catalog = (Array.isArray(recitersPayload.reciters) ? recitersPayload.reciters : []).flatMap((reciter) => (Array.isArray(reciter.moshaf) ? reciter.moshaf : []).map((moshaf) => {
                        const server = typeof moshaf.server === 'string' ? moshaf.server : '';
                        const surahList = String(moshaf.surah_list || '').split(',').map(Number).filter(Number.isInteger);
                        return {
                            key: `mp3:${moshaf.id}`,
                            reciterId: Number(reciter.id),
                            name: String(reciter.name || '').trim(),
                            moshafName: String(moshaf.name || '').trim(),
                            server,
                            surahList,
                            timingReadId: timingByFolder.get(server.replace(/\/$/, '')) || null,
                        };
                    })).filter((entry) => entry.name && /^https:\/\//i.test(entry.server) && entry.surahList.length);
                    if (!catalog.length) throw new Error('reciters-shape');
                    fullSurahReciters = catalog;
                    try { localStorage.setItem(recitersCacheKey, JSON.stringify({ items: catalog, savedAt: Date.now() })); } catch (_) { /* Playback remains available without storage. */ }
                } else fullSurahReciters = catalog;
                const compatible = catalog.filter((entry) => entry.surahList.includes(selectedSurah) && (scope === 'surah' || entry.timingReadId));
                const options = compatible.map((entry) => ({ value: entry.key, label: entry.moshafName ? `${entry.name} · ${entry.moshafName}` : entry.name }));
                const previous = reciterInput.value;
                reciterInput.replaceChildren(...options.map(({ value, label }) => { const option = document.createElement('option'); option.value = value; option.textContent = label; return option; }));
                if (!options.length) {
                    const empty = document.createElement('option'); empty.value = ''; empty.textContent = 'لا يتوفر قارئ بهذا النطاق لهذه السورة'; reciterInput.append(empty);
                    if (audioStatus) audioStatus.textContent = scope === 'current' ? 'هذه السورة لا تتوفر لها توقيتات للآية المنفردة؛ اختر السورة كاملة.' : 'لا تتوفر تلاوة لهذه السورة في المصدر حاليًا.';
                    return;
                }
                reciterInput.value = options.some((option) => option.value === previous) ? previous : options[0].value;
                if (audioStatus) audioStatus.textContent = `${options.length} قراءة متاحة لهذا الاختيار.`;
            } catch (_) {
                const fallback = document.createElement('option'); fallback.value = ''; fallback.textContent = 'تعذر تحميل قائمة القراء';
                reciterInput.replaceChildren(fallback);
                if (audioStatus) audioStatus.textContent = 'تعذر الاتصال بمكتبة MP3Quran. تحقق من الاتصال ثم أعد تحميل الصفحة.';
            }
        };
        reciterInput?.addEventListener('change', () => { const wasPlaying = !audio.paused; audio.pause(); activeTrack = null; playVerse(activeIndex, wasPlaying); });
        previousAudioButton?.addEventListener('click', () => { if (activeIndex > 0) playVerse(activeIndex - 1); });
        nextAudioButton?.addEventListener('click', () => { if (activeIndex + 1 < activeVerses.length) playVerse(activeIndex + 1); });
        audio?.addEventListener('ended', () => { if (repeatInput.checked) { audio.currentTime = 0; audio.play(); } else if (scopeValue() !== 'surah' && activeIndex + 1 < activeVerses.length) playVerse(activeIndex + 1); else { const session = getMediaSession(); if (session) session.playbackState = 'none'; if (audioStatus) audioStatus.textContent = 'اكتملت التلاوة لهذا النطاق.'; } });
        audio?.addEventListener('play', () => { const session = getMediaSession(); if (session) session.playbackState = 'playing'; });
        audio?.addEventListener('pause', () => { const session = getMediaSession(); if (session && !audio.ended) session.playbackState = 'paused'; });
        audio?.addEventListener('timeupdate', () => {
            updatePosition();
            const elapsed = audio.currentTime * 1000;
            if (scopeValue() === 'current' && activeSegmentTiming && elapsed >= Number(activeSegmentTiming.end_time)) {
                if (repeatInput?.checked) setAudioPosition(Number(activeSegmentTiming.start_time) / 1000);
                else { audio.pause(); if (audioStatus) audioStatus.textContent = 'اكتمل الاستماع إلى الآية المحددة.'; }
                return;
            }
            if (!activeTimings.length) return;
            const timing = activeTimings.find((entry) => Number(entry.ayah) > 0 && elapsed >= Number(entry.start_time) && elapsed < Number(entry.end_time));
            if (!timing) return;
            const nextIndex = activeVerses.findIndex((verse) => verse.numberInSurah === Number(timing.ayah));
            if (nextIndex >= 0 && nextIndex !== activeIndex) { activeIndex = nextIndex; paintActiveVerse(); }
        });
        audio?.addEventListener('error', () => { if (audioStatus) audioStatus.textContent = 'تعذر تشغيل التلاوة. اختر قارئًا آخر أو تحقق من الاتصال.'; });
        const session = getMediaSession();
        if (session) {
            const actions = { play: () => audio?.play(), pause: () => audio?.pause(), stop: stopPlayback, nexttrack: () => playVerse(activeIndex + 1), previoustrack: () => playVerse(activeIndex - 1), seekto: ({ seekTime }) => { if (audio && Number.isFinite(seekTime)) audio.currentTime = seekTime; }, seekforward: () => { if (audio) audio.currentTime = Math.min(audio.duration || Infinity, audio.currentTime + 10); }, seekbackward: () => { if (audio) audio.currentTime = Math.max(0, audio.currentTime - 10); } };
            Object.entries(actions).forEach(([name, handler]) => { try { session.setActionHandler(name, handler); } catch (_) { /* Action is not supported by this browser. */ } });
        }
        if (['ayah', 'tafsir', 'review'].includes(quranReader.dataset.tool) && !quranReader.dataset.ayah) {
            status.dataset.state = 'info';
            status.textContent = quranReader.dataset.tool === 'review' ? 'حدد السورة وبداية ونهاية النطاق ثم اضغط تجهيز النطاق.' : 'اختر السورة ورقم الآية ثم اضغط عرض.';
        } else {
            const initialReading = () => loadReading({ mode: quranReader.dataset.mode, surah: Number(quranReader.dataset.surah), ayah: Number(quranReader.dataset.ayah) || null, ayahEnd: Number(quranReader.dataset.ayahEnd) || Number(quranReader.dataset.ayah) || null, juz: Number(quranReader.dataset.juz), hizb: Number(quranReader.dataset.hizb), page: Number(quranReader.dataset.page) });
            if (quranReader.dataset.tool === 'listen') loadReciters(scopeValue(), Number(surahInput.value)).then(initialReading);
            else initialReading();
        }
    }
})();
(() => {
    const accountNav = document.querySelector('[data-account-nav]');
    if (!accountNav) return;
    const links = [...accountNav.querySelectorAll('a[href^="#account-"]')];
    const panels = [...document.querySelectorAll('[data-account-panel]')];
    const showSection = (hash, scroll = false) => {
        const active = links.find((link) => link.hash === hash) || links[0];
        const activePanel = panels.find((panel) => `#${panel.id}` === active?.hash) || panels[0];
        links.forEach((link) => {
            if (link === active) link.setAttribute('aria-current', 'location');
            else link.removeAttribute('aria-current');
        });
        panels.forEach((panel) => { panel.hidden = panel !== activePanel; });
        active?.scrollIntoView({ block: 'nearest', inline: 'nearest' });
        if (scroll && activePanel) activePanel.scrollIntoView({ block: 'start', behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' });
    };
    showSection(window.location.hash);
    accountNav.addEventListener('click', (event) => {
        const link = event.target.closest('a[href^="#account-"]');
        if (link && accountNav.contains(link)) showSection(link.hash, true);
    });
    window.addEventListener('hashchange', () => showSection(window.location.hash, true));
})();

(() => {
    const adminNav = document.querySelector('[data-admin-nav]');
    if (!adminNav) return;
    const links = [...adminNav.querySelectorAll('a[href^="#admin-"]')];
    const panels = [...document.querySelectorAll('[data-admin-panel]')];
    const showSection = (hash, scroll = false) => {
        const active = links.find((link) => link.hash === hash) || links[0];
        const activePanel = panels.find((panel) => `#${panel.id}` === active?.hash) || panels.find((panel) => panel.id === 'admin-overview');
        links.forEach((link) => {
            if (link === active) link.setAttribute('aria-current', 'location');
            else link.removeAttribute('aria-current');
        });
        panels.forEach((panel) => { panel.hidden = panel !== activePanel; });
        active?.scrollIntoView({ block: 'nearest', inline: 'nearest' });
        if (scroll && activePanel) activePanel.scrollIntoView({ block: 'start', behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' });
    };
    showSection(window.location.hash);
    adminNav.addEventListener('click', (event) => {
        const link = event.target.closest('a[href^="#admin-"]');
        if (link && adminNav.contains(link)) showSection(link.hash, true);
    });
    window.addEventListener('hashchange', () => showSection(window.location.hash, true));
})();

(() => {
    const wireFilter = (inputSelector, rowSelector, emptySelector) => {
        const input = document.querySelector(inputSelector);
        if (!input) return;
        const rows = [...document.querySelectorAll(rowSelector)];
        const empty = document.querySelector(emptySelector);
        const filter = () => {
            const query = input.value.trim().toLocaleLowerCase();
            let visible = 0;
            rows.forEach((row) => {
                const matches = !query || (row.dataset.search || '').toLocaleLowerCase().includes(query);
                row.hidden = !matches;
                if (matches) visible += 1;
            });
            if (empty) empty.hidden = visible !== 0;
        };
        input.addEventListener('input', filter);
    };
    wireFilter('[data-admin-user-search]', '[data-admin-user]', '[data-admin-user-empty]');
    wireFilter('[data-admin-audit-search]', '[data-admin-audit]', '[data-admin-audit-empty]');
})();
