# Independent Admin shell acceptance — 17.2

Integration base: `37a24cd6b3b0078e1aa2d2952ef02e73ec056e13`.
Parallel commits:10384e6,3765e12,2617368,3610258,02640ee,6d74f37. KEEP WITH FIXES; no textual conflicts. Earlier1238dc4/29ee276 CI experiment is explicitly superseded (net empty diff).

The dashboard now renders through `admin/Views/layout.php` with independent CSS/JavaScript, bilingual chrome/navigation, isolated saved light/dark theme, skip link, flash/error surfaces and responsive sidebar. Source body editors remain Arabic until their corresponding content/recovery integration tasks; this accepts shell localization, not every future editor. Existing metrics/data queries have not been replaced; privacy-bounded dashboard acceptance is17.5.

## Corrections

- Fixed the source RTL selector, which targeted a nonexistent body direction attribute, and logical drawer positioning. Arabic opens from the right; English from the left.
- Added dialog semantics, localized close button, focus entry/trap/return, Escape/scrim dismissal, inert hidden drawer/background, body scroll lock and accessible breakpoint transition. Selected panel receives focus. No-JavaScript fallback exposes the sidebar/panels.
- Retained exact selected panel through locale/theme changes. Added a skip-link focus target.
- Corrected dark primary/danger foreground contrast,44px controls, inventory row/form styling and real metric layout lost when removing public CSS. Removed320px document minimum that created15px horizontal overflow alongside a vertical scrollbar.
- Preserved the existing campaign batch adapter in a separate Admin-only file; otherwise removing app.js would silently remove batch continuation/stop feedback. This is an interim existing-behavior adapter, not WP20 acceptance. No campaign was sent.
- Updated authentication checks to accept localized dashboard titles rather than hardcoded Arabic. Authentication regression coverage is retained.

## Evidence

- `python tests/admin_auth_http_test.py`:60 actual HTTP assertions PASS, including the prior49 authentication gates plus independent shell assets, absence of public script/chrome, drawer/skip targets, non-owner recovery omission, locale/theme persistence/validation and campaign adapter presence. Ignored log:`storage/engineering/admin-shell-http.log`.
- `scripts/test-backend.ps1`:20,000 disposable backend assertions PASS; log:`storage/engineering/admin-shell-backend.log`. No database migration/API/Android change. PHP syntax for layout/auth/dashboard/router and JS syntax for both Admin scripts PASS.
- Actual browser1440 desktop English light/Arabic dark: visible sidebar, localized chrome, panel selection, RTL placement and no horizontal overflow.1024 desktop;768 tablet;360 Arabic dark and320 English light: measured document scroll width no greater than client width after corrections. At320 client305/scroll305 with vertical scrollbar.
- Actual keyboard: Shift+Tab from close wraps to Audit; Tab wraps back; Escape restores menu toggle. Background becomes inert while open. Resizing an open360 drawer to1024 removes modality/inert state and focuses the selected visible navigation link. Selecting a panel closes the drawer and focuses its content. Theme/locale changes preserve `#admin-content`.
- Browser console: no captured warning/error. Source reduced-motion override reviewed; OS/browser reduced-motion preference was not changed. Screenshots in ignored `storage/engineering/`:admin-shell-desktop-en-final.png,admin-shell-desktop-ar-dark.png,admin-shell-360-ar-dark-drawer.png,admin-shell-320-en-light-drawer.png. The earlier320 screenshot documented the overflow before correction.
- No real user account/settings, public theme, email delivery or destructive owner operation changed. No physical Android gate applies to this web-only change. Future RBAC17.3, user tools17.4, privacy17.5 and recovery17.6 remain unaccepted.

The prior Windows Chrome automation confidence failure was not bypassed. The newly available in-app browser viewport capability enabled all size tests; temporary viewport override is reset after browser testing.
