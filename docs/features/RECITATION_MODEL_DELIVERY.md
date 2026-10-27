# Verified optional recitation-model delivery

Task: 09.2  
Implementation branch: \`parallel/admin-foundation\`  
Status: **IMPLEMENTED / ACCEPTANCE PENDING**

The current repository model package is treated as an optional, license-restricted local artifact, not as an APK-bundled dependency and not as a proven native inference engine.

## Trusted manifest

The Android Gradle build reads the checked-in model package:

- \`zipformer_a0w_ep1_a05.int8.onnx\`;
- \`zipformer_quran.json\`;
- \`zipformer_a0w_ep1_a05.io.json\`.

For every build it computes SHA-256 directly from those exact bytes and generates \`review/model_manifest.json\` inside generated app assets. The manifest also records exact byte sizes, version \`zipformer-a0w-ep1-a05\`, NPL-1.2 and \`runtime_verified=false\`.

The build copies the repository's NPL-1.2 text and NOTICE into generated assets. It does **not** copy the ~75 MB model package into the APK.

## Consent and license

Consent is explicit and scoped to owner + model version + license id. The screen shows a concise NPL-1.2 restriction summary and can reveal the complete license text before acceptance.

The UI does not claim that NPL-1.2 is MIT or commercially unrestricted. It states that a feature powered by this model may not be sold/paywalled/subscription-gated/ad-funded under the included terms.

## Download integrity

Downloads are allowed only from \`assets/models/<known-file>\` on the same HTTPS origin as the configured Athar API host. HTTP and foreign hosts fail closed; redirects are disabled.

WorkManager persists the download across process death and requires connected network, battery-not-low and storage-not-low. It is unique per environment/owner/version.

Before starting, the client requires free space for the full package plus a 32 MiB reserve.

Each artifact:

1. downloads to app-private \`.part\`;
2. is bounded to the manifest byte count;
3. is SHA-256 hashed during the stream;
4. must match exact size and digest;
5. is renamed to its final private filename only after verification.

Cancellation cancels the active HTTP call and removes the partial file. Network/transient HTTP failures receive bounded retry; integrity mismatch is terminal and never activates the package.

## Activation, restart and rollback

The model becomes active only after all three final files re-verify. The generated manifest is stored beside the activated version.

A failed update never replaces the currently active version. The store retains the active and previous verified version and can atomically roll back only if the previous package still verifies. Removal is explicit and deletes model package files without touching Quran, reading progress or other private user data.

## Runtime boundary

Task 09.2 intentionally stops at verified delivery. \`runtime_verified=false\` is visible in the UI. No microphone permission, audio capture, ONNX Runtime dependency or inference claim is added here. Those belong to Task 09.3 and must remain unavailable until real ARM32/API27 runtime tests pass.

Acceptance remains pending for Android assemble/unit/test APK/lint/resources, generated-manifest inspection, restart/cancel/low-storage/corruption/rollback tests and authorized API27 physical download evidence.
