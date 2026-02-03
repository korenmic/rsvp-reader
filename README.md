# RSVP Reader for Android

A hybrid Split-Screen + Floating Overlay RSVP reader for Android.

## Requirements

- Android SDK API 29+
- Android Studio
- Java/Kotlin development environment

## Setup

1. Open project in Android Studio
2. Update `local.properties` with your Android SDK path
3. Sync Gradle
4. Build and run on device

## Permissions Required

- Accessibility Service
- Draw Over Other Apps (System Alert Window)
- Foreground Service

## Usage

1. Enable split-screen mode
2. Place RSVP Reader in top half
3. Place source app (browser, reader, etc.) in bottom half
4. Grant required permissions
5. Use floating overlay to control reading speed
6. Touch and hold overlay to read
7. Release to pause

## Build

```bash
./gradlew assembleDebug
```

APK will be in: `app/build/outputs/apk/debug/`
