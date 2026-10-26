# Native scholar profile

The dedicated Compose profile is reached from watch metadata, series details or catalog navigation. It displays the published name, optional authorized portrait, collapsed biography and an explicit HTTPS biography source. Portraits use the existing first-party image endpoint; missing portraits do not create fabricated scholar imagery.

Series and standalone lessons are separate tabs with independent page state and lazy rows. They query the shared published catalog rather than relying on the legacy nested preview. Lesson filters inherit the series scholar only when no direct scholar is set. Standalone means `series_id IS NULL`; series lessons do not appear in both tabs. Counts exclude unpublished direct scholars. Nothing changes the public legacy website layout.

API: GET `/scholars/{id}/profile` returns metadata without nested lists; GET `/series?scholar_id={id}&page=...` and `/lessons?scholar_id={id}&standalone=1&page=...` return paginated rows. Verified identity remains required by the native API policy. Offline metadata follows the existing per-account cached-content boundary; errors use native retry/offline states. Arabic/RTL/light and English/LTR resources are paired.

Evidence: catalog suite40 assertions (including inherited scholar, standalone separation and hidden-scholar counts); native compilation/device acceptance recorded in TEST_REPORT.md, not implied by source completion.
