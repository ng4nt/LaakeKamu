# LääkeKamu – Architecture

How the LääkeKamu Android app is structured. For the tables see [DATA_MODEL.md](DATA_MODEL.md); for the threat model see [SECURITY.md](SECURITY.md).

## Overview

A single-module app with a **layered architecture** and **MVVM** in the UI layer. State flows down from ViewModels to composables as `StateFlow`s, and events flow up (unidirectional data flow). Dependencies are wired by hand, without a DI framework.

```
Compose UI  ──►  ViewModel  ──►  Repository (interface)  ──►  Room / SQLCipher
                                       ▲                          ▲
                  domain rules ────────┘            reminders ─────┘ (AlarmManager, notifications)
```

Package root: `fi.hh.laakekamu`.

## Layers

### 1. UI (`ui`)
- **Jetpack Compose** with Material 3. The interface is Finnish only.
- **Navigation:** one `NavHost` in `LaakeKamuRoot`. Three tabs in the bottom bar (`MainDestination`): *Lääkelista* (medication list), *Etusivu* (Home), *Oirepäiväkirja* (symptom diary). Forms (add or edit a medication, add a symptom) are separate routes (`FormRoute`).
- **One package per screen**, each with a `…Screen` composable and a `…ViewModel`: `etusivu`, `laakelista`, `lisaalaake`, `muokkaalaake`, `oirepaivakirja`, `lisaaoire`, `tietokanta` (the database error screen). Shared widgets live in `components`, Finnish formatting in `format`, and the theme in `theme`.
- **ViewModels** expose state with `StateFlow` and call repositories. Each has a companion `Factory` built with `viewModelFactory { initializer { … } }` that takes its dependencies from `AppContainer`.

### 2. Domain (`domain`)
- Pure Kotlin: it imports only `kotlin.*` and `java.time.*`, so it has no Android or data-layer dependency and is unit-tested on the JVM.
- `AnnosRules`: which doses exist on a day and their status (taken, missed, upcoming).
- `MuistutusRules`: how reminder rows change over time without rewriting history, and how a reminder time becomes an alarm moment (including daylight saving time).
- `LaakeValidators`: name, date range and reminder time checks, plus the treatment duration text (`kestoOf`).
- `KuinkaUsein`: the *daily* / *as needed* enum.

### 3. Data (`data`)
- **Room** database `AppDatabase` (6 entities, 5 DAOs) on **SQLCipher**. See [DATA_MODEL.md](DATA_MODEL.md).
- **Repositories** are interfaces with Room implementations: `LaakeRepository` (medications and reminders), `AnnosRepository` (doses and ticking them), `PaivakirjaRepository` (diary calendar data) and `OireRepository` (symptom entries).
- **Key handling:** `SqlCipherKey` makes and unwraps the database key with the Android Keystore; `SqlCipherSetup` builds the raw-key passphrase and converts an old plaintext database; `TietokannanTila` describes whether the database could be opened.
- `Converters` store `LocalDate` and `Instant` as integers.

### 4. Reminders (`reminders`)
- `MuistutusScheduler` keeps one alarm per reminder time (slot), set with `AlarmManager.setAlarmClock` so Doze does not delay it, and snooze alarms with `setExactAndAllowWhileIdle`.
- `AlarmReceiver` fires when a slot's alarm or a snooze alarm goes off and posts one notification per due, untaken medication; a slot's next alarm is then set. If the database cannot be opened it posts a single generic "open the app" notification with no medication data.
- `NotificationActionReceiver` handles the **Otettu** (taken) and **Muistuta 10 min päästä** (snooze 10 minutes) buttons.
- `BootReceiver` listens for protected system broadcasts (boot, app update, time and time zone changes, exact-alarm permission changes) and resets the alarms. Doses missed while the phone was off are announced as "Myöhässä".
- `Ilmoitukset` builds the notifications. The lock screen shows a generic version without the medication name.
- Two decorators keep the alarms in step with the data: `AjastavaLaakeRepository` reschedules after every medication change, and `KuittaavaAnnosRepository` removes a dose's notification when it is ticked in the app.

## Dependency injection

`AppContainer`, created in `LaakeKamuApp` (the `Application` class), owns the database, the repositories and the scheduler and hands them to ViewModels and receivers. The database opens lazily on the first `database()` call, not at app start.

## Threading

- Compose and ViewModel state run on the main thread.
- Database, Keystore and SharedPreferences work runs on an injected IO dispatcher (`Dispatchers.IO` in the app, a test dispatcher in tests).
- Debug builds enable `StrictMode`, which **logs** disk reads and writes on the main thread and leaked closeables. It does not crash the app.

## Security architecture

1. **Encryption at rest:** the database is encrypted with SQLCipher using a raw 32-byte key.
2. **Key protection:** the key is wrapped with an AES-256-GCM key in the Android Keystore; only the wrapped key and its IV are stored, in private SharedPreferences.
3. **Recovery:** if the database cannot be opened, `AppContainer` reports a `TietokannanTila` and the error screen offers *retry* and, when the key or file is unusable, *start over* (`AppContainer.startOver()`), which deletes the key and the database files only after the user confirms.
4. **No network, no backup:** no INTERNET permission, and all data is excluded from backup and device transfer.

Details and trade-offs are in [SECURITY.md](SECURITY.md).
