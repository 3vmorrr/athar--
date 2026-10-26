# Feature migration matrix
| Legacy source | Destination | Preservation rule | Verification gate |
|---|---|---|---|
| PHP users + cookie sessions | shared identity service + native bearer sessions | Preserve hashes, IDs, usernames, roles and verification state | Login/revoke/OTP/ownership integration tests |
| Quran view + browser reading state | native reader + Room + API sync | Shared 114-surah index; separate book marks/position | All 6236 positions round-trip; boundary navigation |
| MP3Quran playback/timing | Media3 + reciter repository | Keep supported providers and timing availability semantics | Real reciter/surah switch; audio focus/background test |
| Browser model worker | independent native local inference adapter | Preserve license; no microphone upload | Graph compatibility, known recordings, uncertain feedback |
| rn0x adhkar counter | native offline counter | Preserve sourced counts; account-specific progress | Restart/counter/reset/retry tests |
| Scholars/series/lessons/books | API catalog + native screens + web admin | Preserve existing IDs, ordered associations and rights | CRUD + publication/parent visibility tests |
| Dorar proxy | shared provider service | Server-side token and source/grade metadata | Approved-provider integration or truthful unavailable state |
| Account/khatmah | shared services + native screens | Preserve plans, private aggregates, preferences | Two-account isolation, date and workload tests |
| `/admin` embedded view | independent admin entry/layout/auth | Preserve content, campaigns, moderation and owner operations | RBAC/CSRF/responsive/localization tests |
| Owner backups | shared backup service | Include new managed assets/tables; exclude live credentials | Isolated full round-trip and tamper rejection |
| No Night Prayer equivalent | native feature + private versioned API | Additive tables; explicit self-reporting; local exemptions | Domain/DST/sync/privacy tests |

The new scope explicitly asks for optional scholar biography/source and authorized PDF management, superseding earlier requests that simplified these forms. Restore these only as optional metadata/optional authorized upload, with direct links still supported. Record the implementation decision rather than silently deleting either workflow.
