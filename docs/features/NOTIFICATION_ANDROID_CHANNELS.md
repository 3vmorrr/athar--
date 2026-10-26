# Android notification channels and permission

Task: 10.2  
Implementation branch: \`parallel/admin-foundation\`  
Status: **IMPLEMENTED / ACCEPTANCE PENDING**

## Stable localized channels

Android creates four stable channels without requesting runtime permission:

- \`athar.lessons.v1\` — explicit lesson reminders
- \`athar.routines.v1\` — personal Quran/Adhkar/Khatmah/Night Prayer routines
- \`athar.following.v1\` — followed scholar/series updates
- \`athar.campaigns.v1\` — separately consented Admin campaigns

Channel names and descriptions are Arabic/English resources. Re-running channel creation updates localized names while preserving the user's OS-level channel choices.

Campaigns intentionally remain on a different channel and consent boundary from personal reminders.

## Android 13+ permission state

\`POST_NOTIFICATIONS\` is declared for target API 36, but Athar does not request it on startup.

\`NotificationPermissionController\` exposes these states:

- \`NOT_REQUIRED\` below API 33
- \`GRANTED\`
- \`READY_TO_REQUEST\` before the first explicit request
- \`DENIED_CAN_RETRY\` when Android permits a contextual retry/rationale
- \`SETTINGS_REQUIRED\` after a denial/revocation that must be changed in system settings

The controller records only whether the OS prompt has ever been launched. It stores no account, worship, reminder or notification content.

Callers must invoke \`markRequestLaunched()\` only immediately before an explicit user-triggered permission request. The foundation deliberately provides no automatic launcher, so enabling a server preference cannot silently open an Android permission dialog.

## Revocation and denial

Permission health is read from Android every time rather than cached as granted. If a user revokes notification permission later, the state fails closed and directs future UI to system notification settings instead of pretending delivery remains available.

## Acceptance still required

The integration lane still needs build/lint/resource validation plus API 33+ device checks for first request, denial, retry/rationale where Android offers it, grant, later revocation, app settings return, Arabic/English channel labels, and preservation of OS channel choices.
