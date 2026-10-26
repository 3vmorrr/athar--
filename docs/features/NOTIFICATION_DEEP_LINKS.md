# Notification deep-link contract

Task: 10.3  
Implementation branch: \`parallel/admin-foundation\`  
Status: **IMPLEMENTED / ACCEPTANCE PENDING**

## Internal allowlisted destinations

Notification taps use explicit \`MainActivity\` intents with two private app extras:

- expected owner id
- one allowlisted destination token

Supported stable tokens are:

- \`lesson:<positive id>\`
- \`series:<positive id>\`
- \`quran\`
- \`adhkar\`
- \`night\`
- \`inbox\`

Arbitrary URLs, relative paths, unknown content types, non-positive ids and private Night Prayer session identifiers are rejected.

The contract intentionally does not accept a generic route string. A notification therefore cannot inject an arbitrary Navigation Compose destination.

## Authentication and account restoration

\`MainActivity\` retains a valid notification launch while authentication is restoring or signed out. Dispatch occurs only after a verified \`SignedIn\` state exists.

Every launch is bound to the owner id captured when the notification was created. If a different account is signed in when the tap is restored, the target is consumed without navigation. This prevents a notification created for one account from opening another account's private destination.

Existing public/content destinations are restored through the current navigation contract:

- lesson -> native lesson detail
- series -> native series detail
- Quran -> Quran hub
- Adhkar -> Adhkar
- Night Prayer -> Night Prayer

The \`inbox\` token is already reserved and owner-bound, but WP16 owns the actual private inbox screen. Until WP16 registers that screen, an inbox token fails closed rather than being misrouted to a different destination. No current sender emits inbox notifications yet.

## Activity reuse

Notification intents use \`CLEAR_TOP | SINGLE_TOP\`. \`MainActivity.onNewIntent\` reparses the allowlisted target, so tapping a notification while Athar is already open does not require a second task stack.

## Acceptance still required

The integration lane must run build/navigation tests and device journeys for cold start, warm app, signed-out restore, same-owner restore, different-owner fail-closed behavior, malformed extras, back navigation, process recreation and future WP16 inbox registration.
