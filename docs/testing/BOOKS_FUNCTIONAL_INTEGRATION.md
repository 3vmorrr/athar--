# Books functional integration

<!-- actual-implementation-audit:start -->
> **Current authority —2026-10-29:** [149-task actual source audit](../progress/ACTUAL_IMPLEMENTATION_AUDIT.md). 112 tasks have implemented source (73 in main, 39 only unmerged); 19 are accepted/closed, 54 need current acceptance only. Seven earlier native closures reopened after source changes. These metrics overlap; use the audit's exclusive149 partition. Older counts, percentages, next-task statements and dated test results below are historical receipts, not current inventory or newly executed tests.

<!-- actual-implementation-audit:end -->

## Baseline and source review

Refetched target5fb3cddd84f420d913882ecf8ded8942df6b97d9 and parallel7392e93b088990c3478cf32197781f831eb10f83. Source commits760bfb9 (rights detail), aa0124f/dbf9f0c/0680197 (download and ownership), a08d60f (management), e65b253/ac90c3f (PDF), a49d71a (source URL),2854cc0/ed84cb9/8b5725e (deletion/reader storage) are implementation input. New source books/ files were absent from the preliminary old-module inventory and are explicitly reviewed here.

Keep current editorial covers/catalog/detail identity and current centralized backend BookAccess rights checks; the source deletes that newer helper and inlines older checks. Adopt functional metadata/source/rights behavior without replacing current covers with a different hero. Native source/download URLs require credential-free public HTTPS, standard port and no private/reserved literal destinations; real DNS answers and each redirect also require validation in the downloader. Immediate policy gate: main compile +2 JVM policy cases PASS;372 paired resources PASS.

## Durable owned storage corrections

The source uses owner ID alone across API environments and shared per-book temporary files. Storage now scopes by API partition +owner +book, uses unique request generations, immutable completed files and an atomic checked manifest. A durable cancellation/revocation fence is checked under the same owner lock as final publication. Account deletion cannot race a late publish into recreating owned files. A failed replacement leaves the prior manifest/copy intact. Corrupt manifest/checksum state preserves bytes until explicit removal. Pure filesystem race/reopen/integrity/namespace tests PASS, including Windows delayed-unlink recovery; network fixtures also verify redirects, cancellation and failed replacement. Publication is fenced once the durable revocation marker is written; remote-deletion receipt/process-death recovery remains a separate07.4 acceptance gate.

## PDF review

Source PDF rendering and closure race on different threads; opening follows a separate hash check and mutable path. These require reconciliation with verified immutable descriptors and serialized renderer lifetime. Android's [PdfRenderer reference](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer#PdfRenderer(android.os.ParcelFileDescriptor)) recommends an isolated process with minimal permissions for untrusted documents, and documents descriptor ownership/closing and worker-thread construction. The implemented reader uses a private isolated service, verified read-only descriptors and bounded raw pixel pipes; no app files, API credentials or image-format parser are exposed to the renderer. API29+ binds a separate isolated instance per document; API26–28 uses the supported private isolated service. The extra receiving-descriptor flag check is guarded to API30+, while the parent opens read-only descriptors on all supported versions. Native PDF rendering and encrypted Room progress were exercised on API34 below; API26/27 and other physical acceptance remain open.

Hardware/provider/process-death/WorkManager/PDF/accessibility acceptance remains open. Prior QA emulator attempts did not expose a transport. No downloaded public file or synthetic fixture proves a real provider journey.


## Integrated behavior and local gate — 2026-10-29

Kept central BookAccess and adopted/fixed public-destination validation for catalog, direct download, legacy web and existing Admin form validation; public HTTPS/no URL credentials/standard port/private and reserved address rejection now agree with native policy. Relevant PHP gate:46 rights/Admin destination +51 catalog/progress assertions PASS. No migration.

Foreground WorkManager data-sync downloads use a separate bearer-free HTTP client, public DNS/redirect checks, a200MiB per-file bound and32MiB free-space reserve. Progress updates are throttled to actual percentage changes and reset on retry. Only committed data reaches100%; checksum/manifest failures preserve prior copies. Stale uncommitted parts are disposable after a new durable request generation. [Official long-running worker guidance](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running) supports foreground downloads and requires the declared runtime service type; Android16 quota behavior and real long-running provider downloads remain device gates, not claimed PASS.

Account Saved books inventory persists independently of public catalog availability, with encrypted title metadata and explicit availability recheck before each new download. Cancellation/removal is scoped by API partition, owner and book. Removing a PDF preserves its encrypted reading position. Page state is bound to owner/partition/book/exact SHA256 in the existing private Room store, validated before overwrite and protected by transactional actor/revocation checks. Unreadable records stay intact. Unpartitioned source-branch page preferences are retained and quarantined rather than assigned to an unproven API realm. FileProvider fallback grants read-only access to the selected verified immutable copy.

Debug APK/test APK/lint PASS (0 errors;59 warnings),29 focused JVM cases PASS,400 paired resources PASS. Initial lint caught API30-only fcntlInt; fixed with a supported-version guard and reran the gate. No lint baseline/suppression added.

## Actual API34 instrumentation

The dedicated read-only QA emulator booted after releasing this task's idle Gradle daemon and using1024MiB/SwiftShader/no Vulkan. Both APKs installed with -r; no uninstall/data reset.20 instrumentation cases PASS in9.416s:PDF service1, encrypted book storage/actor transactions2, counter storage5, Quran navigation3/search5, reading storage1 and protected storage3. PDF bytes were generated fixture data and rendered through the real installed isolated Binder service; the test verified pixels, private/isolated manifest flags and rejection of corrupt copies. This proves that bounded fixture journey, not a live provider, UI visual/permission/background/reboot journey or phone acceptance. Diagnostic logs and device inventory remain ignored/private. The emulator session was closed after the batch.

Follow-up07.3 must include new encrypted book page/title records in private export.07.4 must close remote-delete/local-fence process-death recovery.07.6 must queue genuine native completed-download activity only after verified publication; URL lookup is not completion and the current worker sends no invented activity. Package acceptance remains PARTIAL until these related gates and actual provider/physical/accessibility evidence are closed.

Milestone regression after WP03–WP06:all114 JVM cases PASS (32s);all20 isolated backend suites PASS (21,013 executed assertions). Existing provider fixtures are repeated inside the media-catalog extension; counts describe executed checks, not unique scenarios. No live SMTP/provider send.
