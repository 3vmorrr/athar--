# Native Quran resume position

Status: TESTED for the explicit resume increment; shared background synchronization and broader device acceptance remain open. This extends the existing reader, canonical index, encrypted Room cache and revision-checked API. No Quran text or existing bookmarks are replaced.

The user explicitly chooses “Resume from here” beside an ayah. The owned position is encrypted and saved locally before network synchronization. Opening or scrolling the reader does not create worship/activity/completion records. A compact resume control selects the saved surah and scrolls to its exact ayah; repeated resume within the same surah also creates a new scroll request. Reading remains available when synchronization fails.

`ReadingRepository` uses the existing cached_documents table, scoped by account and API origin. A pending marker survives process/repository restart. Requests bind expectedUser to the captured account. Coalesced application-scope synchronization never cancels an in-flight mutation. No backend migration is needed: GET/PUT `/quran/position` already validates canonical references and expected_revision.

After a lost response, retry may receive409. The repository fetches current owned state: an equivalent target at a newer revision acknowledges the prior save without another mutation; a different target produces a conflict. Pending local data is not overwritten by refresh. The user may choose the account position or explicitly keep the device position against a freshly read revision. A further concurrent change can conflict again; no silent last-write-wins override is added.

Automatic retries run after local edits, verified sign-in and a successful refresh. Pending UI includes manual retry. System-level background/connection scheduling is still part of the shared synchronization phase; this increment does not promise continuous background synchronization or infer Quran-reading statistics.

Verification: ReadingStorageTest covers canonical validation, ciphertext storage, restart/offline retry, lost acknowledgement, both conflict choices, refresh preservation and account isolation. ReadingJourneyTest covers real fixture HTTP save, exact resume scrolling, activity recreation and unchanged Quran-session totals in Arabic and English. Actual results belong in TEST_REPORT.md; test source alone is not a pass.

Executed:6 native storage cases pass. Arabic and English UI journeys pass save/sync/exact scrolling and three consecutive activity recreations. A shared Arabic regression also passes reading, watch and adhkar (3 cases). Opening/resuming leaves Quran-session totals unchanged.

The first Arabic run exposed non-saveable tool mode; mode/search state now restores. An English follow-up exposed a Compose measure/layout crash during recreation. Programmatic dataset/cursor resets now use LazyListState.requestScrollToItem, which defers the request to the next remeasure. Both language cases and the shared regression pass after that change. The failed runs remain documented; passing debug emulator cases is not release performance certification.
