# Casio F-91W Lock Screen

A native Android app that renders a pixel-faithful Casio F-91W LCD watch face on the Samsung Galaxy S24 Ultra lock screen and Always On Display (AOD).

![Watch face](docs/preview.png)

## Features

- Runs on the lock screen without unlocking the device
- Three modes: **TIME**, **STOPWATCH**, **ALARM SET**
- Authentic DSEG7Classic 7-segment font
- AOD mode — minimal HH:MM display, 5-second tick, burn-in prevention
- Auto-starts on device boot (once permission is granted)
- All interaction via on-screen tap zones — no physical button interception

## Device Target

| Field | Value |
|---|---|
| Device | Samsung Galaxy S24 Ultra (SM-S928B) |
| OS | One UI 6.1 / Android 14 (API 34) |
| Min SDK | API 29 (Android 10) |

## Tap Zones

```
┌─────────────────────────────┐
│         LCD face            │
│                             │
├────────┬───────────┬────────┤
│  LEFT  │   MIDDLE  │ RIGHT  │
│ cycle  │  context  │ action │
│  mode  │  action   │        │
└────────┴───────────┴────────┘
```

| Zone | TIME mode | STOPWATCH mode | ALARM mode |
|---|---|---|---|
| LEFT | Cycle mode → | Cycle mode → | Cycle mode → |
| MIDDLE | — | Resume (if stopped) | +1 minute |
| RIGHT | Toggle 12/24h | Start / Stop / Reset | +1 hour |

## Requirements

- Android SDK 34 (install via Android Studio or `sdkmanager`)
- Java 17
- ADB with USB debugging enabled on device

## Build & Install

```bash
# Build debug APK
./gradlew assembleDebug

# Install via ADB (USB cable required, USB debugging must be on)
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch — grants overlay permission via system settings screen
adb shell am start -n com.casio.lockscreen/.MainActivity

# Confirm service is running
adb shell dumpsys activity services com.casio.lockscreen

# Live logs
adb logcat --pid=$(adb shell pidof com.casio.lockscreen)
```

## Permissions Required

| Permission | Purpose |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Draw over the lock screen |
| `FOREGROUND_SERVICE` | Keep the service alive |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Required for overlay foreground services (API 34) |
| `RECEIVE_BOOT_COMPLETED` | Auto-start after reboot |
| `WAKE_LOCK` | Prevent CPU sleep during AOD tick |
| `USE_EXACT_ALARM` | Alarm firing (future) |

On first launch the app sends you to the system overlay permission screen. Once granted, the watch face appears on the lock screen and the app can be closed.

## Samsung-Specific Notes

- **Clock on lock screen:** Go to `Settings → Lock Screen → Clock Style → None` to hide Samsung's built-in clock underneath the overlay.
- **Battery optimisation:** Add the app to the battery optimisation whitelist (`Settings → Battery → Background usage limits`) or the service may be killed after a few minutes.
- **AOD:** Must be enabled in `Settings → Lock Screen → Always On Display` for the overlay to appear in AOD state.

## Project Structure

```
app/src/main/
├── AndroidManifest.xml
├── assets/fonts/
│   ├── DSEG7Classic-Bold.ttf       ← 7-segment display font (SIL OFL)
│   └── DSEG7Classic-Regular.ttf
└── java/com/casio/lockscreen/
    ├── MainActivity.kt             ← Permission request + service launcher
    ├── OverlayService.kt           ← ForegroundService + WindowManager overlay
    ├── CasioEngine.kt              ← State machine (TIME / STOPWATCH / ALARM_SET)
    ├── CasioFaceView.kt            ← Compose Canvas renderer
    └── BootReceiver.kt             ← Auto-start on reboot
```

## Font

The 7-segment digits use [DSEG7Classic](https://github.com/keshikan/DSEG) by Keshikan, licensed under the [SIL Open Font License 1.1](https://scripts.sil.org/OFL).

## License

MIT
