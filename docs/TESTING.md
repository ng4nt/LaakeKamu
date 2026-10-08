# Testing

LääkeKamu has two kinds of automated tests. Run the counts yourself if you need exact numbers; they change with every commit.

| Kind | Location | Runs on | Command |
|---|---|---|---|
| Unit tests | `app/src/test` | JVM (no device) | `./gradlew testDebugUnitTest` |
| Instrumented tests | `app/src/androidTest` | Emulator or Android device | `./gradlew connectedDebugAndroidTest` |
| Code style | all Kotlin sources | JVM | `./gradlew ktlintCheck` |

Frameworks: JUnit 4, `kotlinx-coroutines-test`, AndroidX Test, Espresso and Compose UI test (`ui-test-junit4`).

## What is tested where

| Area | Unit tests (JVM) | Instrumented tests (device) |
|---|---|---|
| `domain` | Dose rules, reminder history, scheduling slots, validators | None; the domain layer has no Android dependency |
| `data` | Type converters, clock, SQLCipher setup rules, database-state rules | Room DAOs and repositories on a real database, plaintext to encrypted migration, Keystore key wrapping, key-loss handling |
| `reminders` | Rescheduling and cancelling alarms | Slot scheduling, late and deleted-medication doses, the "taken" notification action |
| `ui` | ViewModels, progress, diary rules, formatting | Compose screens, Finnish pickers, number formatting, an end-to-end diary flow |

## Design choices

- **Injected clock.** Domain and ViewModel code take a `java.time.Clock`, so tests use a fixed date and time zone instead of the real time.
- **Fake repository.** `FakeLaakeRepository` lets ViewModel tests run without Room.
- **Real Keystore and SQLCipher in instrumented tests.** Encryption, key wrapping and migration are only meaningful on a real device or emulator, so they are not mocked.
- **Accessibility checks.** Several UI tests run at 200 % font scale to make sure buttons and form errors stay reachable.

## Manual check: simulating key loss

Debug builds include `SimulateKeyLossReceiver`, which deletes the database's Keystore key so you can see the error screen without breaking a real device.

```bash
adb shell am broadcast -n fi.hh.laakekamu/.debug.SimulateKeyLossReceiver
adb shell am force-stop fi.hh.laakekamu
# start the app again
```

The app should show the "Tietoja ei voitu avata" screen. Data is deleted only after you confirm "Aloita alusta" (start over). The receiver does not exist in release builds.

## Not covered

- No automated test runs on a physical locked phone, so the lock-screen notification text is verified by hand.
- Alarm behaviour across Doze and reboot is covered by scheduling logic tests, not by waiting for real alarms.
