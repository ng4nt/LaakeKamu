# Privacy Policy

LääkeKamu is a medication reminder app. This policy explains what data the app handles. In short: **everything stays on your phone, and nothing is sent to anyone.**

## What the app stores

Only what you enter yourself:

- Your medications: name, form, dose, unit, how often, start and end date.
- Reminder times.
- Which doses you marked as taken, and when.
- Symptom diary entries and the medication they may relate to.

The app does not ask for your name, e-mail address, phone number, location, contacts or any account.

## Where the data is stored

On your phone only, in an encrypted database in the app's private storage. The encryption key is protected by the Android Keystore of your device.

## What is not collected

- **No network access.** The app does not have the INTERNET permission, so it cannot upload anything.
- **No cloud backup or device transfer.** The app's data is excluded from Android's backup, so it is not copied to your Google account or to a new phone.
- **No analytics, advertising, tracking or crash-reporting services.**
- **No third parties.** No data is shared, sold or disclosed.

Because of this, the developer has no access to your data and cannot recover it for you.

## Permissions

| Permission | Why |
|---|---|
| Notifications (`POST_NOTIFICATIONS`) | To show dose reminders. Android 13 and newer asks you first. |
| Exact alarms (`USE_EXACT_ALARM`, `SCHEDULE_EXACT_ALARM` on Android 12 and older) | To remind you at the exact time you chose. |
| Run at startup (`RECEIVE_BOOT_COMPLETED`) | To set your reminders again after the phone restarts. |

## Notifications and your lock screen

On the lock screen a reminder shows a generic text without the medication name. The name is shown after you unlock the phone.

## Your control over the data

- Delete a medication in the app. It disappears from the list and from Home; its past intake history stays in the diary.
- To remove **all** data, clear the app's storage in Android settings or uninstall the app.
- You cannot export your data from the app in this version.

## Children

The app is not directed at children and collects no personal information from anyone.

## Changes

If this policy changes, the new version and its date will be published in this file in the repository.

## Contact

Open an issue in the project's repository. For security problems, follow [SECURITY.md](SECURITY.md).
