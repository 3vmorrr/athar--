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
    if (quranReader && quranReader.dataset.tool !== 'search') {
        const form = quranReader.querySelector('[data-quran-form]');
        const modeInput = quranReader.querySelector('[data-reading-mode]');
        const surahInput = quranReader.querySelector('[name="surah"]');
        const ayahInput = quranReader.querySelector('[name="ayah"]');
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
        const tafsirSection = quranReader.querySelector('[data-quran-tafsir]');
        const tafsirButton = quranReader.querySelector('[data-tafsir-toggle]');
        const tafsirFrame = quranReader.querySelector('[data-tafsir-frame]');
        const reviewPanel = quranReader.querySelector('[data-recitation-review]');
        const reviewStart = quranReader.querySelector('[data-review-start]');
        const reviewStop = quranReader.querySelector('[data-review-stop]');
        const reviewStatus = quranReader.querySelector('[data-review-status]');
        const reviewResult = quranReader.querySelector('[data-review-result]');
        const reviewActions = quranReader.querySelector('[data-review-actions]');
        const reviewBeep = quranReader.querySelector('[data-review-beep]');
        const baseUrl = 'https://api.quranpedia.net/v1';
        const surahNames = Array.from(surahInput.options, (option) => option.textContent.replace(/^\d+\.\s*/, '').trim());
        let fontSize = 1.8;
        let loadedVerses = [];
        let activeVerses = [];
        let activeIndex = 0;
        let activeTrack = null;
        let requestVersion = 0;
        let fullSurahReciters = [];

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
            mediaMetadata(verse);
            if (audioStatus) audioStatus.textContent = scopeValue() === 'surah' ? `الآن: سورة ${surahNames[verse.surah - 1]} كاملة` : `الآن: ${surahNames[verse.surah - 1]} · الآية ${verse.numberInSurah}`;
        };

        const playVerse = async (index, shouldPlay = true) => {
            if (!audio || !activeVerses.length) return;
            activeIndex = Math.max(0, Math.min(index, activeVerses.length - 1));
            const verse = activeVerses[activeIndex];
            paintActiveVerse();
            if (content.querySelector(`#ayah-${verse.surah}-${verse.numberInSurah}`)) content.querySelector(`#ayah-${verse.surah}-${verse.numberInSurah}`).scrollIntoView({ block: 'nearest', behavior: 'smooth' });
            const identifier = reciterInput.value;
            const qari = fullSurahReciters.find((item) => `qa:${item.id}` === identifier);
            const url = scopeValue() === 'surah'
                ? (qari ? `https://download.quranicaudio.com/quran/${qari.relative_path}${String(Number(surahInput.value)).padStart(3, '0')}.mp3` : `https://cdn.islamic.network/quran/audio-surah/128/ar.alafasy/${Number(surahInput.value)}.mp3`)
                : `https://cdn.islamic.network/quran/audio/128/${encodeURIComponent(identifier.replace(/^verse:/, ''))}/${verse.globalNumber}.mp3`;
            if (activeTrack !== url) { audio.pause(); audio.src = url; audio.load(); activeTrack = url; }
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
                        const word = document.createElement('span'); word.className = 'verse-word'; word.dataset.wordIndex = String(index); word.textContent = part;
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
            if (quranReader.dataset.tool !== 'daily') content.append(heading, memoryButton, verseList);
            else content.append(heading, verseList);
            if (quranReader.dataset.review === 'true') memoryButton.click();
        };

        const loadReading = async ({ mode = modeInput.value, surah = Number(surahInput.value), ayah = Number(ayahInput.value) || null, juz = Number(juzInput.value), hizb = Number(hizbInput?.value || quranReader.dataset.hizb), page = Number(pageInput.value) } = {}) => {
            const thisRequest = ++requestVersion;
            status.hidden = false; status.dataset.state = 'loading'; status.textContent = 'جارٍ تحميل الآيات…';
            content.hidden = true; content.replaceChildren(); loadedVerses = []; activeVerses = [];
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
                if (thisRequest !== requestVersion) return;
                if (quranReader.dataset.tool === 'daily') loadedVerses = allVerses;
                else if (['ayah', 'tafsir', 'review'].includes(quranReader.dataset.tool)) loadedVerses = ayah ? allVerses.filter((verse) => verse.numberInSurah === ayah) : allVerses;
                else loadedVerses = mode === 'surah' && ayah && quranReader.dataset.tool !== 'listen' ? allVerses.filter((verse) => verse.numberInSurah >= ayah) : allVerses;
                if (!loadedVerses.length) throw new Error('empty-range');
                const currentVerse = loadedVerses[0];
                const heading = quranReader.dataset.tool === 'daily' ? `آية اليوم · سورة ${surahNames[currentVerse.surah - 1]} · الآية ${currentVerse.numberInSurah}` : mode === 'juz' ? `الجزء ${juz} · ${loadedVerses.length} آية` : mode === 'hizb' ? `الحزب ${hizb} · ${loadedVerses.length} آية` : mode === 'page' ? `صفحة ${page} · ${loadedVerses.length} آية` : `${surahNames[surah - 1]} · ${ayah ? `من الآية ${ayah}` : `${loadedVerses.length} آية`}`;
                buildReading(loadedVerses, heading);
                if (quranReader.dataset.tool === 'daily') {
                    const actions = quranReader.querySelector('[data-daily-verse-actions]');
                    if (actions) {
                        actions.replaceChildren();
                        [['/quran?tool=listen', 'استمع إلى الآية'], ['/quran?tool=tafsir', 'افتح التفسير']].forEach(([href, label]) => { const link = document.createElement('a'); link.className = 'button button-secondary'; link.textContent = label; link.href = `${href}&surah=${currentVerse.surah}&ayah=${currentVerse.numberInSurah}`; actions.append(link); });
                    }
                }
                content.hidden = false;
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
                if (reviewPanel) reviewPanel.hidden = quranReader.dataset.tool !== 'review' || !ayah;
                if (mode === 'surah' && ayah) content.querySelector(`#ayah-${surah}-${ayah}`)?.scrollIntoView({ block: 'center' });
                configurePlayback(false);
                if (audioStatus) audioStatus.textContent = 'الموضع جاهز. اضغط تشغيل للاستماع.';
            } catch (_) {
                if (thisRequest !== requestVersion) return;
                status.dataset.state = 'error'; status.replaceChildren();
                const message = document.createElement('span'); message.textContent = 'تعذر تحميل الآيات الآن. تحقق من الاتصال ثم حاول مجددًا.';
                const retry = document.createElement('button'); retry.className = 'button button-secondary'; retry.type = 'button'; retry.textContent = 'إعادة المحاولة'; retry.addEventListener('click', () => loadReading({ mode, surah, ayah, juz, hizb, page }), { once: true });
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
                ? 'اختر من مكتبة قراء السور الكاملة على QuranicAudio.'
                : 'الآية المنفردة متاحة من قراء Al Quran Cloud؛ اختر السورة كاملة للوصول إلى المكتبة الأوسع.';
        };
        scopeInputs.forEach((input) => input.addEventListener('change', () => { updateAudioScope(); loadReciters(scopeValue()).then(() => configurePlayback(false)); }));
        updateAudioScope();
        form?.addEventListener('submit', (event) => {
            event.preventDefault();
            if (modeInput.value === 'page' && (Number(pageInput.value) < 1 || Number(pageInput.value) > 604)) { pageInput.setCustomValidity('رقم الصفحة يجب أن يكون بين 1 و604'); pageInput.reportValidity(); return; }
            pageInput.setCustomValidity('');
            const query = new URLSearchParams({ tool: quranReader.dataset.tool, mode: modeInput.value, surah: surahInput.value, juz: juzInput.value, hizb: hizbInput?.value || '1', page: pageInput.value });
            if (modeInput.value === 'surah' && ayahInput.value) query.set('ayah', ayahInput.value);
            history.replaceState(null, '', `/quran?${query}`);
            if (quranReader.dataset.tool === 'review' && reviewWorker) {
                reviewStopCapture(); reviewWorker.terminate(); reviewWorker = null; reviewReady = false; reviewStartQueued = false;
                if (reviewResult) reviewResult.hidden = true;
                reviewSetStatus(reviewText('يتم تحميل الآية المختارة.', 'Loading the selected verse.'));
            }
            loadReading();
        });
        quranReader.querySelectorAll('[data-font-step]').forEach((button) => button.addEventListener('click', () => { fontSize = Math.min(2.8, Math.max(1.2, fontSize + Number(button.dataset.fontStep) * 0.2)); quranReader.style.setProperty('--reading-font-size', `${fontSize}rem`); }));

        let reviewWorker = null;
        let reviewStream = null;
        let reviewContext = null;
        let reviewProcessor = null;
        let reviewPendingChunks = [];
        let reviewPendingLength = 0;
        let reviewReady = false;
        let reviewStartQueued = false;
        let reviewIssueKey = '';
        let reviewCounted = false;
        const reviewText = (ar, en) => root.lang === 'en' ? en : ar;
        const reviewSetStatus = (message, state = 'info') => { if (reviewStatus) { reviewStatus.textContent = message; reviewStatus.dataset.state = state; } };
        const reviewStopCapture = () => {
            if (reviewProcessor) { reviewProcessor.onaudioprocess = null; reviewProcessor.disconnect(); reviewProcessor = null; }
            if (reviewStream) { reviewStream.getTracks().forEach((track) => track.stop()); reviewStream = null; }
            reviewPendingChunks = []; reviewPendingLength = 0;
            if (reviewStart) reviewStart.disabled = !loadedVerses.length;
            if (reviewStop) reviewStop.disabled = true;
        };
        const reviewResample = (input, fromRate) => {
            const ratio = fromRate / 16000;
            const length = Math.max(1, Math.floor(input.length / ratio));
            const output = new Float32Array(length);
            for (let i = 0; i < length; i++) {
                const at = i * ratio; const left = Math.floor(at); const fraction = at - left;
                output[i] = input[left] * (1 - fraction) + (input[Math.min(left + 1, input.length - 1)] || 0) * fraction;
            }
            return output;
        };
        const reviewStartCapture = async () => {
            if (!reviewContext || !reviewStream || !reviewReady) return;
            const expected = loadedVerses.find((verse) => verse.surah === Number(surahInput.value) && verse.numberInSurah === Number(ayahInput.value));
            if (!expected) { reviewSetStatus(reviewText('اختر آية صالحة أولًا.', 'Choose a valid verse first.'), 'error'); return; }
            reviewWorker.postMessage({ type: 'start', passage: { surah: expected.surah, ayah: expected.numberInSurah } });
            const source = reviewContext.createMediaStreamSource(reviewStream);
            reviewProcessor = reviewContext.createScriptProcessor(4096, 1, 1);
            const muted = reviewContext.createGain(); muted.gain.value = 0;
            reviewProcessor.onaudioprocess = (event) => {
                const data = event.inputBuffer.getChannelData(0);
                reviewPendingChunks.push(new Float32Array(data)); reviewPendingLength += data.length;
                if (reviewPendingLength < reviewContext.sampleRate * 0.42) return;
                const merged = new Float32Array(reviewPendingLength); let offset = 0;
                reviewPendingChunks.forEach((chunk) => { merged.set(chunk, offset); offset += chunk.length; });
                reviewPendingChunks = []; reviewPendingLength = 0;
                const samples = reviewResample(merged, reviewContext.sampleRate);
                reviewWorker.postMessage({ type: 'audio', samples }, [samples.buffer]);
            };
            source.connect(reviewProcessor); reviewProcessor.connect(muted); muted.connect(reviewContext.destination);
            reviewStartQueued = false; reviewStart.disabled = true; reviewStop.disabled = false; reviewCounted = false; reviewIssueKey = '';
            reviewResult.hidden = true; reviewSetStatus(reviewText('نستمع الآن. اقرأ الآية الظاهرة بوضوح وبوتيرة معتدلة.', 'Listening. Recite the displayed verse clearly at a steady pace.'), 'ready');
        };
        const reviewBeepOnce = () => {
            if (!reviewBeep?.checked || !reviewContext) return;
            const oscillator = reviewContext.createOscillator(); const gain = reviewContext.createGain();
            oscillator.frequency.value = 660; gain.gain.setValueAtTime(0.0001, reviewContext.currentTime);
            gain.gain.exponentialRampToValueAtTime(0.12, reviewContext.currentTime + 0.02);
            gain.gain.exponentialRampToValueAtTime(0.0001, reviewContext.currentTime + 0.18);
            oscillator.connect(gain); gain.connect(reviewContext.destination); oscillator.start(); oscillator.stop(reviewContext.currentTime + 0.2);
        };
        const reviewHandleEvent = (message) => {
            if (message.type === 'correction') {
                const issue = message.state?.issue;
            content.querySelectorAll('.verse-word.is-possible-error, .verse-word.is-reviewed-correct').forEach((word) => word.classList.remove('is-possible-error', 'is-reviewed-correct'));
                if (issue && ['possible_omission', 'possible_substitution', 'possible_vowel', 'possible_repetition', 'possible_skipped_ayah', 'unclear_ayah'].includes(issue.kind)) {
                    const targetVerse = content.querySelector(`#ayah-${issue.surah}-${issue.ayah}`);
                    const words = targetVerse?.querySelectorAll('.verse-word') || [];
                    const wholeAyah = ['possible_skipped_ayah', 'unclear_ayah'].includes(issue.kind);
                    const word = wholeAyah ? null : words[issue.word];
                    if (targetVerse && (word || wholeAyah)) {
                        const corrected = message.state?.phase === 'corrected';
                        const markedWords = wholeAyah ? words : [word];
                        markedWords.forEach((item) => item.classList.add(corrected ? 'is-reviewed-correct' : 'is-possible-error'));
                        if (corrected) {
                            reviewSetStatus(reviewText('تم رصد تحسن في إعادة المحاولة.', 'The retry appears improved.'), 'ready');
                            reviewResult.textContent = reviewText('تحسّنت المحاولة بحسب النموذج. تظل النتيجة استرشادية.', 'The model detected an improved retry. This result is still advisory.');
                            reviewActions.hidden = false;
                            reviewActions.querySelector('[data-review-action="retry"]').hidden = true;
                            reviewActions.querySelector('[data-review-action="continue"]').hidden = false;
                            reviewActions.querySelector('[data-review-action="dismiss"]').hidden = true;
                        } else {
                            const issueKey = `${issue.surah}:${issue.ayah}:${issue.word}:${issue.kind}`;
                            if (issueKey !== reviewIssueKey) { reviewIssueKey = issueKey; reviewBeepOnce(); }
                            reviewResult.textContent = wholeAyah
                                ? reviewText('لم يتمكن النموذج من متابعة الآية بثقة؛ راجعها كاملة بنفسك.', 'The model could not confidently follow this verse; please check the whole verse yourself.')
                                : reviewText(`تنبيه: قد تحتاج كلمة «${word.textContent}» إلى مراجعة. قد يخطئ النموذج؛ استمع إلى تلاوتك وتحقق بنفسك.`, `Possible issue near “${word.textContent}”. The model can be wrong; listen and check the word yourself.`);
                            reviewActions.hidden = message.state?.phase !== 'error' && message.state?.phase !== 'retrying';
                            reviewActions.querySelector('[data-review-action="retry"]').hidden = message.state?.phase === 'retrying';
                            reviewActions.querySelector('[data-review-action="continue"]').hidden = message.state?.phase === 'retrying';
                            reviewActions.querySelector('[data-review-action="dismiss"]').hidden = message.state?.phase === 'retrying';
                        }
                        reviewResult.hidden = false;
                    }
                } else reviewActions.hidden = true;
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
                if (reviewStartQueued) reviewStartCapture();
                else reviewSetStatus(reviewText('النموذج جاهز على هذا الجهاز. اضغط «ابدأ الاستماع».', 'The model is ready on this device. Press “Start listening”.'), 'ready');
            } else if (message.type === 'stopped') {
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
                reviewWorker = new Worker('/assets/js/recitation-worker.js', { type: 'module' });
                reviewWorker.addEventListener('message', (event) => reviewHandleEvent(event.data));
                reviewWorker.addEventListener('error', () => { reviewStartQueued = false; reviewStopCapture(); reviewWorker?.terminate(); reviewWorker = null; reviewReady = false; reviewSetStatus(reviewText('تعذر تحميل محرك المراجعة. تحقق من الاتصال وحاول مجددًا.', 'The review engine failed to load. Check your connection and try again.'), 'error'); });
                reviewWorker.postMessage({ type: 'init', quran: loadedVerses.map((item) => ({ surah: item.surah, ayah: item.numberInSurah, text_uthmani: item.text, surah_name: surahNames[item.surah - 1], surah_name_en: `Surah ${item.surah}` })) });
            } else if (reviewReady) return;
            reviewStartQueued = true;
            reviewSetStatus(reviewText('يتم تجهيز النموذج القرآني المجاني على جهازك. التنزيل الأول نحو 75 ميجابايت.', 'Preparing the free Quran model on your device. The first download is about 75 MB.'), 'loading');
        };
        reviewStart?.addEventListener('click', async () => {
            if (!loadedVerses.length || !Number(ayahInput.value)) { reviewSetStatus(reviewText('اختر السورة والآية ثم اضغط «اختيار الآية».', 'Choose a surah and verse, then press “Select verse”.'), 'error'); return; }
            reviewStart.disabled = true;
            try {
                if (!navigator.mediaDevices?.getUserMedia) throw new Error('microphone-unavailable');
                reviewStream = await navigator.mediaDevices.getUserMedia({ audio: { channelCount: 1, echoCancellation: false, noiseSuppression: false, autoGainControl: false } });
                const AudioContextClass = window.AudioContext || window.webkitAudioContext;
                reviewContext ||= new AudioContextClass();
                await reviewContext.resume(); reviewEnsureWorker();
                if (reviewReady) reviewStartCapture();
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
            reviewStopCapture(); reviewStartQueued = false;
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
            reviewWorker.postMessage({ type: 'correct', action: button.dataset.reviewAction });
            if (button.dataset.reviewAction === 'retry') reviewSetStatus(reviewText('أعد الآية كاملة؛ ما زال الميكروفون يعمل.', 'Repeat the full verse; the microphone is still on.'), 'ready');
            if (['continue', 'dismiss'].includes(button.dataset.reviewAction)) {
                reviewActions.hidden = true;
                reviewResult.hidden = true;
                content.querySelectorAll('.verse-word.is-possible-error, .verse-word.is-reviewed-correct').forEach((word) => word.classList.remove('is-possible-error', 'is-reviewed-correct'));
            }
        });

        const loadReciters = async (scope = scopeValue()) => {
            if (!reciterInput) return;
            try {
                let options;
                if (scope === 'surah') {
                    const cached = JSON.parse(localStorage.getItem('athar-full-surah-reciters') || 'null');
                    fullSurahReciters = Array.isArray(cached?.items) && Date.now() - cached.savedAt < 86400000 ? cached.items : null;
                    if (!fullSurahReciters) {
                        const response = await fetch('https://quranicaudio.com/api/qaris', { headers: { Accept: 'application/json' }, signal: AbortSignal.timeout(12000) });
                        if (!response.ok) throw new Error('reciters-unavailable');
                        const payload = await response.json();
                        fullSurahReciters = payload.filter((item) => Number.isInteger(Number(item.id)) && typeof item.relative_path === 'string' && /^[a-z0-9_-]+(?:\/[a-z0-9_-]+)*\/$/i.test(item.relative_path))
                            .map(({ id, name, arabic_name, relative_path }) => ({ id: Number(id), name: (arabic_name || name || '').trim(), relative_path }))
                            .filter((item) => item.name);
                        if (!fullSurahReciters.length) throw new Error('reciters-shape');
                        localStorage.setItem('athar-full-surah-reciters', JSON.stringify({ items: fullSurahReciters, savedAt: Date.now() }));
                    }
                    options = fullSurahReciters.map((reciter) => ({ value: `qa:${reciter.id}`, label: reciter.name }));
                } else {
                    const cached = JSON.parse(localStorage.getItem('athar-quran-reciters') || 'null');
                    verseReciters = Array.isArray(cached?.items) && Date.now() - cached.savedAt < 86400000 ? cached.items : null;
                    if (!verseReciters) {
                        const response = await fetch('https://api.alquran.cloud/v1/edition/format/audio', { signal: AbortSignal.timeout(10000) });
                        if (!response.ok) throw new Error('reciters-unavailable');
                        const payload = await response.json();
                        verseReciters = payload.data.filter((edition) => edition.language === 'ar' && edition.type === 'versebyverse' && /^ar\.[a-z0-9-]+$/.test(edition.identifier) && !/-\d+$/.test(edition.identifier)).map(({ identifier, name }) => ({ identifier, name }));
                        localStorage.setItem('athar-quran-reciters', JSON.stringify({ items: verseReciters, savedAt: Date.now() }));
                    }
                    options = verseReciters.map((reciter) => ({ value: `verse:${reciter.identifier}`, label: reciter.name }));
                }
                const current = reciterInput.value;
                reciterInput.replaceChildren(...options.map(({ value, label }) => { const option = document.createElement('option'); option.value = value; option.textContent = label; return option; }));
                if (options.some((option) => option.value === current)) reciterInput.value = current;
                mediaMetadata(activeVerses[activeIndex]);
                if (loadedVerses.length) configurePlayback(false);
            } catch (_) {
                const fallback = document.createElement('option');
                fallback.value = 'verse:ar.alafasy'; fallback.textContent = 'مشاري راشد العفاسي';
                reciterInput.replaceChildren(fallback);
            }
        };
        reciterInput?.addEventListener('change', () => { const wasPlaying = !audio.paused; activeTrack = null; playVerse(activeIndex, wasPlaying); });
        previousAudioButton?.addEventListener('click', () => { if (activeIndex > 0) playVerse(activeIndex - 1); });
        nextAudioButton?.addEventListener('click', () => { if (activeIndex + 1 < activeVerses.length) playVerse(activeIndex + 1); });
        audio?.addEventListener('ended', () => { if (repeatInput.checked) { audio.currentTime = 0; audio.play(); } else if (scopeValue() !== 'surah' && activeIndex + 1 < activeVerses.length) playVerse(activeIndex + 1); else { const session = getMediaSession(); if (session) session.playbackState = 'none'; if (audioStatus) audioStatus.textContent = 'اكتملت التلاوة لهذا النطاق.'; } });
        audio?.addEventListener('play', () => { const session = getMediaSession(); if (session) session.playbackState = 'playing'; });
        audio?.addEventListener('pause', () => { const session = getMediaSession(); if (session && !audio.ended) session.playbackState = 'paused'; });
        audio?.addEventListener('timeupdate', updatePosition);
        audio?.addEventListener('error', () => { if (audioStatus) audioStatus.textContent = 'تعذر تشغيل التلاوة. اختر قارئًا آخر أو تحقق من الاتصال.'; });
        const session = getMediaSession();
        if (session) {
            const actions = { play: () => audio?.play(), pause: () => audio?.pause(), stop: stopPlayback, nexttrack: () => playVerse(activeIndex + 1), previoustrack: () => playVerse(activeIndex - 1), seekto: ({ seekTime }) => { if (audio && Number.isFinite(seekTime)) audio.currentTime = seekTime; }, seekforward: () => { if (audio) audio.currentTime = Math.min(audio.duration || Infinity, audio.currentTime + 10); }, seekbackward: () => { if (audio) audio.currentTime = Math.max(0, audio.currentTime - 10); } };
            Object.entries(actions).forEach(([name, handler]) => { try { session.setActionHandler(name, handler); } catch (_) { /* Action is not supported by this browser. */ } });
        }
        if (quranReader.dataset.tool === 'listen') loadReciters(scopeValue());
        if (['ayah', 'tafsir', 'review'].includes(quranReader.dataset.tool) && !quranReader.dataset.ayah) {
            status.dataset.state = 'info';
            status.textContent = quranReader.dataset.tool === 'review' ? 'اختر السورة ورقم الآية ثم اضغط اختيار الآية.' : 'اختر السورة ورقم الآية ثم اضغط عرض.';
        } else loadReading({ mode: quranReader.dataset.mode, surah: Number(quranReader.dataset.surah), ayah: Number(quranReader.dataset.ayah) || null, juz: Number(quranReader.dataset.juz), hizb: Number(quranReader.dataset.hizb), page: Number(quranReader.dataset.page) });
    }
})();
