# Local reminder consent recheck

Foundation hardening for Tasks 10.x / 13.x.

Every local reminder worker now re-reads the encrypted owner-scoped notification preference snapshot immediately before policy evaluation and posting.

Delivery requires the master switch, current consent version and the reminder's exact category to still be enabled. Missing or unreadable preference state fails closed. A reminder retained while consent is paused is not posted merely because it was scheduled earlier.

This applies to lesson, Quran, adhkar, Khatmah, Night Prayer and future local reminder categories without requiring each feature to duplicate consent checks.
