# Native interface journey verification

The Android test APK contains an opt-in real-HTTP journey, `NativeUiJourneyTest`. It uses the application's actual session repository, Keystore, Room partition and Compose screens. It does not bypass authentication, verification or API authorization.

## Isolation

`tests/native_fixture.php create` requires an explicitly selected local server and a matching `ATHAR_TEST_DATA_DIR`. It creates a random `athar_test_native_*` schema, a schema-scoped `athar_fixture_*` account and clearly identified interface fixtures. No real email is sent. The fixture password belongs only to the generated test reader. The fixture database credentials are stored in ignored `storage/engineering/native-fixture.json`; never copy that file into documentation or Git.

Run the test API with `php -S 127.0.0.1:8004 -t public tests/native_router.php`. This router rejects other addresses/ports and database identifiers. It disables loading the site's `.env` and uses only the generated fixture account. The ordinary website and emulator API continue using their own configuration.

The test-only runner selects this API only with instrumentation argument `nativeApiSmoke=true`. There is no production endpoint override. The application partitions its token file and Room database by API origin, so fixture user IDs cannot share cached data with the real development server.

## Assertions

- Log in through the real versioned API with the verified fixture reader.
- Load actual published fixture lessons on the Arabic home screen.
- Open lesson details, favorite the lesson and confirm the owned API result.
- Use the visible back button to return to the home navigation.
- Open the private Night Prayer calendar and return to the home content.
- Open Quran reading, save the first ayah and confirm the owned bookmark.
- Recreate the activity and verify the bookmark still exists.

Screenshots are written to the debug application's private files directory. They are visual inspection evidence, not proof of accessibility, frame rate or production performance. Native storage tests separately cover all6236 offline verses, media-origin restrictions, encrypted Night Prayer edits and durable adhkar completion cycles.

The opt-in journey also accepts `nativeLanguage=ar|en`, `nativeTheme=light|dark` and a descriptive `capturePrefix`. Labels are resolved from the requested Android locale, rather than duplicating translated text in assertions. Device-size/font-scale changes are applied only to the dedicated emulator.

On Android13+, set the actual OS per-app language before starting the test process: `adb -s emulator-5554 shell cmd locale set-app-locales org.athar.app.debug --user 0 --locales en` (or `ar`). The pre-activity AppCompat call alone has no active delegate context in this instrumentation host and cannot reliably establish the platform locale. The first English attempt deliberately failed its localized-home assertion because the OS still reported Arabic; it is not counted as a passed English case. Test assertions remain locale-specific and must not silently fall back to either language.

## Executed visual cases — 2026-10-25

| Case | Result | Visual evidence |
|---|---|---|
| Arabic phone,1080×1920 at420dpi, light/100% font | PASS, full real-HTTP journey | [Home](screenshots/phone-home-ar.png), [calendar](screenshots/phone-calendar-ar.png) |
| Arabic phone, same dimensions, dark/130% font | PASS, full real-HTTP journey; calendar agenda fallback | [Home](screenshots/phone-home-ar-dark-large.png) |
| Arabic tablet,1600×2560 at240dpi, light/100% font | PASS, full real-HTTP journey; rail and3 lesson columns | [Home](screenshots/tablet-home-ar.png) |
| English tablet, same dimensions, light/100% font | PASS after explicit OS per-app locale setup; LTR rail/content, Arabic Quran text | [Home](screenshots/tablet-home-en.png) |

Screenshots contain deliberately labeled interface fixtures in a disposable database. They are not a claim that those fixture lessons/books are real religious publications. No real account data or credentials appear in them. These cases do not certify all screens, physical phones, TalkBack, landscape/foldable behavior or release frame timing.

## Cleanup

Stop only the fixture PHP process. Use `tests/native_fixture.php drop` with the same explicitly verified local server to remove only its recorded generated schema/account. Do not reset the real application database or run device data-clear commands against a user's phone. Preserve test failures and record actual execution results in TEST_REPORT.md before marking acceptance.

After the executed cases, the test emulator's size, density, font scale and app locale were restored, and the dedicated emulator was shut down. The isolated8004 server and its recorded disposable schema/account were removed; incomplete fixtures from two failed setup attempts were separately identified by exact schema names and marker rows before cleanup. The recovered website runtime on8081/8002/3309 was retained.
