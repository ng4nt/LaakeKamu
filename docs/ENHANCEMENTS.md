# LääkeKamu – Enhancements & Backlog

Living list of things to fix, improve or add after 1.0.0. Keep it short; move finished items to
`CHANGELOG` / git history and delete them here.

**Guardrails (do not break when adding features):** no INTERNET permission, no cloud backup, no
analytics, database stays encrypted, locked screen shows no medication names, Finnish UI,
no `fallbackToDestructiveMigration()` (schema changes need tested migrations).

Priority: **P1** next release · **P2** soon · **P3** nice to have.

---

## Fixes / known gaps

| Pri | Item | Notes |
|---|---|---|
| P1 | Manual test of reboot, Doze, update and time-zone/DST reminders on a physical device | Currently covered by scheduling-logic tests only (see `docs/TESTING.md`) |
| P1 | Manual check that the locked screen shows no medication name | No automated test on a locked phone |
| P1 | Fix contrast of the navigation bar (icons/labels/selected indicator) | Check against WCAG AA (4.5:1 text, 3:1 icons) in light and dark theme |
| P2 | Accessibility pass (font scale 200 %, contrast) | Target group may include older users |
| P2 | TalkBack support: content descriptions, logical focus order, announce state changes (dose taken, reminder added), Finnish speech labels | Test with TalkBack on a real device; add Compose semantics tests |
| P3 | Add more device/screen-size coverage to UI tests | |

## New features

| Pri | Idea | Notes |
|---|---|---|
| P1 | "Today" button (Tänään) to jump back to the current day | For the date views (home / diary) when browsing other dates |
| P2 | Local export of diary/intake history (PDF or CSV) to share with a doctor | User-initiated via the system share sheet; no network permission needed |
| P2 | Encrypted local backup / restore (user-chosen file) | Needs a key-handling design; update `docs/SECURITY.md` |
| P2 | Button to modify diary symptoms, decide what happens to deleted symptoms | |
| P2 | Refill reminders (remaining pills, "running low") | Needs a DB migration |
| P3 | Flexible schedules (every N days, weekdays only, as-needed doses) | |
| P3 | Snooze length setting and repeat-reminder escalation | |
| P3 | Statistics view: adherence % per week/month, symptom trends | |
| P3 | Home-screen widget showing the next dose | Must not reveal medication names if privacy lock is on |
| P3 | Additional languages (English, Swedish) | Finnish remains the default |
| P3 | Dark theme polish / dynamic colour | |

## Testing improvements

- [ ] Move pure logic (scheduling, DST/time zone, formatting) to fast JVM unit tests; keep `androidTest` for Room/SQLCipher/UI
- [ ] Add accessibility checks to Compose UI tests (`enableAccessibilityChecks()`, touch target size, contrast, content descriptions)
- [ ] Notification tests with UiAutomator: reminder posts, "Taken" action works, lock-screen content is redacted
- [ ] Reboot / time-change / time-zone tests via `BootReceiver` and fake clock; document the manual device checklist
- [ ] Room migration tests (`MigrationTestHelper`) for every schema change using `app/schemas`
- [ ] Screenshot tests (Paparazzi/Roborazzi) for light/dark, font scale 200 %, small screens
- [ ] Run instrumented tests on a Gradle Managed Device / emulator matrix (min SDK, latest SDK) in CI
- [ ] Coverage report (JaCoCo) and a minimum threshold for `data` and `reminders` packages
- [ ] Security checks: assert no INTERNET permission and `allowBackup=false` in the merged manifest
- [ ] Usability test with 3–5 users (think-aloud + SUS) and record changes made

## Technical debt / quality

- [ ] Run both test suites and record the counts in `docs/TESTING.md`
- [ ] Add CI (build + unit tests on push)
- [ ] Enable lint / detekt / ktlint checks
- [ ] Review dependency versions regularly (SQLCipher, Room, Compose)
- [ ] Prepare Play Store / release signing and release notes process

## Ideas to evaluate (unsorted)

-

---

## How to use this file

1. Add an idea under the right section with a priority.
2. When starting one, create a branch and (if it touches data) a Room migration + test.
3. When done, remove the line and mention it in the commit / release notes.
