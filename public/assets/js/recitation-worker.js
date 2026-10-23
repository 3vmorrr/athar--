const MODEL_URL = new URL('../models/zipformer_interp_gentle_a05.int8.onnx', import.meta.url).href;
const IO_URL = new URL('../models/zipformer_interp_gentle_a05.io.json', import.meta.url).href;
const CORPUS_URL = new URL('../models/zipformer_quran.json', import.meta.url).href;
const CORE_URL = 'https://cdn.jsdelivr.net/npm/@tilawa/core@0.4.0/+esm';
const ORT_URL = 'https://cdn.jsdelivr.net/npm/onnxruntime-web@1.24.2/dist/ort.wasm.min.mjs';
const ORT_ASSETS = 'https://cdn.jsdelivr.net/npm/onnxruntime-web@1.24.2/dist/';
const CACHE_NAME = 'athar-tilawa-assets-v1';
let session = null;
let messageQueue = Promise.resolve();

const tell = (message) => self.postMessage(message);

async function fetchJson(url) {
    const cache = 'caches' in self ? await caches.open(CACHE_NAME).catch(() => null) : null;
    const cached = await cache?.match(url);
    if (cached) return cached.json();
    const response = await fetch(url);
    if (!response.ok) throw new Error(`Asset download failed (${response.status}).`);
    const copy = response.clone();
    try { await cache?.put(url, copy); } catch (_) { /* Keep the asset usable when storage is full. */ }
    return response.json();
}

async function fetchModel(url) {
    const cache = 'caches' in self ? await caches.open(CACHE_NAME).catch(() => null) : null;
    const cached = await cache?.match(url);
    if (cached) {
        tell({ type: 'loading', percent: 100 });
        return cached.arrayBuffer();
    }

    const response = await fetch(url);
    if (!response.ok || !response.body) throw new Error(`Model download failed (${response.status}).`);
    const total = Number(response.headers.get('content-length')) || 0;
    const reader = response.body.getReader();
    const chunks = [];
    let loaded = 0;
    while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        chunks.push(value);
        loaded += value.byteLength;
        tell({ type: 'loading', percent: total ? Math.min(99, Math.floor(loaded / total * 100)) : 0 });
    }
    const blob = new Blob(chunks, { type: 'application/octet-stream' });
    const arrayBuffer = await blob.arrayBuffer();
    try { await cache?.put(url, new Response(blob, { headers: { 'Content-Type': 'application/octet-stream' } })); } catch (_) { /* The model remains usable for this visit when storage is full. */ }
    tell({ type: 'loading', percent: 100 });
    return arrayBuffer;
}

async function initialize(quran) {
    if (session) return;
    tell({ type: 'loading_status', message: 'Loading the review engine.' });
    const [{ ZipformerSession }, ort] = await Promise.all([import(CORE_URL), import(ORT_URL)]);
    ort.env.wasm.wasmPaths = ORT_ASSETS;
    ort.env.wasm.numThreads = 1;
    ort.env.wasm.simd = true;

    tell({ type: 'loading_status', message: 'Downloading the on-device Quran model.' });
    const [model, io, corpus] = await Promise.all([fetchModel(MODEL_URL), fetchJson(IO_URL), fetchJson(CORPUS_URL)]);
    tell({ type: 'loading_status', message: 'Starting local recognition.' });
    session = await ZipformerSession.create({
        ort,
        model,
        io,
        corpus,
        quran,
        executionProviders: ['wasm'],
        tailSeconds: 1.5,
        onEvent: (message) => tell(message),
    });
    tell({ type: 'ready' });
}

self.addEventListener('message', (event) => {
    const message = event.data || {};
    if (message.type === 'init') {
        initialize(Array.isArray(message.quran) ? message.quran : []).catch((error) => tell({ type: 'error', message: error instanceof Error ? error.message : String(error) }));
    } else if (message.type === 'start') {
        messageQueue = messageQueue.then(() => {
            if (!session) throw new Error('The local review model is not ready.');
            session.reset();
            session.setExpected(message.passage || null);
            session.setMode('correction');
            tell({ type: 'review_started' });
        }).catch((error) => tell({ type: 'error', message: error instanceof Error ? error.message : String(error) }));
    } else if (message.type === 'audio' && message.samples instanceof Float32Array) {
        const samples = message.samples;
        messageQueue = messageQueue.then(async () => { if (session) await session.feed(samples); })
            .catch((error) => tell({ type: 'error', message: error instanceof Error ? error.message : String(error) }));
    } else if (message.type === 'stop') {
        messageQueue = messageQueue.then(async () => {
            if (session) await session.stop();
            tell({ type: 'stopped' });
        }).catch((error) => tell({ type: 'error', message: error instanceof Error ? error.message : String(error) }));
    } else if (message.type === 'correct') {
        messageQueue = messageQueue.then(() => {
            if (!session) return;
            for (const output of session.correct(message.action)) tell(output);
        }).catch((error) => tell({ type: 'error', message: error instanceof Error ? error.message : String(error) }));
    }
});
