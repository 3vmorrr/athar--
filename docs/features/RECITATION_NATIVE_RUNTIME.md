# Native recitation runtime and microphone boundary

Task: 09.3  
Implementation branch: \`parallel/admin-foundation\`  
Status: **CORE IMPLEMENTED / USER RUNTIME PROBE + PHYSICAL ACCEPTANCE PENDING**

This stage deliberately separates three claims that must not be conflated:

1. microphone capture works locally;
2. the downloaded ONNX graph can load and execute with its exact state tensors;
3. a complete recitation recognizer can turn microphone audio into truthful Quran verdicts.

This implementation establishes (1) and a real compatibility probe for (2). It does **not** claim (3).

## ONNX Runtime

The Android app pins \`com.microsoft.onnxruntime:onnxruntime-android:1.31.0\`.

The build resolves the exact AAR and fails \`preBuild\` unless it contains both:

- \`jni/armeabi-v7a/libonnxruntime.so\`;
- \`jni/arm64-v8a/libonnxruntime.so\`.

This turns ARM32 packaging from an assumption into a build gate. Actual execution on the authorized API27 ARM32 phone remains required.

The runtime parses the already-downloaded \`zipformer_a0w_ep1_a05.io.json\`. The current graph is expected to have 61 input frames, 48-frame hop, 80 features and 251 vocabulary entries. Tensor metadata is bounded; total zero-state input allocation is capped at 16 MiB.

The probe opens the verified model file with the native CPU runtime using one intra-op and one inter-op thread, checks exact input/output node-name sets, constructs every declared float32/int64 state tensor, executes one graph call requesting \`log_probs\`, and verifies the returned shape \`[1,12,251]\`.

A successful probe proves graph/runtime/operator/I/O compatibility for that device/runtime. It is **not** a recitation accuracy result and is not saved as a Quran verdict.

## Microphone capture

\`RECORD_AUDIO\` is declared, but no startup or background permission request exists.

The capture path is 16 kHz, mono, PCM16 using the voice-recognition audio source. Data is converted to bounded 100 ms Float32 chunks. The capture object itself stores no recording and writes no audio file.

The caller-side buffering contract is capped at four chunks / 6,400 samples. Permission is checked before start and during capture. Revocation, dead audio device, capture failure and user stop have distinct stop reasons.

Capture is not a foreground/background recorder and must be stopped by the screen lifecycle when the UI leaves the active foreground.

## Why recognition remains disabled

The shipped web feature delegates streaming fbank/state handling, CTC decoding, Quran phoneme search and per-word tracking to \`@tilawa/core\`. That TypeScript engine is not present in this Android repository.

Feeding raw PCM directly into this ONNX graph would be incorrect because the graph expects 80-dimensional feature frames plus 98 streaming state inputs. Therefore this task does not invent a PCM-to-feature transform or display a fake verse/correction result.

The next implementation work must port or otherwise provide an approved equivalent of that frontend/decoder before Task 09.4 may expose alignment feedback.

## Privacy

The microphone path contains no HTTP client, SessionRepository request or upload API. ONNX inference uses app-private verified files. No audio or transcript is persisted by this stage.

Acceptance remains pending for Android assemble/unit/test APK/lint/resources, the ABI AAR check, real model probe on API27 ARM32 and arm64 where available, contextual permission denial/revocation/interruption, background-stop behavior, memory observation and physical microphone format evidence.
