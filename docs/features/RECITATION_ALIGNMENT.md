# Expected-passage recitation alignment

Task: 09.4  
Implementation branch: \`parallel/admin-foundation\`  
Status: **FUNCTIONAL EXPECTED-AYAH REVIEW IMPLEMENTED / PHYSICAL ACCEPTANCE PENDING**

The Android review engine now has a conservative expected-passage alignment core on top of Task 09.3's raw CTC tokens.

## Trusted corpus boundary

\`ReviewCorpusPassageReader\` reads only the already SHA-256 verified downloaded \`corpus\` artifact. The v2 corpus is streamed and materializes at most one surah JSON object at a time; Android does not create a second full corpus object just to review one passage.

Only 1–5 requested ayahs from one surah are retained. Each retained word contains the corpus-provided Mushaf glyph, phoneme string and plain form. Passage/word/phoneme counts are bounded before alignment.

## Token and phoneme boundary

\`ReviewCtcVocabulary\` is pinned to the reviewed 251-class Tilawa model vocabulary. Class 250 remains blank and never enters alignment.

A multi-character CTC token expands into individual heard phoneme characters while retaining the same frame and confidence margin for those characters. Quran display text is never reconstructed from CTC output.

## Conservative word alignment

\`ReviewPassageAligner\` performs a bounded phoneme edit alignment against the user-selected expected passage. Live alignment has a free expected tail so future words remain \`PENDING\`; finalization aligns through the expected end.

Substitution costs preserve the reviewed Tilawa phonetic equivalences: canonical madd/nasal forms, hamza variants, short-vowel closeness and known neighboring articulation groups.

Word states are:

- \`EMPTY\`: no audio evidence exists yet;
- \`PENDING\`: future expected content has not been reached;
- \`CONFIRMED\`: low phoneme distance **and** clear acoustic margin;
- \`UNCERTAIN\`: weak margin, moderate distance, or a boundary omission where the engine should abstain;
- \`WRONG\`: high-distance clear evidence;
- \`SKIPPED\`: an interior expected word has too little aligned evidence while later expected content was heard.

This is deliberately stricter than treating an acoustically weak exact decode as correct: low CTC margin produces \`UNCERTAIN\`, not \`CONFIRMED\`.

The DP is bounded to 7,500 heard characters and 12 million cells. It never uploads or persists heard audio/phoneme transcripts.

## Tests

Pure JVM coverage includes exact passage, insertion, deletion, substitution, repeated word, low-margin noisy audio, pending future words, empty take, multi-character token expansion, phonetic neighbor costs and bounded corpus extraction.

The next 09.4 commit integrates this core into a Quran reader review surface and preserves the same uncertainty semantics in UI.


## Quran reader integration

Long-pressing a Quran ayah now offers **Review recitation of this ayah** only when the verified model package is installed.

The review surface uses the selected canonical ayah as the expected passage. It never performs a whole-Quran guess. The canonical Tanzil text is shown above word-level corpus slots, and each slot starts empty before audio.

During capture, raw CTC tokens are expanded and aligned in memory after each inference window. Word surfaces update through the conservative states above. On stop, the bounded inference queue drains, the model tail is finalized, and a final expected-passage alignment is shown.

User-facing language intentionally says “clear match”, “unclear”, “possible mismatch” and “possible skipped word”. It never presents a low-confidence result as correct/incorrect fact.

The screen inherits the same contextual microphone lifecycle as 09.3: explicit permission request, background/revocation stop, four-chunk queue, overload fail-closed, no audio persistence/upload.

No haptic or success sound is enabled yet. Those cues are optional in Task 09.4 and remain omitted until physical accessibility/device validation can prove they are not misleading or disruptive.
