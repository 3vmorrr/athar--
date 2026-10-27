# Release artifacts and operations handoff

Task: 24.5  
Implementation branch: \`parallel/admin-foundation\`  
Status: **HANDOFF PACKAGING IMPLEMENTED / FINAL VERIFIED ARTIFACTS PENDING**

## Build the handoff

First complete Task 24.3 signed APK/AAB generation and verification. The repository must be clean and HEAD must be the exact release commit.

Self-test:

\`\`\`bash
python scripts/release_handoff.py --self-test
\`\`\`

Create the handoff:

\`\`\`bash
python scripts/release_handoff.py \
  --repo-root . \
  --git-sha <release-git-sha> \
  --apk android/app/build/outputs/apk/release/app-release.apk \
  --aab android/app/build/outputs/bundle/release/app-release.aab \
  --version-code <positive-version-code> \
  --version-name <semver> \
  --cert-sha256 <approved-release-certificate-sha256> \
  --output-dir artifacts/release
\`\`\`

The script refuses a dirty worktree, verifies APK/AAB via \`android_release_artifacts.py\`, and creates:

- verified signed APK;
- verified signed AAB;
- \`android-release-verification.json\`;
- deterministic \`athar-server-<version>-<sha>.zip\`;
- operations/deployment guides;
- \`release-manifest.json\`;
- \`SHA256SUMS.txt\`;
- deterministic outer \`*-handoff.zip\`.

The server/Admin package is created with \`git archive\` from tracked files only. It includes runtime source under Admin/app/backend/bin/database/public/shared plus \`.env.example\` and README, but rejects local config, storage, keystores, private keys, Google service config and service-account files.

\`artifacts/\` remains ignored by Git.

## Release handoff contents

The operator receives:

1. signed APK for the approved direct-install/testing channel;
2. signed AAB for the approved publishing channel;
3. server/Admin package from the exact same source commit;
4. checksums and release manifest;
5. Android signing/reproducibility guide;
6. deployment/environment/backup/hosted-acceptance guides;
7. user guide;
8. Admin guide;
9. maintenance/recovery runbook;
10. known limits and unresolved gate list.

## Required review before delivery

- verify \`SHA256SUMS.txt\` from a second trusted machine/process where practical;
- compare manifest Git SHA/version/signer fingerprint with the approved release record;
- inspect the server ZIP entry list for expected paths;
- run the repository secret/security checkpoint;
- confirm no blocked/acceptance-pending item is being described to users as production-proven;
- confirm rollback owner/contact and backup reference are recorded outside the public package.

## Final acceptance

24.5 must remain PARTIAL until a real handoff is generated from the approved signed artifacts and hosted/device acceptance evidence. The implementation of packaging and guides alone is not a release approval.
