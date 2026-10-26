# Database audit

Local observed runtime: MariaDB 10.4.32; ledger includes 001–009. Existing local rows were not destructively modified by audit.

| Migration | Created tables / changes |
|---|---|
| `001_initial.sql` | users, quran_bookmarks, quran_positions, khatmah_plans, scholars, lessons, books, adhkar_items, audit_logs, schema_migrations |
| `002_lesson_series.sql` | series |
| `003_favorites_and_video_progress.sql` | user_favorites, video_progress |
| `004_owner_backups_and_auth_tokens.sql` | auth_tokens, backup_artifacts, backup_download_grants |
| `005_account_verification_and_preferences.sql` | Additive column/index changes |
| `006_book_catalog_metadata.sql` | Additive column/index changes |
| `007_accounts_email_and_activity.sql` | platform_settings, account_daily_stats, lesson_comments |
| `008_email_campaigns.sql` | email_campaigns, email_campaign_recipients, email_send_events |
| `009_scholar_images.sql` | Additive column/index changes |

No versioned mobile token/session, shared rate bucket, Night Prayer/session-sync, series-favorite or suspension schema exists at baseline. Existing `file_key` and scholar biography/source columns must remain for data preservation. User favorites use a polymorphic target ID without target foreign keys; validate target existence/type server-side. Backup images omission and session-only rate limits are tracked defects. Test new schema in `athar_test_*`, not the live `athar` schema.
