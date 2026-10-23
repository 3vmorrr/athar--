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
        if (themeButton) themeButton.setAttribute('aria-pressed', String(theme === 'dark'));
        if (themeLabel) themeLabel.textContent = theme === 'dark' ? 'الوضع الفاتح' : 'الوضع الداكن';
        const themeColor = document.querySelector('meta[name="theme-color"]');
        if (themeColor) themeColor.setAttribute('content', theme === 'dark' ? '#20221F' : '#F5F3ED');
    };

    setTheme(readSavedTheme());

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
        if (event.target instanceof HTMLAnchorElement && window.matchMedia('(max-width: 700px)').matches) {
            menuButton?.setAttribute('aria-expanded', 'false');
            menuButton?.setAttribute('aria-label', 'فتح القائمة');
            nav.setAttribute('data-open', 'false');
        }
    });

    const quranReader = document.querySelector('[data-quran-reader]');
    if (quranReader) {
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
        const scopeInput = quranReader.querySelector('[data-audio-scope]');
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
            session.metadata = new MediaMetadata({ title: `${surahName} · الآية ${verse.numberInSurah}`, artist: reciterName, album: 'القرآن الكريم · أثر' });
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
            if (previousAudioButton) previousAudioButton.disabled = activeIndex === 0;
            if (nextAudioButton) nextAudioButton.disabled = activeIndex >= activeVerses.length - 1;
            content.querySelector(`#ayah-${verse.surah}-${verse.numberInSurah}`)?.classList.add('is-playing');
            mediaMetadata(verse);
            if (audioStatus) audioStatus.textContent = `الآن: ${surahNames[verse.surah - 1]} · الآية ${verse.numberInSurah}`;
        };

        const playVerse = async (index, shouldPlay = true) => {
            if (!audio || !activeVerses.length) return;
            activeIndex = Math.max(0, Math.min(index, activeVerses.length - 1));
            const verse = activeVerses[activeIndex];
            paintActiveVerse();
            if (content.querySelector(`#ayah-${verse.surah}-${verse.numberInSurah}`)) content.querySelector(`#ayah-${verse.surah}-${verse.numberInSurah}`).scrollIntoView({ block: 'nearest', behavior: 'smooth' });
            const identifier = reciterInput.value;
            const url = `https://cdn.islamic.network/quran/audio/128/${encodeURIComponent(identifier)}/${verse.globalNumber}.mp3`;
            if (activeTrack !== url) { audio.pause(); audio.src = url; audio.load(); activeTrack = url; }
            if (!shouldPlay) return;
            try { await audio.play(); }
            catch (_) { if (audioStatus) audioStatus.textContent = 'اضغط تشغيل من مشغل الصوت لبدء التلاوة.'; }
        };

        const configurePlayback = (shouldPlay = false) => {
            const selected = Number(ayahInput.value) || null;
            const current = loadedVerses.findIndex((verse) => verse.surah === Number(surahInput.value) && verse.numberInSurah === selected);
            const mode = scopeInput.value;
            const start = current >= 0 ? current : 0;
            if (mode === 'current') activeVerses = loadedVerses.slice(start, start + 1);
            else if (mode === 'surah' && modeInput.value === 'surah') activeVerses = loadedVerses.filter((verse) => verse.surah === Number(surahInput.value)).slice(start);
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
                loadedVerses = mode === 'surah' && ayah ? allVerses.filter((verse) => verse.numberInSurah >= ayah) : allVerses;
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
                if (audioWrap) audioWrap.hidden = false;
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
            const surahScope = scopeInput.querySelector('option[value="surah"]');
            surahScope.disabled = modeInput.value !== 'surah';
            if (surahScope.disabled && scopeInput.value === 'surah') scopeInput.value = 'reader-list';
        };
        modeInput.addEventListener('change', updateModeControls); updateModeControls();
        form?.addEventListener('submit', (event) => {
            event.preventDefault();
            if (modeInput.value === 'page' && (Number(pageInput.value) < 1 || Number(pageInput.value) > 604)) { pageInput.setCustomValidity('رقم الصفحة يجب أن يكون بين 1 و604'); pageInput.reportValidity(); return; }
            pageInput.setCustomValidity('');
            const query = new URLSearchParams({ mode: modeInput.value, surah: surahInput.value, juz: juzInput.value, page: pageInput.value });
            if (modeInput.value === 'surah' && ayahInput.value) query.set('ayah', ayahInput.value);
            history.replaceState(null, '', `/quran?${query}`);
            loadReading();
        });
        quranReader.querySelectorAll('[data-font-step]').forEach((button) => button.addEventListener('click', () => { fontSize = Math.min(2.8, Math.max(1.2, fontSize + Number(button.dataset.fontStep) * 0.2)); quranReader.style.setProperty('--reading-font-size', `${fontSize}rem`); }));

        const loadReciters = async () => {
            try {
                const cached = JSON.parse(localStorage.getItem('athar-quran-reciters') || 'null');
                let reciters = Array.isArray(cached?.items) && Date.now() - cached.savedAt < 86400000 ? cached.items : null;
                if (!reciters) {
                    const response = await fetch('https://api.alquran.cloud/v1/edition/format/audio', { signal: AbortSignal.timeout(10000) });
                    if (!response.ok) throw new Error('reciters-unavailable');
                    const payload = await response.json();
                    reciters = payload.data.filter((edition) => edition.language === 'ar' && edition.type === 'versebyverse' && /^ar\.[a-z0-9-]+$/.test(edition.identifier) && !/-\d+$/.test(edition.identifier)).map(({ identifier, name }) => ({ identifier, name }));
                }
                const uniqueReciters = new Map(reciters.filter((reciter) => /^ar\.[a-z0-9-]+$/.test(reciter.identifier) && !/-\d+$/.test(reciter.identifier)).map((reciter) => [reciter.identifier, reciter]));
                reciters = Array.from(uniqueReciters.values());
                localStorage.setItem('athar-quran-reciters', JSON.stringify({ items: reciters, savedAt: Date.now() }));
                const current = reciterInput.value;
                reciterInput.replaceChildren(...reciters.map(({ identifier, name }) => { const option = document.createElement('option'); option.value = identifier; option.textContent = name; return option; }));
                if (reciters.some((reciter) => reciter.identifier === current)) reciterInput.value = current;
                mediaMetadata(activeVerses[activeIndex]);
            } catch (_) { /* Keep the bundled Alafasy option available offline. */ }
        };
        reciterInput.addEventListener('change', () => { const wasPlaying = !audio.paused; activeTrack = null; playVerse(activeIndex, wasPlaying); });
        scopeInput.addEventListener('change', () => configurePlayback(false));
        previousAudioButton?.addEventListener('click', () => { if (activeIndex > 0) playVerse(activeIndex - 1); });
        nextAudioButton?.addEventListener('click', () => { if (activeIndex + 1 < activeVerses.length) playVerse(activeIndex + 1); });
        audio?.addEventListener('ended', () => { if (repeatInput.checked) { audio.currentTime = 0; audio.play(); } else if (activeIndex + 1 < activeVerses.length) playVerse(activeIndex + 1); else { const session = getMediaSession(); if (session) session.playbackState = 'none'; if (audioStatus) audioStatus.textContent = 'اكتملت التلاوة لهذا النطاق.'; } });
        audio?.addEventListener('play', () => { const session = getMediaSession(); if (session) session.playbackState = 'playing'; });
        audio?.addEventListener('pause', () => { const session = getMediaSession(); if (session && !audio.ended) session.playbackState = 'paused'; });
        audio?.addEventListener('timeupdate', updatePosition);
        audio?.addEventListener('error', () => { if (audioStatus) audioStatus.textContent = 'تعذر تشغيل التلاوة. اختر قارئًا آخر أو تحقق من الاتصال.'; });
        const session = getMediaSession();
        if (session) {
            const actions = { play: () => audio?.play(), pause: () => audio?.pause(), stop: stopPlayback, nexttrack: () => playVerse(activeIndex + 1), previoustrack: () => playVerse(activeIndex - 1), seekto: ({ seekTime }) => { if (audio && Number.isFinite(seekTime)) audio.currentTime = seekTime; }, seekforward: () => { if (audio) audio.currentTime = Math.min(audio.duration || Infinity, audio.currentTime + 10); }, seekbackward: () => { if (audio) audio.currentTime = Math.max(0, audio.currentTime - 10); } };
            Object.entries(actions).forEach(([name, handler]) => { try { session.setActionHandler(name, handler); } catch (_) { /* Action is not supported by this browser. */ } });
        }
        loadReciters();
        loadReading({ mode: quranReader.dataset.mode, surah: Number(quranReader.dataset.surah), ayah: Number(quranReader.dataset.ayah) || null, juz: Number(quranReader.dataset.juz), page: Number(quranReader.dataset.page) });
    }
})();
