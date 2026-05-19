# FlashGate

A personal Android app that intercepts every phone unlock and requires answering one Anki flashcard before granting access to the rest of the phone.

## How it works

1. A foreground service (`ScreenUnlockService`) listens for `ACTION_USER_PRESENT` (keyguard dismissed).
2. When the screen unlocks, the service launches `MainActivity`, which fetches the next due card from AnkiDroid via ContentProvider.
3. The accessibility service (`FlashGateAccessibilityService`) monitors window changes and brings the app back to the foreground if the user tries to navigate away.
4. You answer the card — Again / Hard / Good / Easy — the rating is submitted to AnkiDroid's scheduler and the lock is released, returning you to whatever was on screen before.
5. After answering the first card the lock is released for the rest of the session, and an Exit button appears so you can leave at any time.
6. If no cards are due, the gate is skipped instantly.

## Requirements

- Android 8.0+ (API 26)
- [AnkiDroid](https://f-droid.org/en/packages/com.ichi2.anki/) installed from F-Droid or via direct APK (see [Media / Images](#media--images) below)

## Setup

1. Open the project in Android Studio and sync Gradle.
2. Build and install on your device (`Run` or `adb install`).
3. Launch the app and follow the on-screen prompts:
   - **Display over other apps** — required to launch over the lock screen
   - **Notifications** — required to keep the background service running
   - **All files access** — required to load card images and audio
   - **Accessibility service** — required to keep the card screen in the foreground; find FlashGate in Settings → Accessibility and enable it
   - **Deck selection** — pick which AnkiDroid deck to draw cards from (can be changed later via the gear icon)
4. Done. Lock the screen and unlock to see your first card.

## Architecture

| File | Role |
|---|---|
| `MainActivity.kt` | Setup flow, deck picker, card review UI (Compose) |
| `AnkiRepository.kt` | AnkiDroid ContentProvider queries: deck list, due card, submit answer |
| `ScreenUnlockService.kt` | Foreground service; 1×1 invisible overlay window (BAL bypass) + unlock broadcast receiver |
| `FlashGateAccessibilityService.kt` | Monitors window changes and returns the app to the foreground while a card is due |
| `CardWebView.kt` | Renders card HTML via WebView with local media served through `WebViewAssetLoader` |

### Why the invisible overlay window?

Android 10+ blocks background `startActivity()` calls unless the calling UID has a visible window (`callingUidHasNonAppVisibleWindow`). A 1×1 transparent `TYPE_APPLICATION_OVERLAY` view in the service satisfies this check, allowing the unlock receiver to reliably bring the app to the foreground on every unlock.

### Why the accessibility service?

The accessibility service listens for `TYPE_WINDOW_STATE_CHANGED` events. If the user tries to navigate away while a card is still due (before answering the first card of the session), the service calls `startActivity` to bring FlashGate back to the foreground. Once the first card is answered, `isLocked` is set to false and the service stops intercepting.

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

which this app can read. See [AnkiDroid: Full Storage Access](https://github.com/ankidroid/Anki-Android/wiki/Full-Storage-Access) for details.

## TODO

- **Full audio reimplementation** — currently, audio is handled by injecting an `autoplay` attribute on the first `<audio>` element in the answer HTML. AnkiDroid's own player uses a native `MediaPlayer` managed outside the WebView: it extracts all `[sound:...]` tags during rendering and plays them sequentially, requests Android audio focus, and optionally replays question audio when the answer is revealed. A proper reimplementation would match this behaviour.
