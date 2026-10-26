# Native Quran audio and radio

Status: TESTED for selection/configuration/foreground provider playback; full media acceptance remains IN_PROGRESS. The native Media3 service is independent of the Compose selectors. Current work retains reciter/edition/surah/ayah/mode/follow choices in the screen ViewModel across configuration recreation. Process-death selection persistence is not claimed by this increment.

A selection has its own media identity. A loaded radio item cannot masquerade as a selected surah; a different selection starts only through Play or a source change while that selected recording is actively playing. Changing a paused source does not autoplay. Available edition chips are filtered to the selected surah; a surah change retains a still-supported edition, otherwise chooses a supported one or disables playback. Whole-surah and one-ayah identities are distinct; follow display does not restart audio.

Whole-surah playback starts before the optional timing response. A verse clip requires a real matching timing record; missing timing does not fabricate boundaries. Requests cancel when another recording is selected. Live provider/timing/rotation acceptance remains an explicit gate, separate from pure selection tests. Background playback uses MediaSessionService; physical lock-screen, interruptions and network recovery are still acceptance tasks.

Sources reviewed2026-10-25:
- [Official MP3Quran API](https://www.mp3quran.net/ar/api): catalog, editions, recordings, timing and radio endpoints.
- [Android Media3 background playback](https://developer.android.com/media/media3/session/background-playback): service-owned player/session lifecycle and background controls.

Only approved HTTPS media hosts are accepted by the existing service. No YouTube extraction or API bearer tokens are passed to third-party media hosts. Religious source content remains unchanged; these controls do not infer worship completion.

Verification progress: the first build passes17 JVM tests (2 selection-policy cases plus15 existing), debug/test packaging and lint. The first device journey failed before the UI because its dedicated8004 server was stopped; startup/health were restored, and this failure is not classified as accepted behavior. The opt-in AudioPlaybackTest uses the real provider catalog/timing/media with the generated test account, and records actual playback separately from fixture/UI tests.

Executed provider evidence: the Arabic device run passes real whole-surah playback and timings, paused selection/no autoplay, explicit Play and active-source refresh. Arabic/English selection journeys both pass three recreations and radio separation. These are API34 emulator results, not physical-device background or network-loss acceptance. Current adaptive layout caps the scrolling control section to the available height and reserves a separate bounded area for follow-along or stations; its landscape rerun is pending.

Final gate: Arabic/English adaptive phone cases pass three recreations, real landscape scrolling and radio separation. The actual provider probe also passes unsupported-selection stop and a timed ayah clip, checking the relative play position against its real boundary. Lock-screen, audio focus/interruption, network loss and release performance remain open. See TEST_REPORT.md for named runs and earlier failures.
