# Signed reproducible Android release

Task: 24.3  
Implementation branch: \`parallel/admin-foundation\`  
Status: **RELEASE CONFIGURATION IMPLEMENTED / APPROVED SIGNING + PHYSICAL ACCEPTANCE PENDING**

No production keystore, password, key alias, certificate private key or local signing file is committed to the repository.

## Required release inputs

Actual \`assembleRelease\` / \`bundleRelease\` tasks require all of the following through Gradle properties or environment variables:

- \`ATHAR_API_BASE_URL\` — absolute HTTPS API base ending in \`/\`;
- \`ATHAR_VERSION_CODE\` — positive integer;
- \`ATHAR_VERSION_NAME\` — semantic version such as \`1.0.0\` or \`1.0.0-rc.1\`;
- \`ATHAR_RELEASE_STORE_FILE\` — path to an external keystore;
- \`ATHAR_RELEASE_STORE_PASSWORD\`;
- \`ATHAR_RELEASE_KEY_ALIAS\`;
- \`ATHAR_RELEASE_KEY_PASSWORD\`.

The values are not printed by the verification task. The keystore must exist outside Git.

The normal debug build keeps development defaults. The benchmark build stays release-like but continues to use debug signing for local performance measurement; it is not a production artifact.

## Production release properties

Example environment setup:

\`\`\`bash
export ATHAR_API_BASE_URL="https://api.example.org/api/v1/"
export ATHAR_VERSION_CODE="100"
export ATHAR_VERSION_NAME="1.0.0"
export ATHAR_RELEASE_STORE_FILE="/secure/path/athar-release.jks"
export ATHAR_RELEASE_STORE_PASSWORD="..."
export ATHAR_RELEASE_KEY_ALIAS="athar"
export ATHAR_RELEASE_KEY_PASSWORD="..."
\`\`\`

Windows PowerShell uses \`$env:NAME="value"\` instead.

Do not place these values in \`gradle.properties\`, \`local.properties\`, shell history checked into the repo, CI logs, screenshots or support tickets.

## Build

From \`android/\`:

\`\`\`bash
./gradlew clean :app:verifyReleaseConfiguration :app:assembleRelease :app:bundleRelease
\`\`\`

The release build is explicitly non-debuggable, R8 minified and resource-shrunk, and signs APK/AAB through the external production signing config.

Expected outputs are under:

\`\`\`text
android/app/build/outputs/apk/release/
android/app/build/outputs/bundle/release/
\`\`\`

## Verify package, version and signatures

Run the pure parser self-test first:

\`\`\`bash
python scripts/android_release_artifacts.py self-test
\`\`\`

Then verify the actual outputs:

\`\`\`bash
python scripts/android_release_artifacts.py verify \
  --apk android/app/build/outputs/apk/release/app-release.apk \
  --aab android/app/build/outputs/bundle/release/app-release.aab \
  --version-code 100 \
  --version-name 1.0.0 \
  --cert-sha256 <approved-release-certificate-sha256> \
  --output docs/deployment/results/release-1.0.0.json
\`\`\`

The verifier uses Android SDK \`apksigner\` and \`apkanalyzer\` plus JDK \`jarsigner\`. It checks:

- application id is exactly \`org.athar.app\`;
- APK and AAB agree on application id and version code/name;
- the installable release APK is not debuggable;
- APK cryptographic signature verifies and the signer certificate matches when supplied;
- AAB JAR signature verifies;
- full-file SHA-256;
- a canonical payload SHA-256.

The generated \`docs/deployment/results/\` directory is ignored by Git by default because release evidence may contain local paths/device/build metadata. Publish only the approved redacted evidence artifact in the release handoff task.

## Reproducibility

Build the same pushed commit twice in clean working directories/CI workspaces with the same:

- JDK/Android SDK/Gradle/AGP versions;
- release version code/name;
- HTTPS API base;
- dependency lock/resolution state;
- model/reference assets;
- production signing certificate.

Compare corresponding APKs or AABs:

\`\`\`bash
python scripts/android_release_artifacts.py compare \
  --first /build-one/app-release.apk \
  --second /build-two/app-release.apk
\`\`\`

The required reproducibility signal is \`canonical_payload_equal=true\`.

The canonical digest sorts ZIP entries and hashes their names/content while excluding only JAR signature metadata under \`META-INF\`. This detects real payload drift while avoiding a false failure solely from signature-container metadata. \`raw_bytes_equal\` is also reported when the toolchain happens to produce byte-identical signed artifacts.

A signer certificate mismatch is always a failure even if canonical payloads match.

## Prohibited release practices

Do not:

- commit or upload the keystore/private key;
- use debug signing for production;
- ship an HTTP API base;
- manually edit version metadata after build;
- disable R8/shrinking merely to make a release pass;
- claim reproducibility from one build;
- claim physical-device acceptance without the authorized device;
- copy release secrets into issue/PR comments.

## Acceptance still pending

This task's implementation is landed, but COMPLETE still requires:

1. two clean release builds from the same pushed commit;
2. canonical payload equality;
3. approved production certificate verification;
4. release APK/AAB installation/distribution checks as applicable;
5. authorized API27 physical journey and required Arabic/English/offline/error/retained-state checks;
6. final redacted release evidence.

Those cannot be fabricated in this environment without the approved signing key and authorized device.
