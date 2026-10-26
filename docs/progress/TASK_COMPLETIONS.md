# Task completion receipts

Each report follows implementation → tests/device → docs → review → commit → normal push → remote SHA verification. A commit cannot contain its own hash; its full SHA is reported immediately and added to this ledger in the following checkpoint. The queue is the current state, not a substitute for actual evidence.

| Task | Implementation commit | Verification |
|---|---|---|
|02.7 Cross-module lost-response/storage reopen|795cd3939725fe84732412b4d0e60a401ea20cdd|56 JVM/build/test APK/lint/316 resources; API27 Arabic19/English19/main API1; real HTTP post-commit losses/actual Room file reopen/exact activity totals; normal push/exact remote SHA verified |
|02.7.2 Permanent Night Prayer rejection recovery|3968a7de621de9eaeb1a8fec2a8e243d22317541|56 JVM/build/test APK/lint/316 resources; API27 Arabic10/English29/main API1; retained rejection/migration/reopen/actual422 repair/captures/no-secret review; normal push/exact remote SHA verified |
|21.18 Immersive hero specifications|5e46b37cfdba6ed64c27273f0aaafa1ce3eaaa4e|5 linked design specifications/25 links/9 planned artwork entries; queue149; documentation-only review; normal push/exact remote SHA verified |
|02.7.1 Captured-owner mutation dispatch|d9edd6f45f7d3f7c3a2532bf95df26b7c0f52b3f|53 JVM/build/lint/306 resources; Arabic10/English32/main API1 hardware PASS; no-secret review; normal push/exact remote SHA verified |
|02.1 Protected authoritative reads|6989a8a42ece5438972df13c186ea37c076b9874|29 JVM;11 hardware storage; Arabic/English protected/normal journeys; push verified |
|02.2 Owned persistent-work foundation|309efa59da9e3bae5921d727831e0085d9a3b874|38 JVM; Worker/HTTP1; scheduling/storage12; Arabic/English2 each; push verified. OS lifecycle gate remains02.8 |
|02.3 Disposable response cache|13402f60981a9b93dd1c14e998eaec23663747c3|42 JVM; hardware storage/cache15; Arabic/English2 each; push verified |
|02.4 Authentication request ownership races|c5062b6295b31f2ff3c49c332980dec3d4af78b4|47 JVM/build/lint; Worker/HTTP1; Arabic4/English4; queue validation and secret review; normal push and exact remote SHA verified |
|02.6 Watch/account local recovery|1895a096d3509fd288d1a70bb5974792dd8d0754|50 JVM/build/lint/306 resources; Arabic5/English15 and main API1 hardware PASS; captures/secret review; normal push/exact remote SHA verified |
|02.5 Durable Night Prayer conflict review|a9a937f796bf2154ed875cefd87d22b81b14331e|47 JVM/build/lint/305 resources; Arabic10/English15 hardware cases, main API1; captures and secret review; normal push and exact remote SHA verified |
|21.1 Premium shared primitives and controlled motion|981a9e1293b4270b5e9d2accc8f0a50174bd0763|47 JVM/build/lint; Arabic7/English8 component+regression, main API1, inspected captures/scoped startup; normal push and exact remote SHA verified |

Task02.4's final executed gates are recorded in SESSION_OWNERSHIP.md and TEST_REPORT.md. Its source/diff is committed only after those gates; the completion report records the resulting verified SHA.
