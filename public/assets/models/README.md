# Quran Recitation Review Model Assets

The local recitation review downloads these same-origin assets on first use and caches them in the browser when storage is available:

- `zipformer_a0w_ep1_a05.int8.onnx` — approximately 69.2 MB; current Zipformer release.
- `zipformer_quran.json` — approximately 5.45 MB Quran phoneme corpus.
- `zipformer_a0w_ep1_a05.io.json` — model input/output manifest (same format as the previous release).

Together, first-use model data is approximately 75 MB. The current ONNX model is from [Tilawa's Zipformer a0w-ep1-a0.5 release](https://github.com/yazinsai/tilawa/releases/tag/zipformer-a0w-ep1-a0.5); the compatible phoneme corpus and manifest are from [Tilawa release v0.3.0](https://github.com/yazinsai/tilawa/releases/tag/v0.3.0). The ONNX model and Quran corpus are NPL-1.2 derivatives. See [`NPL-1.2.txt`](NPL-1.2.txt) and [`NOTICE.md`](NOTICE.md) for terms and acknowledgements. The model may not be sold or used to power a paid feature; use another licensed model or obtain permission before commercialization.

Inference runs in the visitor's browser using ONNX Runtime Web (MIT) and the `@tilawa/core` SDK (MIT), loaded from pinned jsDelivr package versions by `recitation-worker.js`. Audio capture uses AudioWorklet where available and falls back to ScriptProcessor in older browsers. Microphone audio stays on the device; it is never uploaded by Athar.

The production host must serve files of this size and permit the roughly 75 MB first-use transfer per visitor. Browser storage quotas may prevent caching; the app still uses the assets for the current visit, and a later visit can require another download.
