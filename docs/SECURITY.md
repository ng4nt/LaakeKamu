# Security Policy

LääkeKamu stores health data (medications and symptoms), so security reports are taken seriously.

## Reporting a vulnerability

Please do **not** open a public issue for a security problem. Use GitHub's private vulnerability reporting instead: open the repository's **Security** tab and choose **Report a vulnerability**. Include the app version (`versionName` in `app/build.gradle.kts`), the Android version, and the steps to reproduce.

This is a student project maintained by one person, so there is no guaranteed response time. I aim to acknowledge a report within a week.

## Supported versions

Only the latest commit on `main` is supported.

## Security model

### What the app protects

The medication list, the reminder times, the record of taken doses and the symptom diary. They are stored only on the phone, in `laakekamu.db`.

### Measures

| Area | Measure |
|---|---|
| Data at rest | The whole database file is encrypted with SQLCipher (`sqlcipher-android` 4.17.0). |
| Key management | A random 32-byte key is created once and wrapped with an AES-256-GCM key in the Android Keystore (alias `laakekamu_db_key`). Only the wrapped key and its IV are stored in private SharedPreferences. See `SqlCipherKey.kt`. |
| Network | The app declares **no INTERNET permission**, so it cannot send data anywhere. |
| Backup | `allowBackup="false"`, and `data_extraction_rules.xml` and `backup_rules.xml` exclude everything from cloud backup and device transfer. |
| Lock screen | Notifications show a generic text on the lock screen; the medication name is visible only after unlocking. |
| Exposed components | `AlarmReceiver` and `NotificationActionReceiver` are not exported and have no intent filters; they are reached only through the app's own PendingIntents. `BootReceiver` is exported only to receive protected system broadcasts and checks the action in `onReceive`. The notification tap opens `MainActivity` with an explicit intent. |
| Key loss | If the database cannot be opened (for example the Keystore key is gone), the app shows an error screen and never deletes data automatically. "Aloita alusta" (start over) deletes the data only after the user confirms. |
| Migration | A plaintext database from early development builds is converted once to an encrypted one, with all rows kept (`SqlCipherSetup.kt`). |
| Source | `.gitignore` excludes signing keys, keystores, `*.apk`, `*.aab` and local databases. |

### Known limitations and trade-offs

- **The Keystore key does not require the screen to be unlocked or the user to authenticate.** Reminders must open the database while the phone is locked, so this is a deliberate trade-off between security and availability. The key stays in hardware-backed storage where the phone has it. An attacker who can run code as the app on an unlocked or rooted device can read the data.
- **Notifications on an unlocked phone show the medication name.** Anyone who can see the unlocked screen can read it.
- **There is no app PIN or biometric lock.** Protection relies on the phone's own screen lock.
- **Release builds are not minified or obfuscated** (`isMinifyEnabled = false`). The code is open source, so this is not a secrecy measure, but it means the APK is easy to inspect.
- **No backup means no recovery.** If the phone is lost or the app data is cleared, the data is gone.
- **The app is a reminder aid, not a medical device.** See the README disclaimer.

### Out of scope

Attacks that need a rooted or compromised device, physical extraction of the phone's hardware keystore, and vulnerabilities in Android, SQLCipher or other dependencies themselves (report those upstream).

## Dependencies

Versions are pinned in `gradle/libs.versions.toml`. Updates are checked and recorded in the commit message of each change to that file.
