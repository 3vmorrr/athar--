# Night Prayer bounded reader integration —2026-10-30

08.3 adds actual reading from saved plans and range-based history entries, with independent device-private prayer markers. Physical, native HTTP, lifecycle/process and full navigation acceptance remain open; component and storage fixtures do not close those gates.

## Source reconciliation

The pinned parallel7392e93 planner only displays continuation derived from all historical ranges, with record/copy/clear callbacks. It has no bounded reader or independent retained prayer position. Current main dfe7407 supplies canonical plan IDs and atomic recording receipts; this change uses those contracts rather than transferring the divergent repository. Existing premium primitives, history editing/deletion/rejection/conflict routes and canonical Quran assets remain.

`NightReadingScope` binds owner, API namespace, source kind, stable ID and exact global bounds. `NightReadingPositionCodec` strictly reconstructs a bounded schema under `private:night-reader:v1:`. Plan IDs require UUIDv4; session IDs accept the existing API UUID versions1–8. Position revisions use transactional compare-and-set; clearing writes a tombstone, including an absent first marker, to fence delayed saves. All source and marker reads validate the captured SessionLease. Writes check the existing deletion fence and recheck generation inside the Room transaction. Unreadable/foreign frames remain retained and cannot become empty writable defaults.

Plan replacement or clear removes only the strictly validated marker for the replaced plan, atomically. A corrupt marker prevents that destructive write. A session deletion prevents later reads/saves through that source; its independent minimal marker remains retained until owned cleanup. A session range edit requires reopening the current range, reviewing old/current canonical references and explicitly clearing the previous marker revision. Notes, worship counts and global reading positions are never changed by marker operations.

Continuation considers only receipts for this plan and the current nondeleted recorded ranges linked by those receipts. It unions clamped intervals without skipping gaps; unrelated older plans/history cannot complete the current plan. Editing or deleting an associated record updates coverage. An explicit marker takes precedence; full recorded coverage permits reading again without recording another prayer.

The bounded reader uses a dedicated state holder, not the global Quran ViewModel. `QuranAyahText` extracts the unchanged existing verse/bismillah rendering and is used by both readers. Quran bytes, Amiri Quran, RTL direction and established line heights are preserved. The user font size reaches the prayer reader through MainShell. Stable global verse keys identify rows. Background marker updates do not jump the active reading position; initial continuation and explicit start/resume controls scroll deliberately.

App wiring captures owner/generation/source/bounds. `NightReaderContent` takes immutable state and typed callbacks separately. Runtime scroll and confirmations use `remember`, without private plaintext SavedState. Explicit encrypted markers survive database reopen; this is not evidence of OS process/reboot acceptance. Parent list state is held outside the reader branch for back navigation. Late acknowledgements, source invalidation, stale confirmations, older observations and conflict-refresh results cannot replace newer validated state or restore removed private presentation.

Owned export adds the optional `device_night_reading_positions` section, validates owner/environment/schema/unique scope keys and accepts earlier export copies. Existing account cleanup includes these private rows. No Room migration, dependency, permission, backend contract or Quran asset changed.

## Evidence boundaries

The initial reader wrapper compiled but D8 rejected an inline Compose early return. The wrapper was split into a normal composable with an ordinary return, without dependency changes. The failed log is retained locally. Subsequent source refinements require the final consolidated receipt; intermediate APKs are not evidence for later edits.

The08.2 isolation test used a generic `reading-position` canary. This checkpoint corrects it to the actual `quran-reading-position` key and reruns the planner storage suite. New reader storage tests directly verify the actual Quran and Khatmah keys, history/outbox immutability and absence of non-auth HTTP. The original08.2 receipt is preserved as dated evidence, with this correction explicit.

Final scoped evidence:50/50 JVM cases across7 classes, debug/test APKs PASS, lint0 errors/68 warnings and516 paired resources.20/20 API34 Room/Keystore/export cases PASS against final APKs;7/7 Arabic RTL and7/7 English LTR Compose cases PASS in dark theme at1.5 font scale. The34 executions represent27 unique cases,14 newly added instrumentation cases; counts are not summed across reruns. Exact source/APK/JUnit/log digests are pinned in ACTUAL_IMPLEMENTATION_AUDIT_INPUT.json. Source1c9f11a7e3974938420822e5359bbdfd631d3f52 was normally pushed and exact remote-head verified.08.3 is IMPLEMENTED/INTEGRATED/ACCEPTANCE_PENDING/PARTIAL. Full native route/back/reentry, real HTTP, process/reboot, physical API27, landscape, light theme, accessibility and performance acceptance remain queued. No current foreground-app restoration is claimed; earlier normal app relaunch policy rejection was not retried through another mechanism.


Follow-on08.4 static review found a monotonic timer/UI candidate but no owner/environment-bound frame or generation-fenced transactional CAS. Starting can overwrite retained state, checkpoint failures are hidden, and recorded session creation and timer clearing are separate writes. Its earlier fully implemented candidate classification is corrected to partial before integration; this reader checkpoint supplies no timer test evidence.
