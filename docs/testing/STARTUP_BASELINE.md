# Release-like startup baseline harness

Task: 22.1  
Implementation branch: \`parallel/admin-foundation\`  
Status: **MEASUREMENT HARNESS IMPLEMENTED / PHYSICAL BASELINE PENDING**

No startup performance number is claimed by this document. Task 22.1 remains acceptance-pending until the authorized representative phone is measured.

## Build variant

The Android app now has a \`benchmark\` build type initialized from \`release\`:

- release minification and resource shrinking remain enabled;
- the variant is non-debuggable;
- debug signing is used only so the local measurement APK is installable without production signing material;
- benchmark/release Gradle tasks require an HTTPS \`ATHAR_API_BASE_URL\`;
- \`src/benchmark/AndroidManifest.xml\` enables shell profiling on Android 10+ without making the app debuggable.

This does not change the production \`release\` manifest or signing configuration.

API26–28 devices can still be measured with \`am start -W\`; the profileable element is for newer-device trace/profiler follow-up.

## Reproducible measurement

Run the parser self-test first:

\`\`\`bash
python scripts/android_startup_baseline.py --self-test
\`\`\`

Build a release-like APK with the approved HTTPS API environment:

\`\`\`bash
cd android
./gradlew :app:assembleBenchmark -PATHAR_API_BASE_URL=https://approved.example/api/v1/
\`\`\`

Install/update that benchmark APK using the team's normal authorized-device procedure. The measurement script deliberately does **not** install/uninstall the app, clear data, grant permissions, change locale/theme or change global settings.

Measure the device's current retained state:

\`\`\`bash
python scripts/android_startup_baseline.py \
  --serial <authorized-serial> \
  --iterations 10 \
  --state-label signed-in-home-existing-data \
  --apk android/app/build/outputs/apk/benchmark/app-benchmark.apk \
  --output docs/testing/results/startup-api27-<build-sha>.json
\`\`\`

The harness records cold and warm \`am start -W\` samples, median/p95/min/max, device manufacturer/model/API/fingerprint, package/activity, git SHA and optional APK SHA-256.

Cold runs use only \`am force-stop\`. Warm runs send HOME and relaunch while keeping the process eligible to remain resident. There is no \`pm clear\`, uninstall or global-setting mutation.

## Evidence rules

A reviewable result must record:

1. exact pushed git SHA and APK SHA-256;
2. authorized device model/API/fingerprint;
3. state label (for example signed-out, signed-in-home-existing-data);
4. at least 10 cold and 10 warm samples for the accepted baseline;
5. Arabic RTL Light journey first on the API27 target, then the required English/error/offline/retained-state checks from the queue;
6. any startup regression fix as a separate measured change, not a speculative optimization.

Do not compare samples taken from different retained states as if they were the same baseline. Do not report debug startup as release evidence.

## Acceptance status

The build/measurement machinery is implemented. Actual cold/warm startup evidence, trace review and any measured regression fixes remain pending because this execution environment has no authorized physical Android device.
