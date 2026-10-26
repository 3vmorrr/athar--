# Scholar follow journey

Task: 12.2  
Implementation branch: \`parallel/admin-foundation\`  
Status: **IMPLEMENTED / ACCEPTANCE PENDING**

Scholar follow state is stored separately from favorites in an encrypted owner-scoped cache under \`private:follow:scholar:<id>\`.

Opening a visible scholar profile refreshes the authoritative \`/me/follows/scholar/<id>\` state. If the network is unavailable but an encrypted state exists, the UI clearly says it is showing the last saved state instead of claiming it is current.

Follow/unfollow uses optimistic local feedback. Before mutating, the repository retains the exact previous encrypted row. If the server request fails, that exact row is restored; a failed request never leaves a fabricated local follow state.

An unreadable encrypted row fails closed and disables mutation rather than overwriting retained ciphertext.

The profile identity surface is now a dedicated elevated card with the larger scholar image, name, explicit follow control, biography/source, and series/standalone filters. The follow action is distinct from save/favorite semantics and uses Arabic/English resources.

Account checks are performed before refresh/mutation and after authoritative persistence. Cache keys include the authenticated owner through the existing Room schema, so account switching cannot reuse another user's follow state.

Acceptance remains pending for Android build/unit/instrumentation gates and physical API27 RTL/LTR, offline, rollback, account-switch, large-font, reduced-motion, dark-mode and accessibility evidence.
