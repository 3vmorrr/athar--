# Quran verse copy and share

Task: 03.6
Implementation branch: `parallel/admin-foundation`
Status: **IMPLEMENTED / ACCEPTANCE PENDING**

The reader’s More control and a search-result long press expose Copy and Share. Existing reading-position and bookmark controls remain available in the reader.

The payload is deliberately bounded and source-preserving:

- the ayah text is the exact `AyahText.text` loaded from the verified bundled Tanzil XML;
- no search normalization, diacritic removal, punctuation rewrite or generated Bismillah is applied;
- the reference is the canonical numeric `[surah:ayah]`;
- the source is fixed to `Tanzil Project 1.1 — https://tanzil.net/`;
- payload length is bounded and malformed/oversized input fails closed.

The Bismillah XML attribute is not silently prepended to a selected ayah because it is stored separately from that ayah's canonical text.

The action dialog keeps Quran text RTL and the numeric reference content-directed. Attribution is included in the copied/shared plain-text payload.

Copy uses the platform clipboard with a plain-text clip. Share uses Android `ACTION_SEND` with `text/plain`; no file URI, broad storage permission, account identifier, bookmark state or private reading progress is attached.

Android assemble/targeted-unit/test APK/lint/resources pass; acceptance remains pending for physical Arabic RTL/English LTR copy/share into representative apps, large font, landscape, dark mode and TalkBack evidence.
