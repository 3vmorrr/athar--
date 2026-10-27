# Recitation review measured evaluation

Task: 09.5  
Implementation branch: \`parallel/admin-foundation\`  
Status: **MEASUREMENT HARNESS IMPLEMENTED / REPRESENTATIVE CORPUS + PHYSICAL RESULTS PENDING**

No review-accuracy or latency percentage is claimed by this document.

## What is measured

The evaluation separates outcomes instead of hiding uncertainty inside one “accuracy” number.

For clips labeled **clean**:

- \`CLEAR_MATCH\` is the expected outcome;
- \`POSSIBLE_ISSUE\` counts as a false rejection;
- \`ABSTAIN\` is reported separately.

For clips labeled **substitution / omission / repetition**:

- \`POSSIBLE_ISSUE\` is the expected outcome;
- \`CLEAR_MATCH\` counts as a false acceptance;
- \`ABSTAIN\` is reported separately.

For **noisy** clips, the expected behavior is \`ABSTAIN\`. Any clear/error decision is reported as a noisy unsafe-decision rate.

The report contains sample counts and Wilson 95% intervals for false acceptance, false rejection, abstention and noisy unsafe decisions. There is deliberately no “99%” target or hidden conversion of abstentions into successes.

## Latency and memory

The Android instrumentation runs the production review path:

\`PCM WAV -> ReviewStreamingRecognizer -> ReviewCtcExpansion -> ReviewPassageAligner\`

Per clip it records:

- total native inference milliseconds;
- finalization milliseconds;
- audio duration;
- real-time factor computed by the host;
- first-token compute time and audio-position time when available;
- sampled process PSS;
- model-frame/raw-token counts.

Inference timing excludes host-side ADB transfer and PSS sampling. Accepted latency evidence should run against the existing release-like/benchmark build, not Debug.

## Frozen corpus manifest

Audio does **not** belong in Git.

Each locally authorized 16 kHz mono PCM16 WAV is pinned by SHA-256 in a JSON manifest. A case contains an opaque speaker id, speaker class, error kind, expected passage and expected behavior.

\`purpose: "development"\` allows small local smoke corpora.

\`purpose: "acceptance"\` requires at least:

- 3 distinct reference reciters;
- 3 distinct learners;
- 30 clean clips;
- 20 substitutions;
- 20 omissions;
- 20 repetitions;
- 20 noisy/unclear clips.

These are minimum coverage floors, not an accuracy target.

## Host checks

Self-test the manifest/metrics code:

\`\`\`bash
python scripts/review_evaluation.py self-test
\`\`\`

Validate a prepared corpus before touching the phone:

\`\`\`bash
python scripts/review_evaluation.py validate \
  --manifest /secure/review-eval/manifest.json \
  --audio-root /secure/review-eval/audio
\`\`\`

## Physical run

Build and install the approved **benchmark/release-like** app and matching Android-test APK using the normal retained-data device procedure. Do not uninstall or clear app data. The verified review model package must already be installed.

Then run:

\`\`\`bash
python scripts/review_evaluation.py device-run \
  --serial <authorized-device> \
  --package org.athar.app \
  --manifest /secure/review-eval/manifest.json \
  --audio-root /secure/review-eval/audio \
  --apk android/app/build/outputs/apk/benchmark/app-benchmark.apk \
  --run-id review-<date>-<short-sha> \
  --results docs/testing/results/review-raw-<short-sha>.json \
  --report docs/testing/results/review-metrics-<short-sha>.json
\`\`\`

The host script:

1. validates every WAV digest;
2. stages only that run under the app's external \`review-eval/<run-id>\` directory;
3. discovers the installed instrumentation targeting the selected package;
4. passes the pushed git SHA and benchmark APK SHA-256 into the test;
5. runs \`ReviewEvaluationInstrumentedTest\`;
6. pulls raw per-case results and produces the aggregate report.

It does **not** grant microphone permission, uninstall/reset the app, clear data, change global settings or fabricate a result for a missing clip.

The instrumentation never uses the device microphone: frozen WAV bytes are fed directly into the same local model frontend. This makes speaker/error labels reproducible while separate Task 09.3/09.4 physical journeys still validate the real microphone lifecycle.

## Current acceptance state

The measurement path and metric definitions are implemented. Actual representative recordings are not stored in this repository, and this execution environment has not run the benchmark APK on the authorized API27 phone. Therefore false-accept/false-reject/latency/memory values remain **unmeasured**, not zero and not passed.
