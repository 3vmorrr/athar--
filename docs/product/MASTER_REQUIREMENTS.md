# MASTER ENGINEERING PROMPT — ATHAR
## Full Repository Audit, Native Android Application, Independent Admin Dashboard, Shared Backend, Documentation-First Development & Production-Quality UX

### YOUR ROLE

Act as a coordinated senior engineering team consisting of:

- Principal Software Architect
- Senior Native Android Engineer
- Senior PHP/MySQL Backend Engineer
- Senior Web Application Engineer
- Senior UI/UX and Motion Designer
- Application Security Engineer
- Database Architect
- Performance Optimization Engineer
- QA Automation Engineer
- DevOps and Free-Hosting Compatibility Specialist
- Technical Documentation Lead

You are responsible for inspecting, designing, implementing, testing, documenting, and preparing the entire Athar ecosystem for deployment.

Do not merely propose an architecture, generate mockups, or provide pseudocode. Produce working, maintainable, tested software.

Do not claim a feature is complete unless it has been implemented and verified.

---

# 1. PROJECT IDENTITY AND REPOSITORY

**Project:** Athar — أثر

**Repository:** https://github.com/3vmorrr/athar--

**Existing branch:** main

**Product:** An Arabic-first Islamic learning, Quran, worship, and personal progress platform.

**New deliverables:**

1. A real, production-quality Android application.
2. A completely separate, responsive web-based administration dashboard.
3. A secure shared backend and versioned API.
4. A maintained MySQL/MariaDB database.
5. Complete architecture, product, deployment, and developer documentation.
6. Automated tests and reproducible build instructions.
7. A scalable foundation for future features.

The existing repository is the source of truth for understanding historical behavior, data structures, visual identity, and existing integrations.

However, existing code must not be treated as automatically correct.

**IMPORTANT:** The user will provide the official Athar logo as an attachment. Inspect and use that asset. Preserve the existing Athar color palette, branding, and overall visual identity. Do not invent a replacement brand or randomly change the established colors.

---

# 2. NON-NEGOTIABLE ENGINEERING RULES

1. Inspect the entire repository before making architectural decisions.
2. Write documentation before implementing or rewriting major features.
3. Preserve existing data and useful functionality.
4. Identify and fix broken or poorly implemented functionality.
5. Require authentication before accessing the Android application's main content.
6. Keep the Android application and administration dashboard separate.
7. Use a secure, reusable API and shared database.
8. Design every module for future extension.
9. Prioritize excellent UI/UX and real-world performance.
10. Use free and open-source development tools and libraries.
11. Avoid mandatory paid APIs, proprietary subscriptions, and paid SDK dependencies.
12. Do not introduce advertising, subscriptions, or premium features.
13. Support Arabic and English throughout both applications.
14. Use proper RTL and LTR behavior.
15. Protect religious content accuracy, provenance, and licensing.
16. Never hardcode credentials or expose privileged APIs.
17. Implement meaningful automated tests.
18. Do not replace actual functionality with placeholder screens.
19. Do not silently remove existing features.
20. Never modify production data destructively without explicit approval.

Do not confuse "the code exists" with "the feature works."

---

# 3. PHASE ZERO — COMPLETE REPOSITORY FORENSIC AUDIT

Before implementing anything, inspect the repository systematically.

Examine all directories, relevant files, dependencies, database migrations, routes, backend handlers, frontend JavaScript, stylesheets, external APIs, security mechanisms, tests, and documentation.

At minimum, inspect:

```text
README.md
.env.example

app/
  Views/
  backup.php
  helpers.php
  mailer.php

public/
  index.php
  router.php
  assets/css/
  assets/js/
  assets/images/
  assets/fonts/
  assets/models/

database/
  migrations/

docs/
  planning/

bin/
tests/
```

Pay particular attention to:

```text
public/index.php
public/assets/js/app.js
public/assets/js/knowledge.js
public/assets/js/i18n.js

public/assets/js/recitation-worker.js
public/assets/js/recitation-audio-worklet.js

app/Views/pages/quran.php
app/Views/pages/adhkar.php
app/Views/pages/books.php
app/Views/pages/lessons.php
app/Views/pages/series.php
app/Views/pages/scholar.php
app/Views/pages/dorar.php
app/Views/pages/account.php
app/Views/pages/auth.php
app/Views/pages/admin.php
```

Also inspect every database migration from `001` through `007`, plus any additional migrations found in the current repository.

## Required audit procedure

For every existing feature:

1. Locate its UI implementation.
2. Trace its frontend interactions.
3. Trace the backend route and handler.
4. Identify the associated database tables.
5. Identify external API dependencies.
6. Inspect validation and authorization.
7. Inspect error handling.
8. Identify dependencies on browser-only functionality.
9. Identify potential security issues.
10. Determine whether the implementation is reusable.
11. Determine how it should behave in Android.
12. Determine whether the admin dashboard needs corresponding controls.
13. Identify missing tests.
14. Record the findings.

Create a feature inventory with these states:

- Implemented and verified
- Implemented but not verified
- Partially implemented
- Broken
- Missing
- Blocked by an external dependency
- Requires redesign

Do not fabricate audit results.

If the project cannot be run, distinguish static-code findings from runtime-tested findings.

## Repository safety

Before making changes:

- Inspect the Git working tree.
- Identify uncommitted work.
- Record the current commit SHA.
- Preserve original migrations and assets.
- Create a safe development branch if permitted.
- Never force-push.
- Never overwrite user changes.
- Never delete the existing web implementation without an approved migration plan.

---

# 4. DISCOVER, VERIFY, INSTALL, AND USE AGENT SKILLS

You must research and use relevant specialized agent skills before implementation.

Explore:

https://skills.sh/

Use the skills CLI when supported:

```bash
npx skills find android
npx skills find kotlin
npx skills find jetpack-compose
npx skills find mobile-ui
npx skills find frontend-design
npx skills find accessibility
npx skills find php
npx skills find mysql
npx skills find security
npx skills find testing
npx skills find performance
```

Research additional official Android engineering skills, including Jetpack Compose performance guidance.

Identify useful skills for:

- Android architecture
- Kotlin and Jetpack Compose
- Mobile UI/UX
- Motion and animation
- Arabic RTL design
- Backend API engineering
- PHP security
- Database optimization
- Authentication
- Automated testing
- Accessibility
- Performance profiling
- Architecture reviews
- Technical documentation

Before installing a skill:

1. Verify that it exists.
2. Inspect its source and publisher.
3. Read its `SKILL.md`.
4. Review scripts and executable instructions.
5. Check relevance and compatibility.
6. Avoid suspicious, unnecessary, or untrusted skills.
7. Install it only if useful and permitted.

Do not blindly execute third-party scripts.

Document the selected skills, their sources, installation steps, and intended use in:

`docs/engineering/AGENT_SKILLS.md`

Use installed skills during implementation, rather than merely listing them.

If a skill installation fails, document the reason and proceed using authoritative official documentation.

---

# 5. RESEARCH PROFESSIONAL DESIGN REFERENCES

Before implementing UI, research modern, high-quality application design patterns.

Study:

- Material Design 3
- Official Android UI guidance
- High-quality educational applications
- Quran reading applications
- Islamic knowledge applications
- Professional media and video applications
- Premium-quality dashboard interfaces
- Arabic-first mobile typography
- Modern onboarding experiences
- Motion design principles
- Accessibility and reduced-motion standards

Use official design documentation and reputable design references.

You may inspect design galleries such as Behance and Dribbble for inspiration, but do not copy copyrighted designs or visual assets.

Create:

`docs/design/DESIGN_RESEARCH.md`

Document:

- Design references
- Navigation patterns
- Component patterns
- Animation principles
- Typography decisions
- Responsive behavior
- Accessibility considerations
- Rejected patterns and their reasons

## Mandatory brand preservation

Extract the actual brand palette from the existing CSS and approved assets.

Preserve the official colors.

You may introduce semantic shades and neutral surfaces derived from the approved palette, but do not replace the visual identity.

The result should feel like an exceptionally polished version of Athar, not an unrelated application.

---

# 6. DOCUMENTATION-FIRST DEVELOPMENT

**Do not begin major implementation before creating the initial documentation set.**

Create or update the following structure:

```text
docs/
├── README.md
├── audit/
│   ├── REPOSITORY_AUDIT.md
│   ├── FEATURE_INVENTORY.md
│   ├── ROUTE_AND_API_AUDIT.md
│   ├── DATABASE_AUDIT.md
│   ├── SECURITY_AUDIT.md
│   ├── DEPENDENCY_AUDIT.md
│   ├── LEGACY_BUGS.md
│   └── MIGRATION_MATRIX.md
│
├── product/
│   ├── PRODUCT_VISION.md
│   ├── FEATURE_SPECIFICATIONS.md
│   ├── USER_JOURNEYS.md
│   ├── USER_ROLES.md
│   ├── ACCEPTANCE_CRITERIA.md
│   └── FUTURE_FEATURES.md
│
├── architecture/
│   ├── SYSTEM_ARCHITECTURE.md
│   ├── ANDROID_ARCHITECTURE.md
│   ├── BACKEND_ARCHITECTURE.md
│   ├── ADMIN_ARCHITECTURE.md
│   ├── API_CONTRACT.md
│   ├── DATABASE_SCHEMA.md
│   ├── AUTHENTICATION.md
│   ├── OFFLINE_AND_SYNC.md
│   └── EXTENSIBILITY.md
│
├── design/
│   ├── DESIGN_RESEARCH.md
│   ├── DESIGN_SYSTEM.md
│   ├── BRAND_GUIDELINES.md
│   ├── MOBILE_SCREENS.md
│   ├── ADMIN_SCREENS.md
│   ├── MOTION_GUIDELINES.md
│   └── ACCESSIBILITY.md
│
├── features/
│   ├── QURAN.md
│   ├── RECITATION_REVIEW.md
│   ├── ADHKAR.md
│   ├── BOOKS.md
│   ├── SCHOLARS.md
│   ├── LESSONS.md
│   ├── DORAR_SEARCH.md
│   ├── AUTHENTICATION.md
│   ├── ACCOUNT.md
│   ├── KHATMAH.md
│   ├── COMMENTS.md
│   └── ADMINISTRATION.md
│
├── engineering/
│   ├── AGENT_SKILLS.md
│   ├── CODING_STANDARDS.md
│   ├── TEST_STRATEGY.md
│   ├── PERFORMANCE.md
│   ├── SECURITY.md
│   └── DEFINITION_OF_DONE.md
│
├── deployment/
│   ├── FREE_HOSTING_RESEARCH.md
│   ├── DEPLOYMENT_GUIDE.md
│   ├── ENVIRONMENT_VARIABLES.md
│   ├── BACKUP_AND_RECOVERY.md
│   └── RELEASE_CHECKLIST.md
│
└── progress/
    ├── IMPLEMENTATION_PLAN.md
    ├── TASK_TRACKER.md
    ├── DECISION_LOG.md
    └── CHANGELOG.md
```

Preserve existing planning documentation and update it where necessary.

## Documentation requirements

Every feature specification must include:

- Purpose
- Existing implementation
- Known defects
- Required new behavior
- Android screens
- Admin dashboard controls
- Backend endpoints
- Database requirements
- Input validation
- Loading states
- Empty states
- Error states
- Offline behavior
- Security considerations
- Accessibility requirements
- Performance requirements
- Test cases
- Acceptance criteria
- Extension points

The documentation must reflect the actual implementation.

Never mark a feature complete solely because its documentation exists.

Maintain the task tracker throughout development.

---

# 7. TARGET SYSTEM ARCHITECTURE

The new Athar ecosystem must consist of three independently maintainable components.

```text
                    ATHAR ECOSYSTEM

        ┌──────────────────────────┐
        │   Native Android App     │
        │   Kotlin / Compose       │
        └────────────┬─────────────┘
                     │
                     │ HTTPS / JSON API
                     │
        ┌────────────▼─────────────┐
        │    Shared PHP Backend    │
        │  Auth / Content / Sync   │
        │   Validation / Services  │
        └────────────┬─────────────┘
                     │
             ┌───────▼───────┐
             │ MySQL/MariaDB │
             └───────────────┘
                     ▲
                     │
        ┌────────────┴─────────────┐
        │   Independent Web Admin  │
        │   Arabic + English       │
        │   Responsive Dashboard   │
        └──────────────────────────┘
```

The diagram is conceptual; implement secure access boundaries rather than exposing the database to clients.

## Android technology

Prefer:

- Kotlin
- Jetpack Compose
- Material 3
- MVVM or an appropriately justified unidirectional architecture
- Clean modular architecture without unnecessary abstraction
- Kotlin Coroutines and Flow
- Retrofit/OkHttp or a justified equivalent
- Room for structured local persistence
- DataStore for lightweight preferences
- Dependency injection with Hilt or a justified alternative
- Modern Android navigation
- Media3 where appropriate and permitted
- Android Keystore-backed secure credential handling

Use stable, compatible library versions verified against current official documentation.

Do not blindly introduce experimental dependencies.

Do not implement the Android app as a WebView wrapper.

A limited WebView may be used only where an external provider officially requires a web-based integration.

## Backend

Prefer retaining and modernizing the existing PHP/MySQL backend if the repository audit confirms this is technically suitable.

Do not rewrite the backend in another language without a documented, compelling reason.

The backend must expose a clean, versioned REST API.

For example:

```text
/api/v1/auth/...
/api/v1/me/...
/api/v1/quran/...
/api/v1/adhkar/...
/api/v1/books/...
/api/v1/scholars/...
/api/v1/series/...
/api/v1/lessons/...
/api/v1/comments/...
/api/v1/khatmah/...
/api/v1/search/...
/api/v1/admin/...
```

These are conceptual route groups, not a final contract. Finalize actual routes after auditing existing functionality.

Use predictable JSON responses, pagination, consistent error codes, and API versioning.

Avoid duplicate business logic between the Android app and admin dashboard.

## Proposed repository organization

After auditing existing structure, propose a safe migration toward:

```text
athar/
├── android/
├── admin/
├── backend/
├── database/
├── docs/
├── tests/
├── scripts/
└── README.md
```

The exact structure may differ if a better layout is justified.

Preserve historical code until migration is verified.

---

# 8. MANDATORY ANDROID AUTHENTICATION

The new Android application requires login.

No user may access its main content before authentication.

The authentication experience must include:

- Welcome screen
- Login
- Registration
- Email verification
- Password recovery
- Password reset
- Secure session restoration
- Logout
- Account deletion
- Session expiry handling

## Registration fields

At minimum:

- Display name
- Unique username
- Email
- Password
- Password confirmation

Preserve or safely migrate existing username constraints.

Validate input on both client and server.

Use secure password hashing, session management, and appropriate rate limiting.

## First launch flow

```text
Launch
   ↓
Animated Splash
   ↓
First-launch check
   ↓
Onboarding (first launch only)
   ↓
Authentication check
   ↓
Login / Registration if unauthenticated
   ↓
Main application
```

Returning authenticated users should bypass unnecessary onboarding and login screens.

The server must enforce authentication independently of UI navigation.

Do not assume that hiding screens secures the API.

Use secure, revocable sessions and avoid long-lived secrets in ordinary preferences.

Email OTP functionality must handle SMTP unavailability honestly. Do not claim verification messages were delivered when the provider failed.

---

# 9. PREMIUM-QUALITY ONBOARDING AND MOTION DESIGN

Create an elegant, memorable first-launch experience.

Use the supplied Athar logo.

## Splash screen

- Official logo
- Brand-consistent background
- Subtle, polished motion
- No unnecessary loading delay
- Proper Android splash behavior
- Smooth transition into onboarding or the application

## Onboarding

Create approximately 3–4 concise screens introducing:

1. Quran reading and listening
2. Islamic knowledge and learning
3. Adhkar and personal worship tools
4. Private progress and continuity

Include:

- Custom vector illustrations or legally reusable assets
- Elegant transitions
- Smooth page indicators
- Skip and continue controls
- Clear Arabic and English copy
- Correct RTL behavior
- Reduced-motion accessibility support

Use lightweight motion techniques such as Compose animations, vector animations, or appropriately licensed Rive/Lottie assets when justified.

Do not introduce heavy animations that increase startup latency, consume excessive battery, or cause frame drops.

Avoid excessive glowing effects, intrusive animations, or generic template aesthetics.

Motion must improve comprehension and perceived quality.

---

# 10. ANDROID DESIGN SYSTEM

Create a unified, reusable design system.

It must cover:

- Color tokens
- Typography
- Quran typography
- Spacing
- Shapes
- Elevation
- Cards
- Buttons
- Input fields
- Navigation
- Dialogs
- Bottom sheets
- Search
- Media controls
- Loading skeletons
- Empty states
- Error states
- Offline indicators
- Success feedback
- Motion tokens

Use the existing Athar palette.

## Arabic-first UX

Arabic is the default language.

English must be available through application settings.

Implement:

- Correct RTL layout
- Correct mixed Arabic/English rendering
- Proper numeric formatting
- Accessible Arabic typography
- Consistent translations
- Layout mirroring where appropriate
- Language persistence

Preserve the existing Quran font licensing requirements.

Never substitute ordinary UI typography for Quranic text if it harms readability or diacritics.

## Navigation

Design a clean mobile navigation structure suitable for the actual information architecture.

Potential primary destinations include:

- Home
- Quran
- Knowledge
- Adhkar
- My Account

Do not force every feature into bottom navigation.

Use nested navigation, contextual tabs, search, and well-designed detail screens.

The navigation must remain intuitive as new features are added.

## Themes

Support a polished light theme.

Support a dark theme if it can be implemented without compromising readability or delaying core functionality.

Both themes must respect the Athar brand.

---

# 11. COMPLETE QURAN MODULE

Implement and verify all existing Quran features.

## Quran navigation

- Browse all 114 surahs
- Surah navigation
- Ayah navigation
- Juz navigation
- Hizb navigation
- Page navigation
- Direct opening of a selected ayah
- Remember last reading position
- Save bookmarks
- Resume reading

## Reading experience

- Excellent Arabic Quran typography
- Adjustable font size
- Comfortable line spacing
- Smooth scrolling
- Clear verse numbering
- Fast navigation
- Copying and sharing where permitted
- Persistent reading preferences
- Correct loading and error states

Do not display fabricated or incomplete Quran text.

Validate surah, ayah, page, juz, and hizb mappings against authoritative sources.

## Quran search

- Arabic text search
- Appropriate Arabic normalization
- Relevant results
- Surah and ayah references
- Direct navigation to results
- Clear coverage limitations
- Pagination or bounded result loading

Use only content and APIs with verified usage rights.

## Recitation playback

- Select reciter
- Play an ayah
- Play a selected range where supported
- Play an entire surah
- Pause and resume
- Seek where supported
- Loading indicators
- Playback failure recovery
- Appropriate background playback
- Android media notification controls when permitted
- Audio focus handling
- Interruption handling
- Efficient buffering

Do not claim offline recitation unless audio has been lawfully downloaded and cached.

## Tafsir

- Select ayah
- Display source-attributed tafsir
- Preserve author and book information
- Preserve edition/source metadata when available
- Handle unavailable tafsir gracefully
- Never generate or fabricate religious interpretation

## Daily verse

- Daily verse selection
- Source and reference
- Open in reader
- Open tafsir
- Listen to recitation
- Share where permitted

Ensure predictable daily selection and avoid unnecessary network requests.

---

# 12. QURAN RECITATION REVIEW — ON-DEVICE AI

The repository contains an existing Quran recitation review implementation.

Inspect:

```text
public/assets/models/
public/assets/js/recitation-worker.js
public/assets/js/recitation-audio-worklet.js
```

The current implementation uses an ONNX-based Zipformer model and supporting Quran phoneme data.

Research its original source, license, model compatibility, input/output format, and current inference logic.

## Requirements

- Request microphone permission appropriately.
- Record or stream audio safely.
- Run inference locally when feasible.
- Avoid uploading microphone audio.
- Provide understandable feedback.
- Distinguish uncertain recognition from confirmed errors.
- Allow replay and retry.
- Handle noisy environments.
- Handle unsupported devices.
- Avoid blocking the main UI thread.
- Monitor memory and battery consumption.

Investigate ONNX Runtime Android or another compatible free local inference runtime.

Do not assume the web inference pipeline can simply be copied to Android.

## Model delivery

The current model assets total approximately 75 MB.

Evaluate:

- Bundling in the APK
- Optional first-use download
- Local caching
- Integrity verification
- Versioned model updates
- Storage management
- Low-memory device behavior
- Free-hosting file size restrictions

Prefer an architecture that avoids unnecessary initial APK size while remaining reliable.

**Important:** Preserve model license notices and non-commercial restrictions. Do not assume the model can be redistributed under arbitrary terms.

If the model cannot be deployed legally or reliably, document the blocker and implement a truthful unavailable state rather than a fake AI feature.

---

# 13. ADHKAR AND DUA MODULE

Implement:

- Adhkar categories
- Dua categories
- Salawat categories
- Search
- Reviewed source-attributed content
- Detailed dhikr screens
- Repeat counts where supported
- Interactive counter
- Reset counter
- Completion feedback
- Personal progress tracking
- Accessibility-friendly controls
- Local persistence where appropriate
- Synchronization with the authenticated account

The counter must not lose progress unexpectedly.

Avoid creating invented repetition counts.

Preserve religious source attribution.

Design the dhikr experience for one-handed mobile use.

Make it calm, readable, and distraction-free.

---

# 14. ISLAMIC BOOK LIBRARY

Implement:

- Curated books
- External catalog search where permitted
- Search by title
- Search by author
- Search by keywords
- Book details
- Author information
- Source information
- Rights metadata
- Book favorites
- Book downloads
- Local download management where supported
- Appropriate PDF reading support
- Offline access to lawfully downloaded files
- Download progress and cancellation
- Empty and error states

Differentiate bibliographic search results from books that are actually available for download.

Do not expose downloads when publication or distribution rights are missing.

Use safe file handling and secure download endpoints.

---

# 15. SCHOLARS, LESSONS, AND SERIES

Implement a complete educational content system.

## Scholars

- Scholar directory
- Scholar detail screen
- Biography
- Profile image where available
- Source reference
- Associated series
- Standalone lessons

## Series

- Series directory
- Series detail screen
- Associated scholar
- Description
- Ordered lessons
- Lesson counts
- Save to favorites
- Resume learning

## Lessons

- Lesson details
- Official YouTube player integration
- Ordered playlist
- Resume playback where supported
- Completion tracking
- Favorites
- Comments
- Related content
- Playback failure recovery

## YouTube compliance

Use only supported official playback mechanisms.

Do not:

- Download YouTube video/audio without authorization
- Remove YouTube branding
- Suppress advertisements
- Bypass playback restrictions
- Claim unsupported background playback
- Promise exact resume timestamps when the provider cannot guarantee them

Provide an official YouTube fallback when embedded playback is unavailable.

---

# 16. COMMENTS AND MODERATION

Implement first-party Athar comments.

Requirements:

- Authenticated posting
- Appropriate text length limits
- Safe output escaping
- Spam and abuse rate limiting
- Pending moderation state
- Admin approval
- Admin rejection or hiding
- Clear moderation status
- Audit history
- User ownership checks

Never expose pending comments to unrelated users.

Do not import YouTube comments.

Keep the system extensible for future reporting and moderation tools.

---

# 17. DORAR AND ISLAMIC KNOWLEDGE SEARCH

Preserve the existing unified Dorar integration concept.

Search categories include:

- Hadith
- Tafsir
- Aqeedah
- Fiqh
- Ethics
- Etiquette
- Islamic history

Research current official API documentation and access requirements.

Use a server-side proxy.

Never expose provider tokens in Android or browser code.

Maintain:

- Source attribution
- Reference links
- Hadith grades as supplied by the source
- Pagination where supported
- Provider-specific errors
- Unavailable states
- Rate-limit handling

Do not scrape protected content or silently substitute unapproved endpoints.

If API access is not approved, provide a properly designed unavailable state and admin configuration workflow.

---

# 18. USER ACCOUNT AND PERSONAL DASHBOARD

Implement a polished account area containing:

- Profile information
- Username
- Email verification status
- Profile editing
- Password change
- Language settings
- Theme settings
- Reading preferences
- Quran bookmarks
- Last reading position
- Favorite books
- Favorite lessons
- Favorite series
- Khatmah plans
- Private activity statistics
- Data export
- Account deletion
- Logout

## Personal statistics

Preserve existing metrics:

- Quran sessions
- Completed lessons
- Completed adhkar
- Book downloads
- Active days

Statistics must be accurate, privacy-conscious, and backed by real recorded activity.

Do not display invented analytics.

Do not create public worship leaderboards.

---

# 19. KHATMAH PLANNING

Implement a full Quran completion planning experience.

Features:

- Create a plan
- Choose target completion date
- Select appropriate workload units
- View daily target
- Track completed amount
- View remaining amount
- Recalculate daily workload
- Pause
- Resume
- Edit
- Restart
- Finish

Ensure correct date calculations and clear behavior when users miss planned days.

Avoid guilt-based streaks or pressure.

Store plans securely and synchronize across authenticated devices.

---

# 20. COMPLETELY SEPARATE ADMIN DASHBOARD

Create a professional web administration application.

It must not be embedded inside the Android app.

It must have its own routes, authentication interface, authorization controls, navigation, layout, and deployment instructions.

The dashboard should use the same official Athar branding and color palette, but a desktop-first administrative information architecture.

It must support Arabic and English, with full RTL/LTR handling.

## Dashboard overview

Show real, useful metrics:

- Total users
- Verified users
- Published scholars
- Published series
- Published lessons
- Published books
- Reviewed adhkar
- Pending comments
- Content awaiting publication
- Recent administrative activity
- Relevant service health information

Use charts only where they improve understanding.

Avoid fabricated numbers.

## Content management

Provide complete CRUD workflows for:

- Scholars
- Series
- Lessons
- Books
- Adhkar
- Supported content metadata
- Source references
- Publication status

Every form must include validation, clear errors, success feedback, and confirmation for destructive operations.

## Scholars management

Allow administrators to:

- Create scholars
- Edit names and biographies
- Manage profile images
- Manage source references
- Publish/unpublish
- Associate series
- Associate standalone lessons

## Series management

Allow administrators to:

- Create series
- Select scholar
- Edit metadata
- Add lessons
- Reorder lessons
- Publish/unpublish
- View associated content

## Lesson management

Allow administrators to:

- Add YouTube links
- Validate video IDs
- Edit titles and descriptions
- Associate scholars
- Associate series
- Configure ordering
- Manage visibility
- Review comments

## Book management

Allow administrators to:

- Add books
- Edit metadata
- Upload authorized PDFs
- Configure direct download links
- Record licensing and rights
- Manage covers
- Publish/unpublish
- Remove content safely

## Adhkar management

Allow administrators to:

- Add adhkar
- Edit text
- Manage categories
- Configure sourced repeat counts
- Record references
- Review content
- Publish/unpublish

## Users management

Provide:

- Searchable user list
- Account status
- Verification status
- Role information
- Authorized support actions
- Suspension where supported
- Controlled role management
- Relevant audit history

Never expose passwords or private worship histories to administrators.

## Moderation

Provide:

- Pending comments queue
- Comment context
- Approve
- Hide/reject
- Moderator identity
- Decision timestamp
- Reason and audit history

## Service settings

Provide secure configuration for:

- SMTP
- Email verification
- Password recovery
- Approved Dorar API credentials
- Other future integrations

Never return stored secret values to the browser.

## Owner operations

Preserve and improve existing owner-only capabilities:

- Encrypted full backups
- Backup verification
- Authorized download grants
- Backup retention
- Restore workflow
- Audit history
- Development-only database reset

Production database reset must remain forbidden server-side.

Preserve the existing security model and strengthen it where necessary.

## Dashboard UX

Implement:

- Responsive sidebar
- Mobile-friendly navigation
- Clear top navigation
- Search
- Filters
- Pagination
- Sortable data tables
- Accessible forms
- Confirmation dialogs
- Toast feedback
- Loading skeletons
- Empty states
- Error states
- Useful bulk operations with authorization checks
- Keyboard navigation
- Accessible contrast
- Clear destructive-action warnings

Do not use generic, visually inconsistent admin templates.

---

# 21. ADMIN ROLES AND PERMISSIONS

Implement and document a permission matrix.

Suggested roles:

- User
- Moderator
- Content Editor
- Administrator
- Owner

Adapt these roles to existing database and application behavior.

Use server-enforced role-based or permission-based authorization.

The owner should control sensitive role grants.

Every privileged operation must be validated independently.

Log sensitive administrative actions without storing unnecessary private user information.

---

# 22. INTERNATIONALIZATION

Both Android and Admin must support:

- Arabic
- English

Arabic must be the default.

Implement proper translation resources, not hardcoded text scattered throughout the code.

Support:

- Dynamic language switching
- Correct RTL/LTR
- Proper layout direction
- Mixed-language content
- Localized validation errors
- Localized empty states
- Localized notifications
- Localized onboarding
- Localized account flows
- Localized admin dashboard

Use one consistent translation terminology guide.

Preserve the existing translation work where correct.

---

# 23. EXTENSIBILITY AND FUTURE FEATURES

Athar will gain additional features.

Design accordingly.

Each feature should have clearly separated:

- UI
- Domain logic
- Data models
- Repository/data access
- Backend routes
- Authorization
- Tests
- Documentation

Avoid tightly coupling unrelated features.

Create reusable patterns for adding:

- New content types
- New navigation destinations
- New admin modules
- New API integrations
- New user settings
- New learning tools
- New media capabilities

Document exactly how developers can add a new feature.

Create:

`docs/architecture/EXTENSIBILITY.md`

Do not overengineer a complex plugin runtime unless it is genuinely needed.

Prefer simple, modular, maintainable code.

---

# 24. PERFORMANCE — TOP PRIORITY

Performance is a release requirement, not an optional improvement.

Optimize:

- Cold startup
- Warm startup
- Navigation latency
- Screen transitions
- Scrolling
- Search response
- Quran rendering
- Audio playback
- Image loading
- Database queries
- Network usage
- Memory consumption
- APK size
- Battery usage
- Admin dashboard responsiveness

## Android performance practices

Use:

- Lazy loading
- Efficient list rendering
- Stable Compose state
- Avoidance of unnecessary recompositions
- Image caching
- Efficient database queries
- Background dispatchers
- Lifecycle-aware collection
- Network timeouts
- Request cancellation
- Appropriate retries
- Baseline Profiles
- Release-mode benchmarking
- R8 optimization
- Performance tracing

Set measurable performance budgets after establishing baseline measurements on representative devices.

Measure rather than invent performance claims.

Avoid unnecessary dependencies.

## Backend performance

Implement:

- Proper database indexes
- Pagination
- Query optimization
- Efficient API payloads
- Appropriate caching
- Request limits
- Timeouts
- Graceful provider failures

Do not require Redis, a dedicated worker server, or other paid infrastructure for the basic deployment.

---

# 25. OFFLINE-FIRST WHERE PRACTICAL

Design meaningful offline behavior.

Prioritize:

- Cached Quran content where licensed
- Reading position
- Bookmarks
- Adhkar
- Preferences
- Downloaded authorized books
- Cached metadata

Use a local database for structured data.

Handle synchronization carefully.

Implement conflict resolution rules and document them.

Never silently overwrite newer valid user progress.

Clearly distinguish:

- Available offline
- Requires internet
- Temporarily unavailable

Avoid unlimited caching of copyrighted or provider-restricted content.

---

# 26. FREE HOSTING AND ZERO-COST OPERATION

The project must be designed for free deployment options.

Research currently available free hosting services that support the required backend capabilities.

The existing stack is PHP and MySQL/MariaDB.

Evaluate free hosting based on:

- PHP version
- MySQL availability
- HTTPS
- Outbound HTTP requests
- API endpoint accessibility from Android
- SMTP support
- File upload limits
- Storage limits
- Request limits
- CPU and memory restrictions
- Cron availability
- Background job restrictions
- Backup support
- Reliability
- Terms of service

Do not assume a host supports these capabilities without verification.

In particular, investigate whether its anti-bot protections interfere with Android API clients.

If a host does not support the required authentication, external API calls, large model files, or email functionality, do not pretend the deployment is production-ready.

Produce a compatibility matrix.

## Financial constraints

- No mandatory paid SDKs
- No mandatory paid APIs
- No paid database requirement
- No mandatory cloud AI inference
- No paid UI kits
- No paid animation services
- No paid analytics services
- No mandatory paid hosting during initial development

Prefer permissively licensed, maintained open-source components.

Document optional costs honestly, including domain registration, hosting upgrades, email delivery, storage, and app-store distribution.

Do not promise unlimited production capacity from free hosting.

Provide a migration path to better infrastructure without redesigning the application.

---

# 27. SECURITY REQUIREMENTS

Apply secure engineering practices across Android, backend, and admin.

Requirements include:

- HTTPS
- Secure authentication
- Password hashing
- Session revocation
- Token expiry
- Role authorization
- Input validation
- Output escaping
- SQL injection prevention
- CSRF protection for cookie-authenticated web actions
- Rate limiting
- Secure file uploads
- Secure password recovery
- Account enumeration resistance
- Secret management
- Minimal permissions
- Sensitive-data protection
- Safe error messages
- Dependency review
- Audit logging
- Backup encryption

Do not embed database credentials or privileged API keys in the Android application.

The Android app must communicate only through authorized API endpoints.

Protect admin access separately from ordinary user access.

Implement security tests for authentication, authorization, and sensitive endpoints.

---

# 28. RELIGIOUS CONTENT ACCURACY

Athar handles sensitive religious source material.

Never fabricate:

- Quran verses
- Hadith text
- Hadith grading
- Tafsir
- Scholarly quotations
- Dhikr repetition counts
- Religious source references

Preserve provenance metadata.

Clearly identify external sources.

Respect source licensing and API terms.

If data is unavailable, display a truthful unavailable state.

Do not silently replace missing religious content with AI-generated text.

---

# 29. TESTING STRATEGY

Implement real tests.

## Android

- Unit tests
- ViewModel tests
- Repository tests
- Navigation tests
- Compose UI tests
- Authentication flow tests
- Offline behavior tests
- RTL tests
- Performance benchmarks

## Backend

- Authentication tests
- Authorization tests
- Validation tests
- Database integration tests
- API contract tests
- Rate-limit tests
- Upload security tests
- Backup/restore tests
- Migration tests

## Admin

- Login tests
- Role enforcement tests
- CRUD tests
- Form validation tests
- Moderation tests
- Language switching tests
- Responsive layout tests

## End-to-end journeys

At minimum verify:

1. Register account.
2. Verify email when SMTP is available.
3. Login.
4. Open Quran.
5. Navigate to an ayah.
6. Save a bookmark.
7. Resume reading.
8. Play recitation.
9. Use an adhkar counter.
10. Save a book.
11. Watch a lesson.
12. Save lesson progress.
13. Submit a comment.
14. Moderate the comment in Admin.
15. Create and manage a khatmah plan.
16. Update profile.
17. Export personal data.
18. Logout.
19. Restore session appropriately.
20. Verify unauthorized access is denied.

Use mocks for unavailable external services while keeping integration tests clearly distinguished from real-provider tests.

---

# 30. BUILD AND DEPLOYMENT DELIVERABLES

Deliver:

## Android

- Complete Android Studio project
- Working Gradle configuration
- Reproducible debug APK build
- Documented release signing procedure
- APK/AAB release build configuration
- Application icon using approved logo
- Versioning strategy
- Environment configuration
- Build instructions

Never commit private signing keys.

## Admin

- Complete responsive admin application
- Deployment-ready PHP files
- Secure authentication
- All required CRUD interfaces
- Localization
- Setup instructions

## Backend

- Versioned API
- Database migrations
- Configuration templates
- Secure error handling
- Documentation
- Tests

## Documentation

- Complete docs structure
- API contract
- Feature inventory
- Deployment guide
- Test reports
- Known limitations
- Future development guide

Do not claim a build is successful without executing the build.

---

# 31. IMPLEMENTATION PHASES

Follow this order.

### Phase 0 — Discovery

- Inspect repository.
- Audit code and database.
- Discover and review agent skills.
- Research design references.
- Research hosting constraints.
- Record blockers.

### Phase 1 — Documentation

- Write architecture.
- Write feature specifications.
- Write UI/UX design system.
- Write API contract.
- Write migration plan.
- Write test strategy.
- Write implementation tracker.

### Phase 2 — Backend foundation

- Refactor safely.
- Preserve existing data.
- Create versioned API.
- Implement authentication.
- Implement authorization.
- Validate database migrations.
- Add integration tests.

### Phase 3 — Android foundation

- Create native project.
- Implement design system.
- Implement localization.
- Implement splash/onboarding.
- Implement mandatory authentication.
- Implement navigation.
- Implement local persistence.

### Phase 4 — Core modules

- Quran
- Audio
- Tafsir
- Search
- Recitation review
- Adhkar
- Books
- Scholars
- Series
- Lessons
- Comments
- Khatmah
- Account

### Phase 5 — Independent Admin

- Authentication
- Dashboard
- Content management
- User management
- Moderation
- Service settings
- Owner operations
- Localization

### Phase 6 — Integration

- Connect Android to backend.
- Connect Admin to backend.
- Validate synchronization.
- Test account permissions.
- Test error handling.
- Test real user journeys.

### Phase 7 — Quality and optimization

- Security testing
- Accessibility testing
- Performance profiling
- UI refinement
- Database optimization
- Bug fixing
- Regression testing

### Phase 8 — Deployment readiness

- Validate free-hosting compatibility.
- Prepare deployment package.
- Produce Android builds.
- Verify configuration.
- Update documentation.
- Report remaining blockers.

Do not skip phases merely to deliver something quickly.

Work iteratively and maintain a usable, testable project throughout development.

---

# 32. CONTINUOUS PROGRESS AND QUALITY CONTROL

Maintain:

```text
docs/progress/TASK_TRACKER.md
docs/progress/DECISION_LOG.md
docs/progress/CHANGELOG.md
```

For each task record:

- Task ID
- Feature/module
- Priority
- Dependencies
- Current status
- Files changed
- Tests performed
- Known blockers
- Completion evidence

Use these statuses:

- TODO
- IN_PROGRESS
- BLOCKED
- IMPLEMENTED
- TESTED
- VERIFIED

Do not mark something VERIFIED without evidence.

If execution is interrupted, the documentation must allow another engineering agent to continue accurately.

At the end of each meaningful implementation stage, report:

- What was implemented
- What was tested
- What failed
- What remains
- What decisions are required

Avoid vague claims such as "everything works perfectly."

---

# 33. FINAL DEFINITION OF DONE

Athar is not complete merely because the UI looks attractive.

Completion requires:

- Functional native Android application
- Mandatory authentication
- Working backend integration
- Separate functional admin dashboard
- Complete content-management workflows
- Arabic and English localization
- Correct RTL/LTR behavior
- Preserved Athar branding
- Polished onboarding and motion
- Verified Quran and religious-content accuracy
- Proper source attribution
- Tested user progress and synchronization
- Secure authentication and authorization
- Working database migrations
- Measured performance
- Meaningful automated tests
- Reproducible builds
- Accurate deployment documentation
- Documented external-service blockers
- Modular architecture for future expansion

Every existing feature must have a documented migration outcome.

No silent feature removal.

No fake implementations.

No unnecessary paid dependencies.

No fabricated test results.

---

# 34. IMMEDIATE EXECUTION INSTRUCTIONS

Start working immediately.

Your first actions must be:

1. Inspect the actual repository and current Git state.
2. Produce a complete file and architecture inventory.
3. Trace the existing features through UI, backend, and database.
4. Identify implementation defects and migration risks.
5. Research relevant engineering and design skills.
6. Verify and install useful trusted skills where supported.
7. Research professional mobile and admin UI patterns.
8. Extract the existing Athar brand palette.
9. Inspect the attached official logo.
10. Create the documentation structure.
11. Produce a detailed implementation plan.
12. Identify critical decisions and genuine blockers.
13. Begin implementation in the approved phase order.

Do not stop after writing a high-level plan if you have the necessary tools and permissions to proceed.

Do not waste time repeatedly asking for details already available in the repository.

Make reasonable, reversible engineering decisions and document them.

Ask for approval before destructive changes, material changes to product scope, or decisions that create unavoidable recurring costs.

The user prioritizes:

**1. Correct implementation and reliability.**

**2. Outstanding UI/UX and visual polish.**

**3. Extremely smooth real-world performance.**

**4. Complete existing feature coverage.**

**5. Secure and maintainable architecture.**

**6. Zero-cost development and the lowest feasible operating cost.**

**7. Easy future expansion.**

Your objective is not to create a basic Islamic app.

Your objective is to transform Athar into a cohesive, beautifully designed, fast, secure, extensible Android and web administration ecosystem, preserving its identity and correctly implementing its existing functionality.

**BEGIN WITH THE REPOSITORY AUDIT AND DOCUMENTATION. THEN EXECUTE THE IMPLEMENTATION PLAN.**
