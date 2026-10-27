# Known limits and unresolved release gates

This file distinguishes implemented behavior from evidence that still requires an external provider, approved host or physical device. A blocked gate is a release blocker for the affected promise, not permission to invent a PASS.

## Current blocked gates from the execution queue

### 02.8 — OS reconnect and process/reboot acceptance

Real constrained dispatch after reconnect, process recreation and reboot still needs authorized runtime evidence without clearing app data.

### 03.8 — Approved Dorar provider contract

Only an approved Arabic hadith search API/contract may be used. Do not replace the missing approval with scraping.

### 15.3 — Authorized physical Push delivery

Push delivery requires an authorized free FCM project/configuration and approved physical test device. Server/client plumbing is not proof of real provider delivery.

### 20.5 — Authorized Gmail inbox proof

Gmail/App Password delivery must be confirmed by the explicitly authorized real test inbox. Do not send acceptance mail to arbitrary addresses.

### 24.1 — Free-host capability proof

A genuinely free compatible production host still needs proof for HTTPS API behavior, outbound providers/SMTP/cron/storage and capacity limits. Documentation/configuration is not hosting proof.

## Acceptance-pending areas

Many queue rows are \`PARTIAL\` because implementation landed but their full automated/physical matrix is not recorded. Do not reinterpret PARTIAL as COMPLETE.

Examples include release startup/jank/memory/background budgets, signed Android release, hosted signed-device acceptance, Admin/Email runtime acceptance and accessibility/device matrices.

## External dependencies

Some features rely on external services such as approved Quran/audio/hadith providers, SMTP and Push infrastructure. The app must present provider/network failure states rather than fabricating content or delivery.

## Release format limits

An Android App Bundle (AAB) is a publishing artifact, not the directly installable device package. Use the verified APK/device distribution path appropriate to the approved release channel.

## Recitation review

On-device recitation review is an assistive feedback feature. It is not a religious ruling, certification of recitation correctness or replacement for a qualified teacher. Model/runtime accuracy and latency claims require the recorded evaluation/device evidence.

## Support boundary

Support can request non-sensitive reproduction information. Users/operators should never be asked to provide passwords, OTPs, bearer tokens, Admin cookies, release private keys or private worship content.
