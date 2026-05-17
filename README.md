# Anki-First Launcher

A personal Android app that intercepts every phone unlock and requires answering one Anki flashcard before granting access to the rest of the phone.

## How it works

1. A foreground service (`ScreenUnlockService`) listens for `ACTION_USER_PRESENT` (keyguard dismissed).
2. When the screen unlocks, the service launches `MainActivity`, which fetches the next due card from AnkiDroid via ContentProvider.
3. The app enters [Lock Task Mode](https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode) (requires Device Owner), preventing dismissal via home or recents.
4. You answer the card — Again / Hard / Good / Easy — the rating is submitted to AnkiDroid's scheduler, lock task ends, and the app finishes back to whatever was on screen before.
5. If no cards are due, the gate is skipped instantly.

## Requirements

- Android 8.0+ (API 26)
- [AnkiDroid](https://play.google.com/store/apps/details?id=com.ichi2.anki) installed (last tested against v2.24.0)
- ADB access for one-time Device Owner setup (see below)

## Setup

1. Open the project in Android Studio and sync Gradle.
2. Build and install on your device (`Run` or `adb install`).
3. Launch the app and follow the on-screen prompts:
   - **Display over other apps** — grant in Settings
   - **Notifications** — grant the runtime permission
   - **Device Owner** — run the ADB command shown on screen:
     ```
     adb shell dpm set-device-owner com.example.ankilauncher/.AdminReceiver
     ```
   - **Deck selection** — pick which AnkiDroid deck to draw cards from (one-time).
4. Done. Lock the screen and unlock to see your first card.

### Removing Device Owner

```
adb shell dpm remove-active-admin com.example.ankilauncher/.AdminReceiver
```

## Architecture

| File | Role |
|---|---|
| `MainActivity.kt` | Setup flow, deck picker, card review UI (Compose) |
| `AnkiRepository.kt` | AnkiDroid ContentProvider queries: deck list, due card, submit answer |
| `ScreenUnlockService.kt` | Foreground service; 1×1 invisible overlay window (BAL bypass) + unlock broadcast receiver |
| `AdminReceiver.kt` | Minimal `DeviceAdminReceiver` required for Device Owner |

### Why the invisible overlay window?

Android 10+ blocks background `startActivity()` calls unless the calling UID has a visible window (`callingUidHasNonAppVisibleWindow`). A 1×1 transparent `TYPE_APPLICATION_OVERLAY` view in the service satisfies this check, allowing the unlock receiver to reliably bring the app to the foreground on every unlock.

### Why Device Owner?

Lock Task Mode with Device Owner is the only way to pin an app such that home and recents are fully disabled. Without it, `startLockTask()` falls back to system "screen pinning", which the user can exit by long-pressing back + recents.

## AnkiDroid API

Cards are fetched and answered via AnkiDroid's public ContentProvider (`com.ichi2.anki.flashcards`):

- `reviewInfo` — returns the next due card for an optional deck filter
- `notes/{noteId}/cards/{ord}` — returns rendered question and answer HTML
- Updating `reviewInfo` with `ease` (1–4) and `time_taken` submits the answer to the scheduler

Permission required: `com.ichi2.anki.permission.READ_WRITE_DATABASE`
