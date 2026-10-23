(() => {
    const quranpedia = 'https://api.quranpedia.net/v1';
    const prayApi = 'https://pray.api.islamic.network/v1';

    const text = (value, fallback = '') => typeof value === 'string' && value.trim() ? value.trim() : fallback;
    const request = async (url) => {
        const controller = new AbortController();
        const timeout = window.setTimeout(() => controller.abort(), 12000);
        try {
            const response = await fetch(url, { headers: { Accept: 'application/json' }, signal: controller.signal });
            if (!response.ok) throw new Error('provider-response');
            return await response.json();
        } finally {
            window.clearTimeout(timeout);
        }
    };
    const make = (tag, className, content) => {
        const node = document.createElement(tag);
        if (className) node.className = className;
        if (content !== undefined) node.textContent = content;
        return node;
    };
    const setStatus = (node, message, state = 'info') => {
        node.hidden = false;
        node.dataset.state = state;
        node.textContent = message;
    };
    const sourceLink = (href, label) => {
        const link = make('a', '', label);
        link.href = href;
        link.target = '_blank';
        link.rel = 'noopener noreferrer';
        return link;
    };
    const parseSearchParams = () => new URLSearchParams(window.location.search);
    const csrfToken = document.querySelector('meta[name="csrf-token"]')?.content || '';

    document.querySelectorAll('[data-favorite]').forEach((button) => {
        button.addEventListener('click', async () => {
            button.disabled = true;
            try {
                const response = await fetch('/api/favorites', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8', 'X-CSRF-Token': csrfToken, Accept: 'application/json' },
                    body: new URLSearchParams({ type: button.dataset.type || '', id: button.dataset.id || '' }),
                    credentials: 'same-origin',
                });
                const result = await response.json();
                if (!response.ok || !result.ok) throw new Error('favorite-failed');
                button.dataset.saved = String(result.saved);
                button.setAttribute('aria-pressed', String(result.saved));
                button.textContent = result.saved ? 'إزالة من المفضلة' : 'أضف للمفضلة';
            } catch (_) {
                button.textContent = 'تعذر الحفظ؛ حاول مجددًا';
            } finally { button.disabled = false; }
        });
    });

    const quranReader = document.querySelector('[data-quran-reader]');
    if (quranReader?.dataset.tool === 'search') {
        const form = quranReader.querySelector('[data-quran-search-form]');
        const input = form.elements.q;
        const status = quranReader.querySelector('[data-quran-search-status]');
        const results = quranReader.querySelector('[data-quran-search-results]');
        const runSearch = async (query) => {
            results.replaceChildren();
            setStatus(status, 'جارٍ البحث في نص القرآن…', 'loading');
            try {
                const payload = await request(`https://api.alquran.cloud/v1/search/${encodeURIComponent(query)}/all/quran-uthmani`);
                const matches = payload?.data?.matches;
                if (payload?.code !== 200 || !Array.isArray(matches)) throw new Error('provider-shape');
                status.hidden = true;
                if (!matches.length) { setStatus(status, 'لا توجد آيات مطابقة لهذه العبارة.', 'empty'); return; }
                matches.slice(0, 25).forEach((match) => {
                    if (typeof match.text !== 'string' || !Number.isInteger(Number(match.surah?.number)) || !Number.isInteger(Number(match.numberInSurah))) return;
                    const card = make('article', 'knowledge-card quran-match');
                    card.append(make('p', 'hadith-text', match.text));
                    const reference = make('p', 'book-details', `سورة ${text(match.surah?.name, 'غير معروفة')} · الآية ${match.numberInSurah}`);
                    const link = make('a', 'button button-secondary', 'افتح الآية');
                    link.href = `/quran?${new URLSearchParams({ tool: 'ayah', surah: String(match.surah.number), ayah: String(match.numberInSurah) })}`;
                    card.append(reference, link);
                    results.append(card);
                });
                if (!results.childElementCount) setStatus(status, 'لم يرسل المصدر نتائج قابلة للعرض.', 'empty');
            } catch (_) {
                setStatus(status, 'تعذر البحث في القرآن الآن. حاول مرة أخرى بعد قليل.', 'error');
                const retry = make('button', 'button button-secondary', 'إعادة المحاولة');
                retry.type = 'button';
                retry.addEventListener('click', () => runSearch(query));
                status.append(' ', retry);
            }
        };
        form.addEventListener('submit', (event) => {
            event.preventDefault();
            const query = input.value.trim();
            if (query.length < 2) return;
            const params = parseSearchParams();
            params.set('q', query);
            history.replaceState(null, '', `/quran?${params}`);
            runSearch(query);
        });
        const initialQuery = parseSearchParams().get('q')?.trim() || '';
        if (initialQuery.length >= 2 && initialQuery.length <= 80) { input.value = initialQuery; runSearch(initialQuery); }
    }

    const hadithPage = document.querySelector('[data-hadith-page]');
    if (hadithPage) {
        const form = hadithPage.querySelector('[data-hadith-form]');
        const results = hadithPage.querySelector('[data-hadith-results]');
        const status = hadithPage.querySelector('[data-hadith-status]');
        const queryInput = form.elements.q;

        const search = async (query) => {
            status.hidden = true;
            results.replaceChildren();
              setStatus(status, 'جارٍ البحث في الموسوعة الحديثية…', 'loading');
            try {
                const response = await fetch('/api/hadith/search?' + new URLSearchParams({ q: query }), { headers:{Accept:'application/json'}, credentials:'same-origin' });
                const payload = await response.json();
                if (!response.ok && payload?.error !== 'service_not_configured') throw new Error('provider-response');
                const items = payload?.data;
                if (!Array.isArray(items)) {
                    if (payload?.error === 'service_not_configured') throw new Error('service-not-configured');
                    throw new Error('provider-shape');
                }
                status.hidden = true;
                if (!items.length) {
                    setStatus(status, 'لا توجد نتائج لهذه العبارة. جرّب كلمات عربية أخرى.', 'empty');
                    return;
                }
                items.forEach((hadith) => {
                    if (typeof hadith.text !== 'string' || !hadith.text.trim() || typeof hadith.grade !== 'string' || !hadith.grade.trim() || typeof hadith.url !== 'string' || !hadith.url.startsWith('https://dorar.net/')) return;
                    const card = make('article', 'knowledge-card hadith-card');
                    const meta = make('div', 'card-meta');
                    meta.append(make('span', 'source-chip', text(hadith.source, 'الموسوعة الحديثية')));
                    meta.append(make('span', 'source-chip', `خلاصة حكم المحدّث: ${hadith.grade}`));
                    if (hadith.narrator) meta.append(make('span', '', `الراوي: ${hadith.narrator}`));
                    if (hadith.muhaddith) meta.append(make('span', '', `المحدّث: ${hadith.muhaddith}`));
                    if (hadith.page_or_number) meta.append(make('span', '', `المرجع: ${hadith.page_or_number}`));
                    card.append(meta, make('p', 'hadith-text', hadith.text));
                    card.append(sourceLink(hadith.url, 'صفحة الحديث في الدرر السنية ↗'));
                    results.append(card);
                });
                if (!results.childElementCount) setStatus(status, 'لم تتوفر نتائج مكتملة بالنص وخلاصة الحكم.', 'empty');
            } catch (error) {
                setStatus(status, error.message === 'service-not-configured' ? 'خدمة البحث لم تُفعّل بعد. أضف مفتاح الدرر السنية المعتمد إلى إعدادات الخادم.' : 'تعذر البحث الآن. حاول مرة أخرى بعد قليل.', 'error');
                const retry = make('button', 'button button-secondary', 'إعادة المحاولة');
                retry.type = 'button';
                retry.addEventListener('click', () => search(query));
                status.append(' ', retry);
            }
        };

        form.addEventListener('submit', (event) => {
            event.preventDefault();
            const query = queryInput.value.trim();
            if (query.length < 2 || query.length > 200) return;
            history.replaceState(null, '', `/hadith?${new URLSearchParams({ q: query })}`);
            search(query);
        });
        const params = parseSearchParams();
        if (params.has('q') && params.get('q').trim().length >= 2 && params.get('q').trim().length <= 200) {
            search(params.get('q').trim());
        }
    }

    const dorarPage = document.querySelector('[data-dorar-page]');
    if (dorarPage) {
        const form = dorarPage.querySelector('[data-dorar-form]');
        const queryInput = form.elements.q;
        const scopeInput = form.elements.scope;
        const results = dorarPage.querySelector('[data-dorar-results]');
        const status = dorarPage.querySelector('[data-dorar-status]');
        const scopeNames = { hadith: 'الحديث النبوي', tafseer: 'التفسير', aqeeda: 'العقيدة', feqhia: 'الفقه', alakhlaq: 'الأخلاق', aadab: 'الآداب', history: 'التاريخ' };
        const search = async (query, scope) => {
            results.replaceChildren();
            setStatus(status, `جارٍ البحث في ${scopeNames[scope]}…`, 'loading');
            try {
                const response = await fetch('/api/dorar/search?' + new URLSearchParams({ q: query, scope }), { headers: { Accept: 'application/json' }, credentials: 'same-origin' });
                const payload = await response.json();
                if (!response.ok && payload?.error !== 'service_not_configured') throw new Error('provider-response');
                if (payload?.error === 'service_not_configured') throw new Error('service-not-configured');
                if (!Array.isArray(payload?.data)) throw new Error('provider-shape');
                if (!payload.data.length) { setStatus(status, 'لا توجد نتائج لهذه العبارة. جرّب كلمات عربية أخرى.', 'empty'); return; }
                payload.data.forEach((item) => {
                    if (!item || typeof item.url !== 'string' || !item.url.startsWith('https://dorar.net/')) return;
                    if (scope === 'hadith' && (typeof item.grade !== 'string' || !item.grade.trim())) return;
                    const body = text(scope === 'tafseer' ? item.body : item.text, text(item.body, text(item.text)));
                    if (!body) return;
                    const card = make('article', `knowledge-card${scope === 'hadith' ? ' hadith-card' : ''}`);
                    const meta = make('div', 'card-meta');
                    meta.append(make('span', 'source-chip', scopeNames[scope]));
                    if (scope === 'hadith' && typeof item.grade === 'string' && item.grade.trim()) meta.append(make('span', 'source-chip', `خلاصة حكم المحدّث: ${item.grade}`));
                    if (item.title) card.append(make('h2', '', item.title));
                    card.append(meta, make('p', scope === 'hadith' ? 'hadith-text' : '', body));
                    card.append(sourceLink(item.url, 'المصدر: الدرر السنية ↗'));
                    results.append(card);
                });
                if (!results.childElementCount) setStatus(status, 'لم تتوفر نتائج مكتملة مرتبطة بمصدرها.', 'empty');
                else status.hidden = true;
            } catch (error) {
                setStatus(status, error.message === 'service-not-configured' ? 'لم يُضف مفتاح الدرر المعتمد بعد. يلزم طلب موافقة الخدمة التي اخترتها ثم حفظ الرمز في إعدادات الإدارة.' : 'تعذر البحث الآن. تحقق من اتصال الخادم وحاول مرة أخرى.', 'error');
                const retry = make('button', 'button button-secondary', 'إعادة المحاولة');
                retry.type = 'button';
                retry.addEventListener('click', () => search(query, scope));
                status.append(' ', retry);
            }
        };
        form.addEventListener('submit', (event) => {
            event.preventDefault();
            const query = queryInput.value.trim();
            const scope = scopeInput.value;
            if (query.length < 2 || query.length > 200) return;
            history.replaceState(null, '', `/dorar?${new URLSearchParams({ q: query, scope })}`);
            search(query, scope);
        });
        const params = parseSearchParams();
        if (Object.hasOwn(scopeNames, params.get('scope'))) scopeInput.value = params.get('scope');
        if (params.has('q') && params.get('q').trim().length >= 2 && params.get('q').trim().length <= 200) search(params.get('q').trim(), scopeInput.value);
    }

    const booksPage = document.querySelector('[data-books-page]');
    if (booksPage) {
        const form = booksPage.querySelector('[data-books-form]');
        const results = booksPage.querySelector('[data-books-results]');
        const status = booksPage.querySelector('[data-books-status]');
        const queryInput = form.elements.q;
        const renderBooks = async (query, page = 1, append = false) => {
            if (!append) results.replaceChildren();
            results.querySelector('.pagination-control')?.remove();
            setStatus(status, 'جارٍ تحميل نتائج الفهرس…', 'loading');
            try {
                const payload = await request(`${quranpedia}/search/${encodeURIComponent(query)}/books?page=${page}`);
                const items = payload?.items;
                if (!Array.isArray(items)) throw new Error('provider-shape');
                status.hidden = true;
                if (!items.length) {
                    setStatus(status, 'لا توجد نتائج مطابقة في هذا الفهرس.', 'empty');
                    return;
                }
                items.slice(0, 15).forEach((item) => {
                    const book = item?.book_info;
                    if (!book || typeof book.name !== 'string') return;
                    const card = make('article', 'knowledge-card book-card');
                    card.append(make('span', 'source-chip', text(book.category, 'كتاب')));
                    card.append(make('h2', '', book.name));
                    card.append(make('p', 'book-author', `المؤلف: ${text(book.author, 'غير مذكور')}`));
                    const details = [book.edition, book.publish_year, book.nasher].filter((value) => typeof value === 'string' && value.trim());
                    if (details.length) card.append(make('p', 'book-details', details.join(' · ')));
                    if (Number.isInteger(Number(book.id))) card.append(sourceLink(`${quranpedia}/book/${Number(book.id)}`, 'بيانات الكتاب لدى المصدر ↗'));
                    results.append(card);
                });
                if (!results.childElementCount) setStatus(status, 'لا تتوفر بيانات قابلة للعرض لهذه النتائج.', 'empty');
                const lastPage = Number(payload.lastPage || 1);
                if (page < lastPage) {
                    const more = make('button', 'button button-secondary pagination-control', 'المزيد من النتائج');
                    more.type = 'button';
                    more.addEventListener('click', () => renderBooks(query, page + 1, true));
                    results.append(more);
                }
            } catch (_) {
                setStatus(status, 'تعذر تحميل الفهرس الآن. حاول مرة أخرى بعد قليل.', 'error');
                const retry = make('button', 'button button-secondary', 'إعادة المحاولة');
                retry.type = 'button';
                retry.addEventListener('click', () => renderBooks(query, page, append));
                status.append(' ', retry);
            }
        };
        form.addEventListener('submit', (event) => {
            event.preventDefault();
            const query = queryInput.value.trim();
            if (query.length < 2 || query.length > 80) return;
            history.replaceState(null, '', `/books?${new URLSearchParams({ q: query })}`);
            renderBooks(query);
        });
        const initialQuery = queryInput.value.trim();
        if (initialQuery.length >= 2 && initialQuery.length <= 80) renderBooks(initialQuery);
    }

    const adhkarPage = document.querySelector('[data-adhkar-page]');
    if (adhkarPage) {
        const form = adhkarPage.querySelector('[data-adhkar-form]');
        const results = adhkarPage.querySelector('[data-adhkar-results]');
        const status = adhkarPage.querySelector('[data-adhkar-status]');
        const renderEntries = async (collection, query = '', page = 1, append = false) => {
            if (!append) results.replaceChildren();
            results.querySelector('.pagination-control')?.remove();
            setStatus(status, 'جارٍ تحميل المحتوى…', 'loading');
            try {
                const params = new URLSearchParams({ page: String(page), limit: '20' });
                let endpoint = `${prayApi}/${collection}?${params}`;
                if (query) endpoint = `${prayApi}/search?${new URLSearchParams({ q: query, type: collection, page: String(page), limit: '20' })}`;
                const payload = await request(endpoint);
                const items = query ? payload?.data?.results : payload?.data;
                if (!payload || payload.status !== 'OK' || !Array.isArray(items)) throw new Error('provider-shape');
                status.hidden = true;
                if (!items.length) {
                    setStatus(status, 'لا توجد نتائج مطابقة في الفهرس المتاح.', 'empty');
                    return;
                }
                items.slice(0, 20).forEach((entry) => {
                    if (typeof entry.slug !== 'string') return;
                    const detailCollection = entry.type === 'adhkaar' ? 'adhkaars' : entry.type === 'salawaat' ? 'salawaats' : entry.type === 'dua' ? 'duas' : collection;
                    const card = make('article', 'knowledge-card dhikr-card');
                    const name = typeof entry.name === 'object' ? entry.name.ar : entry.name;
                    card.append(make('h2', '', text(name, 'ذكر')));
                    let formulaNode = null;
                    if (typeof entry.formula === 'string') {
                        formulaNode = make('p', 'dhikr-formula', entry.formula);
                        card.append(formulaNode);
                    }
                    const actions = make('div', 'dhikr-actions');
                    actions.hidden = !formulaNode;
                    const count = make('output', 'dhikr-count', '0');
                    count.setAttribute('aria-live', 'polite');
                    count.setAttribute('aria-label', 'عدد النقرات');
                    const requiredCount = Math.max(1, Number(entry.repeat_count || entry.repeat || 1));
                    let completionRecorded = false;
                    const add = make('button', 'button button-primary', 'سبّح +1');
                    add.type = 'button';
                    add.setAttribute('aria-label', 'زيادة العداد واحدًا');
                    add.addEventListener('click', async () => {
                        count.value = String(Number(count.value || count.textContent) + 1);
                        count.textContent = count.value;
                        if (!completionRecorded && Number(count.value) >= requiredCount) {
                            completionRecorded = true;
                            const token = document.querySelector('meta[name="csrf-token"]')?.content || '';
                            try { await fetch('/api/adhkar/completion', { method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded;charset=UTF-8','X-CSRF-Token':token,Accept:'application/json'}, body:'completed=1', credentials:'same-origin' }); } catch (_) { /* Guest counters remain usable without an account. */ }
                        }
                    });
                    const reset = make('button', 'button button-secondary', 'إعادة');
                    reset.type = 'button';
                    reset.setAttribute('aria-label', 'إعادة العداد إلى الصفر');
                    reset.addEventListener('click', () => { count.value = '0'; count.textContent = '0'; });
                    const refButton = make('button', 'text-button', formulaNode ? 'المصدر والتفاصيل' : 'عرض النص والمصدر');
                    refButton.type = 'button';
                    const attribution = make('p', 'dhikr-source', '');
                    attribution.hidden = true;
                    refButton.addEventListener('click', async () => {
                        refButton.disabled = true;
                        try {
                            const detail = await request(`${prayApi}/${detailCollection}/${encodeURIComponent(entry.slug)}`);
                            const data = detail?.data;
                            if (!data || detail.status !== 'OK' || typeof data.formula !== 'string') throw new Error('provider-shape');
                            if (!formulaNode) {
                                formulaNode = make('p', 'dhikr-formula', data.formula);
                                card.insertBefore(formulaNode, actions);
                                actions.hidden = false;
                                refButton.textContent = 'المصدر والتفاصيل';
                            }
                            attribution.textContent = `المصدر: ${text(data.source, 'غير مذكور')}`;
                            attribution.hidden = false;
                            refButton.hidden = true;
                        } catch (_) {
                            attribution.textContent = 'تعذر تحميل تفاصيل المصدر الآن.';
                            attribution.hidden = false;
                            refButton.disabled = false;
                        }
                    });
                    actions.append(add, count, reset);
                    card.append(actions, refButton, attribution);
                    results.append(card);
                });
                if (!results.querySelector('.dhikr-card')) setStatus(status, 'لا توجد نصوص مكتملة قابلة للعرض في هذه النتائج.', 'empty');
                const lastPage = Number(payload.meta?.pages || 1);
                if (page < lastPage) {
                    const more = make('button', 'button button-secondary pagination-control', 'المزيد من النتائج');
                    more.type = 'button';
                    more.addEventListener('click', () => renderEntries(collection, query, page + 1, true));
                    results.append(more);
                }
            } catch (_) {
                setStatus(status, 'تعذر الاتصال بمصدر الأذكار. حاول مرة أخرى بعد قليل.', 'error');
                const retry = make('button', 'button button-secondary', 'إعادة المحاولة');
                retry.type = 'button';
                retry.addEventListener('click', () => renderEntries(collection, query, page, append));
                status.append(' ', retry);
            }
        };
        form.addEventListener('submit', (event) => {
            event.preventDefault();
            const collection = ['adhkaars', 'duas', 'salawaats'].includes(form.elements.collection.value) ? form.elements.collection.value : 'adhkaars';
            const query = form.elements.q.value.trim();
            if (query && (query.length < 2 || query.length > 80)) return;
            const params = new URLSearchParams({ collection });
            if (query) params.set('q', query);
            history.replaceState(null, '', `/adhkar?${params}`);
            renderEntries(collection, query);
        });
        const initial = parseSearchParams();
        const collection = ['adhkaars', 'duas', 'salawaats'].includes(initial.get('collection')) ? initial.get('collection') : 'adhkaars';
        form.elements.collection.value = collection;
        if (initial.has('q')) form.elements.q.value = initial.get('q');
        const initialQuery = initial.get('q')?.trim() || '';
        renderEntries(collection, initialQuery.length <= 80 ? initialQuery : '');
    }

    const lessonsPage = document.querySelector('[data-lessons-page]');
    if (lessonsPage) {
        const form = lessonsPage.querySelector('[data-video-form]');
        const input = form.elements.video;
        const status = lessonsPage.querySelector('[data-video-status]');
        const player = lessonsPage.querySelector('[data-video-player]');
        const progressStatus = lessonsPage.querySelector('[data-video-progress-status]');
        const parseVideoId = (raw) => {
            try {
                const url = new URL(raw);
                const host = url.hostname.toLowerCase().replace(/^www\./, '');
                if (host === 'youtu.be') return url.pathname.split('/').filter(Boolean)[0] || '';
                if (!['youtube.com', 'm.youtube.com', 'youtube-nocookie.com'].includes(host)) return '';
                if (url.pathname === '/watch') return url.searchParams.get('v') || '';
                const match = url.pathname.match(/^\/(?:embed|shorts)\/([\w-]{11})(?:\/|$)/);
                return match?.[1] || '';
            } catch (_) { return ''; }
        };
        let youtubePlayer = null;
        let progressTimer = null;
        let activeLessonId = 0;
        let resumeSeconds = 0;
        let apiPromise = null;
        const loadYouTubeApi = () => {
            if (window.YT?.Player) return Promise.resolve();
            if (apiPromise) return apiPromise;
            apiPromise = new Promise((resolve, reject) => {
                const previousReady = window.onYouTubeIframeAPIReady;
                window.onYouTubeIframeAPIReady = () => {
                    if (typeof previousReady === 'function') previousReady();
                    resolve();
                };
                const script = document.createElement('script');
                script.src = 'https://www.youtube.com/iframe_api';
                script.async = true;
                script.onerror = () => reject(new Error('youtube-api-unavailable'));
                document.head.append(script);
            });
            return apiPromise;
        };
        const saveVideoProgress = (complete = false) => {
            if (!activeLessonId || !youtubePlayer || typeof youtubePlayer.getCurrentTime !== 'function') return;
            const position = Math.max(0, Math.floor(youtubePlayer.getCurrentTime() || 0));
            const duration = Math.max(0, Math.floor(youtubePlayer.getDuration() || 0));
            const data = new URLSearchParams({ lesson_id: String(activeLessonId), position: String(position), duration: String(duration), complete: complete ? '1' : '0' });
            fetch('/api/lesson/progress', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8', 'X-CSRF-Token': csrfToken, Accept: 'application/json' },
                body: data,
                credentials: 'same-origin',
                keepalive: true,
            }).then((response) => {
                if (response.ok && progressStatus) {
                    progressStatus.hidden = false;
                    progressStatus.textContent = complete ? 'اكتمل هذا الدرس.' : 'حُفظ موضع المشاهدة بشكل تقريبي.';
                }
            }).catch(() => {});
        };
        const loadVideo = async (raw, updateUrl = false, lessonId = 0, startAt = 0) => {
            const id = parseVideoId(raw);
            if (progressTimer) window.clearInterval(progressTimer);
            if (activeLessonId) saveVideoProgress();
            youtubePlayer?.destroy?.();
            youtubePlayer = null;
            player.replaceChildren();
            activeLessonId = 0;
            if (progressStatus) { progressStatus.hidden = true; progressStatus.textContent = ''; }
            if (!/^[\w-]{11}$/.test(id)) {
                player.hidden = true;
                setStatus(status, 'الرابط غير صالح. استخدم رابط فيديو YouTube عاديًا أو رابط youtu.be.', 'error');
                return;
            }
            const host = document.createElement('div');
            host.id = 'athar-youtube-player';
            host.setAttribute('aria-label', 'مشغل درس YouTube');
            player.append(host);
            player.hidden = false;
            status.hidden = true;
            activeLessonId = Number(lessonId) || 0;
            resumeSeconds = Math.max(0, Number(startAt) || 0);
            try {
                await loadYouTubeApi();
                if (!player.isConnected || !host.isConnected) return;
                youtubePlayer = new window.YT.Player(host, {
                    width: '100%', height: '100%', videoId: id,
                    playerVars: { rel: 0, playsinline: 1, origin: window.location.origin },
                    events: {
                        onReady: (event) => { if (resumeSeconds > 3) event.target.seekTo(resumeSeconds, true); },
                        onStateChange: (event) => {
                            if (!activeLessonId) return;
                            if (event.data === window.YT.PlayerState.PLAYING) {
                                if (progressTimer) window.clearInterval(progressTimer);
                                progressTimer = window.setInterval(() => saveVideoProgress(false), 30000);
                            } else if (event.data === window.YT.PlayerState.PAUSED) {
                                if (progressTimer) window.clearInterval(progressTimer);
                                progressTimer = null;
                                saveVideoProgress(false);
                            } else if (event.data === window.YT.PlayerState.ENDED) {
                                if (progressTimer) window.clearInterval(progressTimer);
                                progressTimer = null;
                                saveVideoProgress(true);
                            }
                        },
                        onError: () => setStatus(status, 'هذا الفيديو غير متاح للتضمين. افتحه من YouTube أو اختر درسًا آخر.', 'error'),
                    },
                });
            } catch (_) {
                activeLessonId = 0;
                setStatus(status, 'تعذر تحميل مشغل YouTube الآن. يمكنك فتح الرابط مباشرة من المصدر.', 'error');
            }
            if (updateUrl) history.replaceState(null, '', `/lessons?${new URLSearchParams({ video: raw })}`);
        };
        window.addEventListener('pagehide', () => saveVideoProgress(false));
        form.addEventListener('submit', (event) => {
            event.preventDefault();
            loadVideo(input.value.trim(), true);
        });
        lessonsPage.querySelectorAll('[data-curated-video]').forEach((button) => {
            button.addEventListener('click', () => {
                if (button.dataset.lessonId && window.location.pathname === '/series') {
                    const params = new URLSearchParams(window.location.search);
                    params.set('lesson', button.dataset.lessonId);
                    window.location.assign('/series?' + params.toString());
                    return;
                }
                const video = button.dataset.curatedVideo || '';
                input.value = video;
                loadVideo(video, true, button.dataset.lessonId, button.dataset.resumeSeconds);
                const title = lessonsPage.querySelector('[data-active-lesson-title]');
                const description = lessonsPage.querySelector('[data-active-lesson-description]');
                if (title && button.dataset.lessonTitle) title.textContent = button.dataset.lessonTitle;
                if (description) description.textContent = button.dataset.lessonDescription || '';
                lessonsPage.querySelectorAll('.playlist-item').forEach((item) => item.setAttribute('aria-current', String(item === button)));
                player.scrollIntoView({ behavior: 'smooth', block: 'center' });
            });
        });
        const initialVideo = lessonsPage.dataset.video.trim();
        if (initialVideo) { input.value = initialVideo; loadVideo(initialVideo, false, lessonsPage.dataset.lessonId, lessonsPage.dataset.resumeSeconds); }
    }
})();
