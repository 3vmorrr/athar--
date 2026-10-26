# System architecture
Android (Kotlin/Compose) and independent Web Admin communicate over HTTPS with versioned PHP services. Only the PHP server accesses MySQL. Existing public PHP routes remain during migration. New server code lives under `backend/`; `public/index.php` dispatches `/api/v1` before legacy HTML and `/admin` uses a distinct shell. Future deployments may use separate origins/document roots without moving business rules into clients.

Core boundaries: identity, catalog, Quran metadata/providers, progress/sync, Night Prayer and privileged administration. Shared domain rules must not depend on HTTP globals. Repositories use PDO prepared queries. Controllers validate transport and authorization, call use cases and serialize stable JSON. Network providers are allowlisted and time-bounded. Do not introduce Redis, persistent workers or a paid inference service.

Adopt incrementally: canonical index/tests → versioned auth and error contract → native auth foundation → content/progress modules → independent Admin → Night Prayer offline/sync → end-to-end/performance gates. No destructive move of existing application files is required.
