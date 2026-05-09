# Anki Custom Launcher

A custom Android home screen launcher that requires the user to answer at least one Anki flashcard before accessing their phone.

## How it works

When the user presses the home button, the launcher intercepts and shows an Anki review card. After answering, they're taken to the normal home screen. Pressing home again resets the gate.

## Requirements

- Android 8.0+ (API 26)
- [AnkiDroid](https://play.google.com/store/apps/details?id=com.ichi2.anki) installed on the device

## Setup

1. Open the project in Android Studio and sync Gradle
2. Build and install on your device
3. Press the home button — Android will ask which launcher to use; select **Anki Launcher** and choose **Always**
4. Grant AnkiDroid database access when prompted

## Architecture

- **MainActivity** — registered as the HOME launcher; owns the `GATE → HOME` state machine
- **Review screen** — renders Anki card front/back with Again / Hard / Good / Easy buttons (Jetpack Compose)
- **Anki integration layer** — fetches due cards and submits answers via the AnkiDroid `ReviewInfo` ContentProvider

## What this project avoids

- Unlock-event hacks or accessibility service abuse
- Overlay systems
- Embedding AnkiDroid directly
