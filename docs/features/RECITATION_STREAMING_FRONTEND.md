# Native streaming recitation frontend

Task: 09.3  
Implementation branch: \`parallel/admin-foundation\`  
Status: **CORE STREAMING INFERENCE IMPLEMENTED / UI WIRING + PHYSICAL ACCEPTANCE PENDING**

The Android review runtime now has a real native acoustic frontend rather than only a zero-input ONNX probe.

## Pipeline boundary

The native pipeline is deliberately limited to:

\`16 kHz Float32 PCM -> 80-bin Kaldi fbank -> stateful Zipformer ONNX -> greedy CTC token IDs\`

This is the complete local acoustic-inference boundary needed by Task 09.3. It does **not** search the Quran corpus, infer an ayah, align words or label anything correct/incorrect. Those semantics remain Task 09.4.

## Frontend parity

\`ReviewKaldiFbank\` follows the pinned Tilawa frontend constants used by the shipped model:

- 16 kHz sample rate;
- 400-sample frame length;
- 160-sample shift;
- 80 mel bins;
- 512-point FFT;
- 0.97 pre-emphasis;
- Povey window;
- 20–7600 Hz mel range;
- reflected edge samples;
- streaming frame readiness \`f*160+280\`.

Pure JVM tests include the upstream readiness table, zero-signal log-floor behavior and a long-stream retained-buffer bound.

## Streaming model state

\`ReviewStreamingRecognizer\` owns exactly one native ONNX session while active.

The IO manifest is now validated as a full state contract: every model input other than \`x\` must have a corresponding \`new_<state>\` output with identical dtype and dimensions. On each 61-frame window, the recognizer copies the model-produced state outputs into fresh bounded tensors and advances by the manifest's 48-frame hop.

\`processed_lens\` is copied from the model output. Host code never invents or increments it.

All native input/state tensors are bounded by the existing 16 MiB manifest gate. Model execution stays sequential with one intra-op and one inter-op thread.

## CTC boundary

The greedy decoder collapses repeated class runs, treats class 250 as blank and reports only:

- token id;
- CTC frame index;
- top-vs-second probability margin.

It intentionally does not embed Quran text or the Tilawa corpus mapping in Task 09.3.

## Stop behavior

The recognizer appends 1.5 seconds of local zero audio before fbank finalization to flush the CTC tail, matching the current Athar web worker's configured tail. No audio or feature frame is persisted.

The next commit wires the existing bounded microphone lifecycle to this recognizer through a four-chunk in-memory queue and exposes only diagnostic frame/token counts. Physical ARM32/ARM64 execution remains an acceptance gate.
