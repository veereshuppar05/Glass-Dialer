# Glass Dialer

A glassmorphism-style Android dialer built with Kotlin and Jetpack Compose.

## Requirements

- Android Studio / Android Gradle Plugin 8.13.2
- JDK 17
- Android SDK Platform 36
- Minimum Android version: Android 10 (API 29)

## Build

Open the project in Android Studio and run the `app` configuration, or use Gradle:

```bash
gradle assembleDebug
```

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.

The app requests the Android default Phone app role because call handling and the in-call UI are provided through Android Telecom.
