# Release checklist
- [ ] Audited defects have tests and outcomes.
- [ ] Supported runtime, isolated migrations, least-privilege database and public-only document root verified.
- [ ] HTTPS JSON API works from Android without browser challenges.
- [ ] Registration/OTP/recovery SMTP delivery verified with authorized real recipient.
- [ ] Native debug build, release/R8 build configuration, signing instructions and no committed keys.
- [ ] Auth scope/rotation/revocation/ownership and admin CSRF/RBAC tests pass.
- [ ] All approved modules and Night Prayer acceptance criteria pass, including offline/sync/privacy.
- [ ] Arabic/English, RTL/LTR, font scale, tablets and accessibility verified.
- [ ] Quran mapping/provenance and model/font/provider licenses reviewed.
- [ ] Background audio, notifications, runtime permissions and lifecycle tested on devices.
- [ ] Managed-asset backup/restore and private-data deletion verified in staging.
- [ ] Measured release startup/frame/memory/network budgets and baseline profiles recorded.
- [ ] Host quotas, model delivery, failure states and maintenance instructions documented.

Unchecked gates are release blockers, not optional polish.

- [ ] Signed APK/AAB verified and release handoff package generated with \`scripts/release_handoff.py\`.
- [ ] Server/Admin ZIP comes from the exact clean release Git SHA and contains no local secrets.
- [ ] Release manifest/checksums independently reviewed.
- [ ] User/Admin/maintenance/known-limit guides delivered to the operator.
- [ ] Hosted signed-device evidence and rollback ownership recorded.
