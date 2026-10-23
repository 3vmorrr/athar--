# Quran Recitation Review Model Assets

The local recitation review downloads these same-origin assets on first use and caches them in the browser when storage is available:

- `zipformer_interp_gentle_a05.int8.onnx` — approximately 69.2 MB.
- `zipformer_quran.json` — approximately 5.45 MB Quran phoneme corpus.
- `zipformer_interp_gentle_a05.io.json` — model input/output manifest.

Together, first-use model data is approximately 75 MB. The ONNX model and Quran corpus are NPL-1.2 derivatives from [Tilawa release v0.3.0](https://github.com/yazinsai/tilawa/releases/tag/v0.3.0). See [`NPL-1.2.txt`](NPL-1.2.txt) and [`NOTICE.md`](NOTICE.md) for terms and acknowledgements. The model may not be sold or used to power a paid feature; use another licensed model or obtain permission before commercialization.

Inference runs in the visitor's browser using ONNX Runtime Web (MIT) and the `@tilawa/core` SDK (MIT), loaded from pinned jsDelivr package versions by `recitation-worker.js`. Microphone audio stays on the device; it is never uploaded by Athar.

The production host must serve files of this size and permit the roughly 75 MB first-use transfer per visitor. Browser storage quotas may prevent caching; the app still uses the assets for the current visit, and a later visit can require another download.
