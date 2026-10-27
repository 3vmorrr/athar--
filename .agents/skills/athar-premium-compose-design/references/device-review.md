# Athar device review

Use `scripts/android-install-debug.ps1` and `docs/development/ANDROID_PHYSICAL_DEVICE.md` from the repository root.

1. Read the installed ADB skill and its command reference. Discover transport, boot state, model/API, current user, and foreground activity. Keep serials/private captures in ignored `artifacts/premium-ui/`.
2. Verify the local `/api/v1/health`. Run `assembleDebug`, `testDebugUnitTest`, `assembleDebugAndroidTest`, `lintDebug` with `-PATHAR_API_BASE_URL=http://127.0.0.1:8002/api/v1/`, the installed Java runtime, and existing wrapper. Do not upgrade dependencies for environmental issues.
3. Install with `adb -s <detected> install --no-streaming -r <apk>`, verify the matching USB reverse, and launch the verified MainActivity. Preserve signature/data; no uninstall, data clear, or grant-all.
4. A secure credential prompt requires the user's physical action. Normal wakeup is allowed; no secure-lock bypass or timeout changes. Tests may keep their own activity awake while running.
5. Observe fresh XML/screenshot before each manual action, then verify the result. Use unique capture names. Avoid account email/password/OTP and unrelated apps. On Windows use Python subprocess binary stdout for screenshots instead of text-mode shell redirection.
6. Read fixture runner/setup before using test users. Restore normal app/API/locale afterward; stop fixture processes. Do not delete genuine records to make screenshots.
7. Capture Home, library/detail, Quran tools/reader, audio, prayer/calendar, adhkar, and non-sensitive settings. Record app-only motion when supported; report unsupported recording. Keep raw evidence private/ignored, and share only requested non-sensitive visuals.
8. Inspect app-scoped crashes/ANRs. Report exact tests, screenshot paths, unfinished API/device/accessibility matrices, and actual audio/network scope. Release profiling is required for frame-time claims.
