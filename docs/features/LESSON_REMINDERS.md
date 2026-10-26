# Lesson reminder choice and consent

Task: 11.1  
Implementation branch: \`parallel/admin-foundation\`  
Status: **IMPLEMENTED / ACCEPTANCE PENDING**

Lesson reminders keep campaign/announcement consent separate, require explicit versioned \`lesson_reminders\` category consent, and keep a separate encrypted owner-scoped opt-in for each lesson.

The first opt-in shows an explicit explanation. If accepted, Athar enables only the lesson-reminder category and requests Android notification permission only from that user action when required. A denied OS permission does not save the lesson choice.

Each selected lesson uses one fixed default reminder at 15 minutes before the event. The choice is encrypted under \`private:lesson-reminder-choice:<lesson id>\` and contains only lesson id plus the 15-minute lead. Absence means off; unreadable ciphertext fails closed.

Upcoming payload parsing now retains event id, source schedule timezone, revision, and Admin \`reminders_enabled\`. The detail UI always exposes opt-out for an existing choice and only offers new opt-in for a scheduled event with a real start time whose reminder flag is enabled.

Task 11.2 owns creating the actual WorkManager reminder from this choice. Acceptance still requires Android build/resource/UI/API33+ device checks, offline/corruption/account-switch behavior, RTL/LTR and accessibility.
