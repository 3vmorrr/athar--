# Open native quality findings

These remain in P7 acceptance; implementation of a feature is not a claim that every device/release gate has passed. Separate observed defects from source-review risks.

| ID | Evidence level | Finding | Required follow-up |
|---|---|---|---|
| QF01 | Observed screenshot | Small English phone navigation clips the long account label | Use a concise localized navigation label and inspect font scaling; preserve full account screen titles |
| QF02 | Observed tablet screenshot | Full comments/composer can span the entire tablet width | Review bounded content widths and keyboard placement while preserving accessible close actions |
| QF03 | Source-review risk | Encrypted cache observation maps decode directly into shared flows | Inject unreadable ciphertext/invalidated key and verify recoverable UI without deleting the retained record or crashing the application |
| QF04 | OTP regression tested; other numeric forms pending | Shared reset/verification normalizer passes Arabic/Eastern/ASCII/leading-zero paste cases in Arabic/English UI | Continue equivalent input coverage for khatmah, ranges and numeric forms; do not normalize passwords |
| QF05 | Not yet executed | Actual official playback, landscape retention and checkpoints pass; provider fullscreen/back and physical-device behavior remain open | Exercise supported provider controls, Back restoration, unavailable videos and network loss without bypassing provider requirements |
| QF06 | Debug diagnostic only | Low-memory headless SwiftShader sample had64.26% janky frames | Recheck isolated editor/player updates, then profile a release-like build on supported hardware; do not certify smoothness from emulator functional tests |
| QF07 | Observed reading screenshot | Reader resume/tool/section controls occupy substantial phone height | Review a compact or collapsible native control hierarchy while retaining exact-resume and accessible actions; do not alter Quran text to save space |
| QF08 | Observed adaptive audio screenshot | Shared brand header and bottom navigation consume substantial landscape height | Evaluate a compact shell/navigation rail; current bounded scrolling passes control reachability, not final visual polish |

Host resource failures are retained in LESSON_WATCH_ACCEPTANCE.md. One tall-phone retry encountered invalid-credentials output while the host also failed to allocate CLR memory. The isolated fixture account was subsequently checked: valid Argon2id hash, matching password and no suspension; the fresh-guest rerun passed without changing the password or weakening authentication.
