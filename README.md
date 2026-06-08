<img width="150" alt="AnkiGatekeeper icon" src="https://github.com/user-attachments/assets/fe424732-5508-44e8-9535-ae4c68899d65"/>

# AnkiGatekeeper

An Android app that displays an undismissable Anki flashcard on phone unlock.

## Requirements

- Android 8.0+ (API 26)
- [AnkiDroid](https://f-droid.org/en/packages/com.ichi2.anki/) installed from F-Droid or via direct APK (see [Media / Images](#media--images) below)

## Installation

1. Download the latest `anki-gatekeeper-*.apk` from [Releases](../../releases) on your Android.
2. Open the downloaded file and click install.
   - If prompted, allow your browser or file manager to install unknown apps — this is a one-time permission for sideloaded APKs.
5. Launch AnkiGatekeeper and follow the on-screen prompts:
   - **Display over other apps** — required to launch over the lock screen
   - **Notifications** — required to keep the background service running
   - **All files access** — required to load card images and audio
   - **Accessibility service** — required to keep the card screen in the foreground; find AnkiGatekeeper in Settings → Accessibility and enable it
   - **Deck selection** — pick which AnkiDroid deck to draw cards from (can be changed later via the gear icon)
6. Done. Lock the screen and unlock to see your first card.

> **Note:** It is better if AnkiDroid is installed from F-Droid or a direct APK (not the Play Store) — see [Media / Images](#media--images) below for why.

### LeechBlock integration

AnkiGatekeeper also runs a minimal HTTP server on `127.0.0.1:8765`. This can be used by Firefox's [LeechBlock](https://www.proginosko.com/leechblock/) extension to trigger a card review whenever the user attempts to access a distracting website. To set it up, open LeechBlock's options for a block set and enter `http://127.0.0.1:8765` as the redirect URL.

## Building from source

1. Open the project in Android Studio and sync Gradle.
2. Build and install on your device (`Run` or `adb install`).
3. Launch the app and follow the on-screen setup prompts.

## How it works

1. A foreground service (`ScreenUnlockService`) listens for `ACTION_USER_PRESENT` (keyguard dismissed).
2. When the screen unlocks, the service launches `MainActivity`, which fetches the next due card from AnkiDroid via ContentProvider.
3. The accessibility service (`AnkiGatekeeperAccessibilityService`) monitors window changes and brings the app back to the foreground if the user tries to navigate away.
4. You answer the card — Again / Hard / Good / Easy — the rating is submitted to AnkiDroid's scheduler and the lock is released, returning you to whatever was on screen before.
5. After answering the first card the lock is released for the rest of the session, and an Exit button appears so you can leave at any time.
6. If no cards are due, the gate is skipped instantly.

## Architecture

| File                                    | Role                                                                                                               |
|-----------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| `MainActivity.kt`                       | Setup flow, deck picker, card review UI (Compose); delegates state and logic to `MainViewModel`                    |
| `MainViewModel.kt`                      | Business logic and durable UI state; survives configuration changes and process death via `SavedStateHandle`        |
| `AnkiRepository.kt`                     | AnkiDroid ContentProvider queries: deck list, due card, submit answer                                              |
| `ScreenUnlockService.kt`                | Foreground service; 1×1 invisible overlay window (BAL bypass) + unlock broadcast receiver + LeechBlock HTTP server |
| `GatekeeperServer.kt`                   | Minimal raw-socket HTTP server on `127.0.0.1:8765`; serves the holding page and fires the card gate intent         |
| `AnkiGatekeeperAccessibilityService.kt` | Monitors window changes and returns the app to the foreground                                                      |
| `CardWebView.kt`                        | Renders card HTML via WebView with local media served through `WebViewAssetLoader`; supports dark mode              |

### Why the invisible overlay window?

Android 10+ blocks background `startActivity()` calls unless the calling UID has a visible window (`callingUidHasNonAppVisibleWindow`). A 1×1 transparent `TYPE_APPLICATION_OVERLAY` view in the service satisfies this check, allowing the unlock receiver to reliably bring the app to the foreground on every unlock.

### Why the accessibility service?

The accessibility service listens for `TYPE_WINDOW_STATE_CHANGED` events. If the user tries to navigate away while a card is still due (before answering the first card of the session), the service calls `startActivity` to bring AnkiGatekeeper back to the foreground. Once the first card is answered, `isLocked` is set to false and the service stops intercepting.

## AnkiDroid API

Cards are fetched and answered via AnkiDroid's public ContentProvider (`com.ichi2.anki.flashcards`):

- `reviewInfo` — returns the next due card for an optional deck filter
- `notes/{noteId}/cards/{ord}` — returns rendered question and answer HTML
- Updating `reviewInfo` with `ease` (1–4) and `time_taken` submits the answer to the scheduler

Permission required: `com.ichi2.anki.permission.READ_WRITE_DATABASE`

## Media / Images

Card images and audio are loaded from AnkiDroid's media folder. Due to Android scoped storage restrictions, this folder is only accessible to other apps when AnkiDroid itself has full storage access.

**AnkiDroid installed from the Play Store does not have full storage access** — it stores media in `/storage/emulated/0/Android/data/com.ichi2.anki/files/AnkiDroid/collection.media/`, which is private to AnkiDroid and cannot be read by this app.

To make media work, install AnkiDroid from **F-Droid** or via a **direct APK install** (e.g. from the AnkiDroid GitHub releases page). These builds have full storage access and store media at:

```
/storage/emulated/0/AnkiDroid/collection.media/
```

which this AnkiGatekeeper can read. See [AnkiDroid: Full Storage Access](https://github.com/ankidroid/Anki-Android/wiki/Full-Storage-Access) for details.
