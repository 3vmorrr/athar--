# Series follow journey

Task: 12.3  
Implementation branch: \`parallel/admin-foundation\`  
Status: **IMPLEMENTED / ACCEPTANCE PENDING**

Series follow uses the encrypted owner-scoped FollowRepository introduced for scholars. Its cache key is \`private:follow:series:<id>\`.

The series summary exposes Follow series and Save to favorites as two separate actions. Follow drives the durable relationship used by future subscription facts; favorite keeps the pre-existing save/bookmark behavior. Neither writes the other's state.

Follow state refreshes on entry. If the network cannot confirm the latest value, retained encrypted state remains visible with an explicit saved-state notice. Follow/unfollow is optimistic, but a failed server mutation restores the exact prior encrypted row and surfaces rollback feedback.

The series identity/progress area is grouped into one elevated card with title, follow control, separate favorite control, scholar identity, progress, description and resume action, using the existing Material/RTL/reduced-motion system.

Acceptance remains pending for Android build/unit/instrumentation and physical RTL/LTR, offline rollback, account switching, favorite-vs-follow independence, large-font, landscape, dark mode and accessibility evidence.
