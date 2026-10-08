<div align="center">

# 💊 LääkeKamu

**A private, offline medication reminder and symptom diary for Android.**

*Lääke* = medication · *Kamu* = buddy

![Android](https://img.shields.io/badge/Android-API%2026%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![Room + SQLCipher](https://img.shields.io/badge/Room%20%2B%20SQLCipher-encrypted-0A7B83)
![Tests](https://img.shields.io/badge/tests-297-brightgreen)
![Network](https://img.shields.io/badge/network-none-lightgrey)
![License](https://img.shields.io/badge/license-MIT-blue)

</div>

---

LääkeKamu helps you remember to take your medication and keep track of how you feel. Your health data
**never leaves your phone**: the app has no internet permission, no account, no analytics and no cloud backup.
The interface is in Finnish.

> ⚠️ **Disclaimer.** LääkeKamu is a reminder aid, not a medical device. It does not give medical advice and
> must not replace instructions from your doctor or pharmacist. Do not rely on it as your only safeguard.

## ✨ Features

| | |
|---|---|
| 🏠 **Home (Etusivu)** | Today's doses at a glance, with a progress bar showing how many are taken, missed and still to come. Tick a dose when you take it, undo if you tapped by mistake. |
| 💊 **Medication list (Lääkelista)** | Add, edit and delete medications: form, dose, unit, start and end date. Split into *daily* and *as needed* medications. |
| ⏰ **Reminders** | Several reminder times per medication, delivered as exact alarms that survive Doze, reboots, app updates and time-zone changes. Confirm a dose or snooze for 10 minutes straight from the notification. |
| 📓 **Symptom diary (Oirepäiväkirja)** | A calendar of days with doses taken and symptoms logged. Mark a symptom as a side effect of a specific medication. |
| 🔒 **Private by design** | Encrypted database, generic lock-screen notifications, no network, no backup. |
| 🕰️ **History stays honest** | Changing or removing a reminder never rewrites past days. Deleted medications keep their intake history. |

## 🔐 Privacy and security

- **Encrypted at rest.** The whole database is encrypted with [SQLCipher](https://www.zetetic.net/sqlcipher/).
  The key is random, wrapped with an AES-256-GCM key in the Android Keystore.
- **Nothing is sent anywhere.** No `INTERNET` permission, `allowBackup="false"`, no third-party services.
- **Lock-screen safe.** Reminders on a locked phone say only "you have a medication reminder"; the medication
  name appears after unlocking.
- **No silent data loss.** If the database cannot be opened, the app shows an error screen and deletes data
  only after you explicitly confirm "start over".

Details: [PRIVACY.md](docs/PRIVACY.md) · [SECURITY.md](docs/SECURITY.md)

## 🏗️ Architecture

A single-module app with a layered architecture and MVVM, wired by hand without a DI framework.

```
Compose UI ──► ViewModel ──► Repository (interface) ──► Room / SQLCipher
                                   ▲                          ▲
              domain rules ────────┘            reminders ─────┘ (AlarmManager, notifications)
```

| Layer | Package | Responsibility |
|---|---|---|
| UI | `ui` | Jetpack Compose screens and ViewModels, one package per screen |
| Domain | `domain` | Pure Kotlin rules: which doses exist on a day, their status, validation, reminder history |
| Data | `data` | Room database on SQLCipher, repositories, Keystore key handling |
| Reminders | `reminders` | Alarm scheduling, notifications, boot and time-change receivers |

The domain layer imports only `kotlin.*` and `java.time.*`, so it is unit-tested on the JVM with an injected
clock.

## 🧰 Tech stack

- **Language:** Kotlin 2.3
- **UI:** Jetpack Compose (BOM 2026.01), Material 3, Navigation Compose, splash screen API
- **Persistence:** Room 2.8 (schema exported to `app/schemas/`), SQLCipher 4.17
- **Async:** Kotlin coroutines and `StateFlow`
- **Tooling:** Android Gradle Plugin 8.8, KSP, ktlint
- **Target:** `minSdk` 26 · `targetSdk` / `compileSdk` 35

## 🚀 Getting started

**Requirements:** Android Studio (Gradle downloads JDK 21 by itself) and an emulator or an Android 8.0+ device.

```bash
# build and install a debug build on a connected device or emulator
./gradlew installDebug

# code style
./gradlew ktlintCheck
```

Or open the project in Android Studio and press **Run**. On Android 13+ the app asks for notification
permission the first time; reminders do not work without it.

## 🧪 Testing

297 automated tests: 222 JVM unit tests and 75 instrumented tests.

```bash
./gradlew testDebugUnitTest            # JVM unit tests
./gradlew connectedDebugAndroidTest    # device / emulator tests
```


## 📚 Documentation

| Document | What it covers |
|---|---|
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | Layers, packages, reminders, threading, dependency wiring |
| [DATA_MODEL.md](docs/DATA_MODEL.md) | Tables, columns, constraints, indexes, encryption, ER diagram |
| [SECURITY.md](docs/SECURITY.md) | Threat model, measures, known limitations, how to report a vulnerability |
| [PRIVACY.md](docs/PRIVACY.md) | What data the app handles and what it never does |

## 🗂️ Project layout

```
app/src/main/java/fi/hh/laakekamu/
├── data/        Room entities, DAOs, repositories, SQLCipher key handling
├── domain/      Pure Kotlin rules and validators
├── reminders/   AlarmManager scheduling, notifications, receivers
└── ui/          Compose screens, ViewModels, navigation, theme
docs/            Architecture, data model, security, privacy, test docs
```

Identifiers follow the Finnish domain vocabulary: `laake` medication, `muistutus` reminder, `annos` dose,
`otettu` taken, `oire` symptom, `paivakirja` diary.

## ⚖️ License

LääkeKamu is open source under the [MIT License](LICENSE).

The app bundles the [Open Sans](https://fonts.google.com/specimen/Open+Sans) font under the SIL Open Font
License; see `app/src/main/assets/licenses/OpenSans-OFL.txt`.
