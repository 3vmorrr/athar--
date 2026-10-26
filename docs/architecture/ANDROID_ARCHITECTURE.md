# Android architecture
Native Kotlin/Compose, Material 3, unidirectional screen state and ViewModels; suspend repositories provide data through Flow. Organize feature packages around `core`, `auth`, `quran`, `knowledge`, `adhkar`, `account`, `nightprayer`, `media` and `sync`. Begin with simple explicit dependency construction; document the boundary and introduce Hilt only if lifecycle/testing complexity justifies it.

Room stores structured data and a transactional outbox; DataStore stores language/theme/onboarding only. Bearer/refresh secrets use Keystore-backed encryption and are excluded from Android backups. Retrofit/OkHttp uses bounded timeouts, cancellation and no credential logging. Media3 provides lawful Quran audio with foreground media notifications; official YouTube playback remains provider-controlled. Local microphone inference is an independent adapter with no upload capability. UI never performs blocking disk/network work.

Minimum API 26 is the initial compatibility target; compile/target 36 and installed JDK/Gradle are verified before a version is pinned. Compact uses bottom navigation, wider devices use a rail/detail layout. No WebView wrapper for Athar screens.

## Learning/player boundary

The learning package isolates official YouTube HTML inside a restricted WebView. Compose owns navigation, lesson information, first-party discussion, playlists and series. WebMessage callbacks accept only the application's HTTPS main-frame origin and bounded validated video/time data; no bearer credentials or private database payloads are passed into JavaScript. Native player updates cue a selected ID without reloading the whole application. Rotation/size configuration is handled by the responsive activity; locale changes still recreate localized UI. Opening full comments hides and pauses the provider view; closing restores it without autoplay. Inline comments remain below the visible player.

Lesson progress is encrypted and account-owned in Room cached_documents, with conflated sync triggers and application-scope checkpoints. Explicit completion creates a durable event ID; opened/ended events alone never complete a lesson. Shared SessionRepository requests support expectedUser so delayed lesson/adhkar/Night Prayer writes cannot acquire another account's credentials.
