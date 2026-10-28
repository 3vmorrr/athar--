# Audio functional integration

## 04.1 ownership and session activity increment

Reviewed sourcec61e58c and the current persistent premium player. Retained Media3 focus/noisy handling and current mini/expanded controls. Adopted the source immutable session-activity PendingIntent. Both branches only cleared queues on unsigned/pending states, so direct SignedInA→SignedInB could retain former media. The service now rechecks verified current ownership before connections/commands/media replacement and clears the queue on owner changes. The application controller carries its captured owner as an additional fence and rechecks it before/after connection and controls.

Media3's verified ControllerInfo UID/trust authorizes callers; owner hints alone never authorize a UID. Trusted system controls remain available; only the app UID may replace media. A complete invalid media request is rejected so filtering cannot change its selected index. Existing URI allowlisting remains. No schema, backend API, dependency or manifest permission changes.

Installed Media3 1.8.0 AAR signatures were inspected for callbacks, commands and connection hints. [Official service reference](https://developer.android.com/reference/androidx/media3/session/MediaSessionService#onGetSession(androidx.media3.session.MediaSession.ControllerInfo)) documents main-thread callbacks and placeholder media-button callers; UID checks therefore belong to real connection/command callbacks, while onGetSession checks current authentication. Framework notification/system callers are not forced to present app-specific hints.

Immediate gate: main Kotlin compilation +17 JVM cases (ownership4/session13) PASS. Extended media replacement policy and its actual service wiring then passed5 ownership cases. No physical/IPC end-to-end/background/lockscreen proof is inferred. Next related increments hydrate retained session metadata, expose focus state and recover bounded source requests; then run the subsystem APK/test APK/lint gate.
