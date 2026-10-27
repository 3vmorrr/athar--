# Review runtime UI and lifecycle

Task: 09.3  
Implementation status: **PARTIAL — mic + native graph probe landed; recognizer frontend still missing**

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

## Remaining functional gap

The shipped web feature obtains the full recognizer from @tilawa/core. That engine performs feature extraction, streaming cache propagation, CTC decode, Quran candidate search and word tracking. It is not present as Kotlin code in Athar.

Because the ONNX model input is 80-dimensional fbank frames rather than PCM samples, connecting microphone PCM directly to the model would be technically wrong. This task therefore remains PARTIAL, not acceptance-only. The next change must implement or import an approved native-equivalent frontend/decoder before 09.3 can be considered functionally implemented and before 09.4 feedback can be truthful.
