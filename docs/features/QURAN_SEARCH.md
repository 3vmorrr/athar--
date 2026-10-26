# Quran search journey

Task: 03.2  
Implementation branch: `parallel/admin-foundation`  
Status: **IMPLEMENTED / RUNTIME ACCEPTANCE PENDING**

Search remains completely local to the verified packaged Quran assets. The
canonical Uthmani ayah text is never rewritten for matching or highlighting.

## Search normalization

A separate mapped normalization layer removes Quranic combining marks/tatweel,
normalizes Alif variants, Alif Maqsura/hamza-on-Ya, hamza-on-Waw and Ta Marbuta,
and collapses whitespace. Every normalized character retains its source index,
so a normalized match is projected back onto the original canonical string for
visual highlighting.

## Paging and exact opening

Results are paged 20 at a time with stable canonical order, explicit previous/
next controls, total/page counts and local loading/error states. Tapping a
result leaves search mode and opens the exact Surah/ayah in the existing Mushaf
reader. Long press still exposes the existing ayah actions. Search does not
update reading progress unless the user explicitly chooses the save-position
action.

The search UI uses the original ayah text with highlighted spans only; no
normalized text is rendered as Quran text.

## Acceptance still required

Do not mark 03.2 COMPLETE until the integration lane runs the normal native
build/unit/androidTest/lint/resource gates, Arabic RTL and English LTR device
journeys, offline search, empty/error/retry, paging, exact result opening,
large-text/landscape/reduced-motion checks, Quran source-integrity verification,
diff/secret review and exact remote-SHA verification.
