(() => {
    const root = document.documentElement;
    const themeButton = document.querySelector('[data-theme-toggle]');
    const themeLabel = document.querySelector('[data-theme-label]');
    const menuButton = document.querySelector('[data-menu-toggle]');
    const nav = document.querySelector('#primary-nav');

    const readSavedTheme = () => {
        try {
            const saved = window.localStorage.getItem('athar-theme');
            if (saved === 'light' || saved === 'dark') return saved;
        } catch (_) {
            // Storage may be disabled; the site still works with the system preference.
        }
        return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
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
        const surahInput = quranReader.querySelector('[name="surah"]');
        const ayahInput = quranReader.querySelector('[name="ayah"]');
        const status = quranReader.querySelector('[data-quran-status]');
        const content = quranReader.querySelector('[data-quran-content]');
        const audioWrap = quranReader.querySelector('[data-quran-audio]');
        const audio = audioWrap?.querySelector('audio');
        const audioStatus = quranReader.querySelector('[data-audio-status]');
        const tafsirSection = quranReader.querySelector('[data-quran-tafsir]');
        const tafsirButton = quranReader.querySelector('[data-tafsir-toggle]');
        const tafsirFrame = quranReader.querySelector('[data-tafsir-frame]');
        const baseUrl = 'https://api.quranpedia.net/v1';
        let fontSize = 1.8;

        const saveAyah = async (surah, ayah, action, button) => {
            const token = document.querySelector('meta[name="csrf-token"]')?.content || '';
            const body = new URLSearchParams({ surah: String(surah), ayah: String(ayah), action });
            button.disabled = true;
            try {
                const response = await fetch('/api/quran/progress', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8', 'X-CSRF-Token': token, Accept: 'application/json' },
                    body,
                    credentials: 'same-origin',
                });
                if (!response.ok) throw new Error('save-failed');
                button.dataset.saved = 'true';
                button.textContent = action === 'bookmark' ? 'حُفظت' : 'تم';
                button.setAttribute('aria-label', action === 'bookmark' ? `تم حفظ الآية ${ayah}` : `تم حفظ موضع القراءة عند الآية ${ayah}`);
            } catch (_) {
                button.textContent = 'أعد المحاولة';
                button.disabled = false;
            }
        };

        const loadSurah = async (surah, ayah = null) => {
            status.hidden = false;
            status.dataset.state = 'loading';
            status.textContent = 'جارٍ تحميل الآيات…';
            content.hidden = true;
            content.replaceChildren();
            if (audio) { audio.pause(); audio.removeAttribute('src'); audio.load(); }
            if (audioWrap) audioWrap.hidden = true;
            if (audioStatus) audioStatus.textContent = '';
            if (tafsirSection) tafsirSection.hidden = true;
            if (tafsirFrame) { tafsirFrame.hidden = true; tafsirFrame.replaceChildren(); }
            if (tafsirButton) { tafsirButton.setAttribute('aria-expanded', 'false'); tafsirButton.textContent = 'عرض التفسير'; }
            try {
                const endpoint = ayah
                    ? `${baseUrl}/mushafs/1/${surah}/${ayah}`
                    : `${baseUrl}/mushafs/1/${surah}`;
                const response = await fetch(endpoint, { headers: { Accept: 'application/json' } });
                if (!response.ok) throw new Error('provider-response');
                const payload = await response.json();
                const verses = Array.isArray(payload) ? payload : [payload];
                if (!verses.length || verses.some((verse) => typeof verse.text !== 'string' || !Number.isInteger(Number(verse.number)))) {
                    throw new Error('provider-shape');
                }

                const heading = document.createElement('h2');
                heading.textContent = `${surahInput.selectedOptions[0].textContent.trim()} · ${ayah ? `الآية ${ayah}` : `${verses.length} آية`}`;
                const verseList = document.createElement('ol');
                verseList.className = 'verse-list';
                verses.forEach((verse, index) => {
                    const ayahNumber = Number(verse.numberInSurah) || (ayah ? Number(ayah) : index + 1);
                    const item = document.createElement('li');
                    item.id = `ayah-${ayahNumber}`;
                    const text = document.createElement('span');
                    text.className = 'verse-text';
                    text.textContent = verse.text;
                    const marker = document.createElement('span');
                    marker.className = 'verse-number';
                    marker.setAttribute('aria-label', `الآية ${ayahNumber}`);
                    marker.textContent = String(ayahNumber);
                    item.append(text, marker);
                    if (quranReader.dataset.signedIn === 'true') {
                        const actions = document.createElement('span');
                        actions.className = 'ayah-actions';
                        actions.setAttribute('aria-label', `أدوات الآية ${ayahNumber}`);
                        const bookmark = document.createElement('button');
                        bookmark.type = 'button';
                        bookmark.textContent = 'حفظ';
                        bookmark.setAttribute('aria-label', `حفظ الآية ${ayahNumber}`);
                        bookmark.addEventListener('click', () => saveAyah(Number(surahInput.value), ayahNumber, 'bookmark', bookmark));
                        const position = document.createElement('button');
                        position.type = 'button';
                        position.textContent = 'موضعي';
                        position.setAttribute('aria-label', `حفظ موضع القراءة عند الآية ${ayahNumber}`);
                        position.addEventListener('click', () => saveAyah(Number(surahInput.value), ayahNumber, 'position', position));
                        actions.append(bookmark, position);
                        item.append(actions);
                    }
                    verseList.append(item);
                });
                content.append(heading, verseList);
                content.hidden = false;
                if (tafsirSection && tafsirButton && tafsirFrame) {
                    const tafsirSurah = surah;
                    const tafsirAyah = ayah || Number(verses[0].number);
                    tafsirSection.hidden = false;
                    tafsirButton.onclick = () => {
                        if (tafsirFrame.childElementCount) {
                            const isOpen = !tafsirFrame.hidden;
                            tafsirFrame.hidden = isOpen;
                            tafsirButton.setAttribute('aria-expanded', String(!isOpen));
                            tafsirButton.textContent = isOpen ? 'عرض التفسير' : 'إخفاء التفسير';
                            return;
                        }
                        const frame = document.createElement('iframe');
                        frame.src = `https://api.quranpedia.net/embed?surah=${tafsirSurah}&ayah=${tafsirAyah}&type=tafsir`;
                        frame.title = `التفسير المتاح للآية ${tafsirAyah} من السورة ${tafsirSurah} عبر Quranpedia`;
                        frame.loading = 'lazy';
                        frame.referrerPolicy = 'strict-origin-when-cross-origin';
                        frame.allowFullscreen = true;
                        tafsirFrame.append(frame);
                        tafsirFrame.hidden = false;
                        tafsirButton.textContent = 'إخفاء التفسير';
                        tafsirButton.setAttribute('aria-expanded', 'true');
                    };
                }
                const singleAyahId = Number(verses[0]?.id);
                if (audio && Number.isInteger(singleAyahId) && singleAyahId > 0) {
                    audio.src = ayah
                        ? `https://cdn.islamic.network/quran/audio/128/ar.alafasy/${singleAyahId}.mp3`
                        : `https://cdn.islamic.network/quran/audio-surah/128/ar.alafasy/${surah}.mp3`;
                    audioWrap.hidden = false;
                }
                status.hidden = true;
                status.dataset.state = 'ready';
                if (ayah) content.querySelector(`#ayah-${ayah}`)?.scrollIntoView({ block: 'center' });
            } catch (_) {
                status.dataset.state = 'error';
                status.replaceChildren();
                const message = document.createElement('span');
                message.textContent = 'تعذر تحميل الآيات الآن. تحقق من الاتصال ثم حاول مجددًا.';
                const retry = document.createElement('button');
                retry.className = 'button button-secondary';
                retry.type = 'button';
                retry.textContent = 'إعادة المحاولة';
                retry.addEventListener('click', () => loadSurah(surah, ayah), { once: true });
                status.append(message, retry);
            }
        };

        form?.addEventListener('submit', (event) => {
            event.preventDefault();
            const surah = Number(surahInput.value);
            const ayah = Number(ayahInput.value) || null;
            const query = new URLSearchParams({ surah: String(surah) });
            if (ayah) query.set('ayah', String(ayah));
            history.replaceState(null, '', `/quran?${query}`);
            loadSurah(surah, ayah);
        });

        quranReader.querySelectorAll('[data-font-step]').forEach((button) => {
            button.addEventListener('click', () => {
                fontSize = Math.min(2.8, Math.max(1.2, fontSize + Number(button.dataset.fontStep) * 0.2));
                quranReader.style.setProperty('--reading-font-size', `${fontSize}rem`);
            });
        });

        audio?.addEventListener('error', () => {
            if (audioStatus) audioStatus.textContent = 'تعذر تشغيل التلاوة. جرّب تحديث الصفحة أو تحقق من الاتصال.';
        });

        loadSurah(Number(quranReader.dataset.surah), Number(quranReader.dataset.ayah) || null);
    }
})();
