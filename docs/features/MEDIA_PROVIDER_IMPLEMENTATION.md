# Media provider implementation increment

Build the versioned native media API around the official MP3Quran v3 catalog, not a hand-picked list. Preserve stable reciter/moshaf identifiers and actual supported surah lists. The provider documentation and live catalog were inspected on2026-10-25:243 reciters,115 timed reads and177 radio rows. These counts may change and are not product constants.

Only fixed provider API endpoints may be fetched server-side. Bound response size/time, verify TLS, disable redirects, cache public JSON privately and never proxy arbitrary user URLs. Returned media URLs must be HTTPS and validated against provider domain allowlists. Radio uses the provider-supplied qurango.net hosts as well as mp3quran.net. Never imply every recording has timing. A timing lookup failure must leave full-surah playback usable.

Routes planned in this increment: `/quran/reciters`, `/quran/audio`, `/quran/radios`, `/quran/timing`. Pagination/search are bounded. Audio resolves only a real catalog tuple reciter+moshaf+surah. Timing validates sorted verse intervals against canonical ayah counts and belongs to the resolved recording. The Android player must recreate tracking state on every recording change, use Media3 background playback and keep spoken Quran text separate from listening-only UI unless follow-along is explicitly chosen.

Tests use injected provider fixtures; live smoke checks are separate. Source: https://www.mp3quran.net/ar/api and https://mp3quran.net/api/v3/ayat_timing/reads . Implementation/test status is recorded in the main tracker; this specification is not completion evidence.
