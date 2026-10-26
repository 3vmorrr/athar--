# Reviewed agent skills
Research date: 2026-10-24. The [skills.sh directory](https://skills.sh/) and `npx --yes skills find android` were inspected. Broad search returned many Expo/third-party candidates; these are not appropriate for the required native Kotlin app. Installed local `find-skills` and `ui-ux-pro-max` were read. The design query was run, but its unrelated replacement palette/fonts were rejected in favor of Athar's existing tokens.

Reviewed official [android/skills](https://github.com/android/skills), published by Google LLC under Apache-2.0. It had 9,580 GitHub stars at inspection. Selected folders contain Markdown/reference files rather than executable install scripts. `jetpack-compose/jetpack-compose-performance/SKILL.md` and `security/android-permissions-security/SKILL.md` were read before installation; relevant reference files must be read before a matching optimization/security fix. The official testing setup was inspected but not installed: its mandatory broad framework additions exceed the initial justified dependency needs; official test documentation will be used instead.

Installed using the reviewed built-in skill-installer helper:
```powershell
python "$env:USERPROFILE/.codex/skills/.system/skill-installer/scripts/install-skill-from-github.py" --repo android/skills --path jetpack-compose/jetpack-compose-performance security/android-permissions-security
```
Installation succeeded under `~/.codex/skills/`. They become automatically discoverable on the next turn and can be read/applied explicitly during this work. Intended use: primitive lazy keys/content types, cheap composition/lifecycle IO boundaries, private manifest components, contextual permission prompts and denial recovery. No third-party executable instruction was blindly run. No skill authorizes sending email or changing production data.
