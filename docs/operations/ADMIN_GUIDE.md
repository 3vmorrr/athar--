# Athar Admin guide

The Admin surface is a separate cookie-session security boundary under \`/admin\`. It does not reuse Android bearer tokens or the public website session.

## Roles and sessions

- Only \`admin\` and \`owner\` roles may enter the Admin shell.
- Owner-only destructive/recovery operations remain distinct from ordinary Admin editorial actions.
- Admin sessions have idle/absolute limits, CSRF protection and live role/suspension/session-version revalidation.
- Always log out from shared machines.

Never copy the Admin cookie into scripts, tickets, logs or release evidence.

## Editorial operations

Admin can manage the implemented editorial domains including scholars, series, lessons/upcoming events, books, Adhkar and discussion moderation.

Operational rules:

- preserve revision/conflict checks instead of overwriting concurrent edits;
- keep book rights/source metadata accurate;
- publication is an explicit state, not a side effect of editing;
- moderation decisions require the implemented reason/revision workflow;
- verify Arabic and English presentation after editorial changes that affect copy.

## Service settings

SMTP/Dorar credentials are encrypted at rest and must never be rendered back to the browser.

Diagnostics may show readiness/state, not the secret itself. Test email is restricted to the owner-authorized encrypted test recipient. Changing or clearing that recipient requires the implemented owner confirmation path.

## Night Prayer defaults

Admin controls only public defaults/source attribution/explanatory copy.

Do not attempt to inspect or export private Night Prayer sessions, notes, exemptions or worship history through Admin.

## Campaigns

The Campaign Center supports implemented Push, Email and combined draft/preview/scheduling/history behavior, subject to provider readiness.

Rules:

- targeting must remain consent-safe;
- do not target from Quran/Adhkar/Khatmah/Night Prayer/private worship completion history;
- use the explicit owner confirmation before scheduling;
- do not silently degrade a combined campaign when one channel is unavailable;
- transactional mail remains separate from campaign consent;
- do not mark Push delivery or Gmail delivery proven until their external acceptance tasks pass.

## User management and recovery

Use suspension/restoration/session revocation and owner recovery controls only for their intended account-management purpose. Privileged actions must leave their audit trail.

Production data deletion/recovery must follow the approved recovery runbook and backup verification. Do not test destructive recovery on live user data.

## Pre-release Admin acceptance

Use an authorized staging session to verify login/logout, non-Admin denial, CSRF rejection, one approved CRUD/audit journey, bilingual shell, service readiness and campaign/email failure states. Store only redacted evidence.
