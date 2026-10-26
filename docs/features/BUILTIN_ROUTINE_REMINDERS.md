# Salawat and Friday reminder engine

Task 13.3 engine foundation on \`parallel/admin-foundation\`.

Salawat and Friday are optional time-based routines only. The app never stores, infers or displays whether the user performed them, and no guilt/streak/failure state exists.

Rules are encrypted under \`private:builtin-routine-reminders:v1\`. Salawat supports selected ISO weekdays; Friday is structurally fixed to ISO Friday. Both store an IANA timezone and optional quiet hours, retain only the next occurrence, expire at the next local midnight, and use stable per-day idempotency keys.

The shared \`salawat_friday\` consent category is still rechecked immediately before delivery. Taps use an allowlisted owner-bound \`home\` deep-link token rather than arbitrary navigation.

Physical/UI acceptance remains pending; the companion UI commit exposes the controls from the account reminder surface.
