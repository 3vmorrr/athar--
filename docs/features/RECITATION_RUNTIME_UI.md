# Review runtime UI and lifecycle

Task: 09.3  
Implementation status: **FUNCTIONAL 09.3 IMPLEMENTATION LANDED / PHYSICAL ACCEPTANCE PENDING**

The model-management screen now exposes two explicit local tests after the verified model package is installed.

## Runtime compatibility probe

The runtime action executes the native ONNX graph with the exact downloaded IO manifest and bounded zero-state tensors. A success badge contains only the native ONNX Runtime version and bounded input-memory size. It does not expose a recitation score or accuracy claim.

## Contextual microphone permission

The app declares RECORD_AUDIO but never requests it on startup, model download or runtime probe. The permission launcher is reached only from the visible “Test microphone locally” action and only after the native graph probe succeeded.

Denied permission leaves the feature idle and retryable. Permission is checked again during each capture loop, so revocation stops capture. Dead audio routes and capture failures have explicit states.

The screen observes lifecycle events. ON_STOP and disposal stop the microphone; ON_RESUME rechecks permission.

## Memory/privacy boundary

Audio is read at 16 kHz mono PCM16 in 100 ms chunks and converted to Float32. The capture core stores no recording. The UI keeps only sample count and the latest peak level, not chunk content. A single foreground test is capped at 24 minutes as a safety bound.

There is no HTTP call in the capture/runtime path and no transcript/recording persistence.

## Local inference counters

After the compatibility probe succeeds, starting the microphone opens a verified streaming recognizer and then begins capture. The UI reports only cumulative model-frame and raw CTC-token counts. These diagnostics prove that microphone audio reached the local model; they are not a Quran match or correctness score.

Audio chunks cross a four-chunk bounded channel. If local inference cannot consume within that bound, capture stops with an explicit overload state. The app never grows an unbounded audio queue and never silently discards a chunk while continuing to claim recognition.

## Remaining semantic boundary

Task 09.3 ends at raw CTC tokens. Task 09.4 must consume the verified Quran phoneme corpus, implement expected-passage alignment and preserve uncertainty before any ayah/word feedback is shown.
