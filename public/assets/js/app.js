(() => {
    const root = document.documentElement;
    const themeButton = document.querySelector('[data-theme-toggle]');
    const themeLabel = document.querySelector('[data-theme-label]');
    const menuButton = document.querySelector('[data-menu-toggle]');
    const nav = document.querySelector('#primary-nav');

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
        }
        if (themeLabel) themeLabel.textContent = root.lang === 'en' ? englishThemeLabel : nextThemeLabel;
        const themeColor = document.querySelector('meta[name="theme-color"]');
        if (themeColor) themeColor.setAttribute('content', theme === 'dark' ? '#20221F' : '#F5F3ED');
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
                const text = document.createElement('span'); text.className = 'verse-text'; text.textContent = verse.text;
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
                verseList.querySelectorAll('li').forEach((item) => { const text = item.querySelector('.verse-text'); const reveal = item.querySelector('.verse-reveal'); text.hidden = enabled; reveal.hidden = !enabled; });
            });
            content.append(heading, memoryButton, verseList);
        };

        const loadReading = async ({ mode = modeInput.value, surah = Number(surahInput.value), ayah = Number(ayahInput.value) || null, juz = Number(juzInput.value), page = Number(pageInput.value) } = {}) => {
            const thisRequest = ++requestVersion;
            status.hidden = false; status.dataset.state = 'loading'; status.textContent = 'جارٍ تحميل الآيات…';
            content.hidden = true; content.replaceChildren(); loadedVerses = []; activeVerses = [];
            stopPlayback(); if (audioStatus) audioStatus.textContent = 'جارٍ تجهيز موضع القراءة…';
            if (tafsirSection) tafsirSection.hidden = true;
            if (tafsirFrame) { tafsirFrame.hidden = true; tafsirFrame.replaceChildren(); }
            if (tafsirButton) { tafsirButton.setAttribute('aria-expanded', 'false'); tafsirButton.textContent = 'عرض التفسير'; }
            try {
                let url;
                if (mode === 'juz') url = `https://api.alquran.cloud/v1/juz/${juz}/quran-uthmani`;
                else if (mode === 'page') url = `https://api.alquran.cloud/v1/page/${page}/quran-uthmani`;
                else url = `${baseUrl}/mushafs/1/${surah}`;
                const response = await fetch(url, { headers: { Accept: 'application/json' }, signal: AbortSignal.timeout(15000) });
                if (!response.ok) throw new Error('provider-response');
                const payload = await response.json();
                const rawVerses = mode === 'surah' ? (Array.isArray(payload) ? payload : [payload]) : payload?.data?.ayahs;
                const allVerses = (Array.isArray(rawVerses) ? rawVerses : []).map(normalizeVerse).filter(Boolean);
                if (!allVerses.length) throw new Error('provider-shape');
                if (thisRequest !== requestVersion) return;
                if (quranReader.dataset.tool === 'ayah' || quranReader.dataset.tool === 'tafsir') loadedVerses = ayah ? allVerses.filter((verse) => verse.numberInSurah === ayah) : allVerses;
                else loadedVerses = mode === 'surah' && ayah && quranReader.dataset.tool !== 'listen' ? allVerses.filter((verse) => verse.numberInSurah >= ayah) : allVerses;
                if (!loadedVerses.length) throw new Error('empty-range');
                const heading = mode === 'juz' ? `الجزء ${juz} · ${loadedVerses.length} آية` : mode === 'page' ? `صفحة ${page} · ${loadedVerses.length} آية` : `${surahNames[surah - 1]} · ${ayah ? `من الآية ${ayah}` : `${loadedVerses.length} آية`}`;
                buildReading(loadedVerses, heading);
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
                if (mode === 'surah' && ayah) content.querySelector(`#ayah-${surah}-${ayah}`)?.scrollIntoView({ block: 'center' });
                configurePlayback(false);
                if (audioStatus) audioStatus.textContent = 'الموضع جاهز. اضغط تشغيل للاستماع.';
            } catch (_) {
                if (thisRequest !== requestVersion) return;
                status.dataset.state = 'error'; status.replaceChildren();
                const message = document.createElement('span'); message.textContent = 'تعذر تحميل الآيات الآن. تحقق من الاتصال ثم حاول مجددًا.';
                const retry = document.createElement('button'); retry.className = 'button button-secondary'; retry.type = 'button'; retry.textContent = 'إعادة المحاولة'; retry.addEventListener('click', () => loadReading({ mode, surah, ayah, juz, page }), { once: true });
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
            const query = new URLSearchParams({ tool: quranReader.dataset.tool, mode: modeInput.value, surah: surahInput.value, juz: juzInput.value, page: pageInput.value });
            if (modeInput.value === 'surah' && ayahInput.value) query.set('ayah', ayahInput.value);
            history.replaceState(null, '', `/quran?${query}`);
            loadReading();
        });
        quranReader.querySelectorAll('[data-font-step]').forEach((button) => button.addEventListener('click', () => { fontSize = Math.min(2.8, Math.max(1.2, fontSize + Number(button.dataset.fontStep) * 0.2)); quranReader.style.setProperty('--reading-font-size', `${fontSize}rem`); }));

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
        if (['ayah', 'tafsir'].includes(quranReader.dataset.tool) && !quranReader.dataset.ayah) {
            status.dataset.state = 'info';
            status.textContent = 'اختر السورة ورقم الآية ثم اضغط عرض.';
        } else loadReading({ mode: quranReader.dataset.mode, surah: Number(quranReader.dataset.surah), ayah: Number(quranReader.dataset.ayah) || null, juz: Number(quranReader.dataset.juz), page: Number(quranReader.dataset.page) });
    }
})();
