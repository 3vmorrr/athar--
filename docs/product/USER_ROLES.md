# User roles and permission matrix
Retain the existing `user`, `admin` and `owner` roles. Additional moderator/editor roles are not introduced until permissions need separation; this avoids gratuitous enum/data migration.

| Capability | Verified user | Admin | Owner |
|---|---|---|---|
| Main Android content and own progress | Yes | Yes | Yes |
| Read/write another user's private worship logs | No | No | No routine UI/API |
| Public content CRUD/publication/moderation | No | Yes | Yes |
| Edit ordinary user profile/suspend with audit | No | Yes | Yes |
| Grant admin role/manage privileged accounts | No | No | Yes with reauthentication |
| Gmail/Dorar service secrets | No | Write-only | Write-only |
| Campaign send to verified opt-ins | No | Yes | Yes |
| Encrypted full backup/restore/local reset | No | No | Yes with step-up and safeguards |

Unverified users only receive verification-scoped mobile credentials and may verify, resend, recover or logout. Admin APIs verify scope, role and CSRF when cookie-authenticated independently on every request.
