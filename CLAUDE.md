# CLAUDE.md — Casio F-91W Lock Screen Face (Samsung S24 Ultra)

## Project Overview

Build a native Android app in Kotlin that renders a pixel-faithful Casio F-91W LCD watch
face on the Samsung Galaxy S24 Ultra lock screen and Always On Display (AOD).

The app must:
- Display on the lock screen without unlocking the device
- Stay alive as a foreground service
- Render the Casio F-91W aesthetic: green-tinted LCD, 7-segment digits, day/date panel
- Support three modes: TIME, STOPWATCH, ALARM
- Allow mode switching and actions via on-screen tap zones (no physical button interception)
- Work on Samsung One UI 6.1+ (Android 14, API 34)

This app is for personal sideloading via ADB. No Play Store publishing required.

---

## Device Target

- **Model:** Samsung Galaxy S24 Ultra (SM-S928B)
- **Screen:** 3088 × 1440 px, 6.8" AMOLED
- **OS:** One UI 6.1 / Android 14 (API 34)
- **ADB:** Connected via USB cable to MacBook

---

## Tech Stack

| Layer | Choice | Reason |
|---|---|---|
| Language | Kotlin | Required for Android native |
| UI | Jetpack Compose + Canvas | CustomPainter equivalent, GPU-accelerated |
| Service | ForegroundService | Keeps app alive in background |
| Overlay | WindowManager TYPE_APPLICATION_OVERLAY | Draws over lock screen |
| Build | Gradle (Kotlin DSL) | Standard Android build |
| Min SDK | API 29 (Android 10) | FLAG_SHOW_WHEN_LOCKED stability |
| Target SDK | API 34 (Android 14) | S24 Ultra target |

---

## Project Structure

```
casio-lockscreen/
├── CLAUDE.md                          ← this file
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/casio/lockscreen/
│   │   │   ├── MainActivity.kt        ← permission request + service launcher
│   │   │   ├── OverlayService.kt      ← ForegroundService, WindowManager
│   │   │   ├── CasioEngine.kt         ← state machine: TIME/STOPWATCH/ALARM
│   │   │   ├── CasioFaceView.kt       ← Compose Canvas renderer
│   │   │   ├── SegmentDrawer.kt       ← 7-segment digit drawing logic
│   │   │   └── AlarmManager.kt        ← alarm storage and comparison
│   │   └── res/
│   │       ├── drawable/
│   │       │   └── casio_background.png   ← LCD green background asset
│   │       ├── layout/
│   │       └── values/
│   │           ├── strings.xml
│   │           └── colors.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/
    └── libs.versions.toml
```

---

## Architecture: Component Map

```
┌─────────────────────────────────────────────┐
│  MainActivity                               │
│  - Request SYSTEM_ALERT_WINDOW permission   │
│  - Start OverlayService                     │
└───────────────┬─────────────────────────────┘
                │ starts
┌───────────────▼─────────────────────────────┐
│  OverlayService (ForegroundService)         │
│  - WindowManager.addView()                  │
│  - Params: TYPE_APPLICATION_OVERLAY         │
│            FLAG_SHOW_WHEN_LOCKED            │
│            FLAG_NOT_TOUCH_MODAL             │
│  - Hosts Compose content via               │
│    ComposeView or AbstractComposeView       │
└───────────────┬─────────────────────────────┘
                │ renders
┌───────────────▼─────────────────────────────┐
│  CasioFaceView (Composable)                 │
│  - Canvas drawing of LCD face               │
│  - Receives CasioState from CasioEngine     │
│  - 3 tap zones: LEFT_BTN, MID_BTN, RIGHT   │
└───────────────┬─────────────────────────────┘
                │ driven by
┌───────────────▼─────────────────────────────┐
│  CasioEngine (StateFlow / ViewModel)        │
│  - Modes: TIME, STOPWATCH, ALARM_SET        │
│  - 1 Hz ticker (Timer / coroutine)         │
│  - onLeftTap() → cycle mode                │
│  - onRightTap() → context action           │
└─────────────────────────────────────────────┘
```

---

## AndroidManifest.xml — Required Permissions

```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.USE_EXACT_ALARM" />
```

Service declaration:
```xml
<service
    android:name=".OverlayService"
    android:foregroundServiceType="specialUse"
    android:exported="false" />
```

---

## CasioEngine — State Machine

```
Modes:
  TIME       → shows HH:MM:SS, date, day
  STOPWATCH  → shows elapsed MM:SS.mm, START/STOP/RESET
  ALARM_SET  → shows alarm time, SET/ENABLE

Tap zones (on-screen buttons mirroring F-91W layout):
  LEFT tap  → cycle mode (TIME → STOPWATCH → ALARM → TIME)
  RIGHT tap → context action:
              TIME mode       → toggle 12/24h
              STOPWATCH mode  → start / stop / reset
              ALARM mode      → increment hour / save

State is a Kotlin data class emitted as StateFlow:
  data class CasioState(
      val mode: Mode,
      val timeHour: Int,
      val timeMinute: Int,
      val timeSecond: Int,
      val dayOfWeek: String,    // "WED"
      val dateDay: Int,
      val dateMonth: Int,
      val stopwatchRunning: Boolean,
      val stopwatchElapsedMs: Long,
      val alarmHour: Int,
      val alarmMinute: Int,
      val alarmEnabled: Boolean,
      val is24Hour: Boolean
  )
```

---

## SegmentDrawer — 7-Segment Specification

Each digit is drawn as 7 line segments on a Canvas.
Segment identifiers follow standard labeling: A (top), B (top-right), C (bottom-right),
D (bottom), E (bottom-left), F (top-left), G (middle).

```
 _
|_|
|_|

Segment map per digit (0–9):
  0 → A B C D E F
  1 → B C
  2 → A B G E D
  3 → A B G C D
  4 → F G B C
  5 → A F G C D
  6 → A F G E C D
  7 → A B C
  8 → A B C D E F G
  9 → A B C D F G
```

Draw each segment as a thick rounded Rect (not a line) for the classic LCD look.
Use color `#B8C9A3` for lit segments, `#8A9E78` at 15% opacity for unlit segments.
Background: `#8DAF6F` (the classic Casio green-grey LCD tint).

---

## Colors

```kotlin
// In colors.xml or Color.kt
val CasioGreen = Color(0xFF8DAF6F)       // LCD background
val SegmentLit = Color(0xFF2D3A1E)       // dark green, lit segment
val SegmentDim = Color(0xFF8DAF6F).copy(alpha = 0.3f)  // unlit segment
val CasioBezel = Color(0xFF1A1A1A)       // outer bezel
val CasioBody  = Color(0xFF2B2B2B)       // watch body
```

---

## OverlayService — Key Window Flags

```kotlin
val params = WindowManager.LayoutParams(
    WindowManager.LayoutParams.MATCH_PARENT,
    WindowManager.LayoutParams.MATCH_PARENT,
    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
    PixelFormat.TRANSLUCENT
)
```

Note: `FLAG_NOT_FOCUSABLE` allows Samsung's lock screen behind to still handle
fingerprint and swipe. Remove it if you want full touch ownership (will break biometrics).

---

## AOD Handling

Detect AOD / screen dim state:
```kotlin
// Register in OverlayService
val filter = IntentFilter().apply {
    addAction(Intent.ACTION_SCREEN_OFF)
    addAction(Intent.ACTION_SCREEN_ON)
    addAction("android.intent.action.DREAMING_STARTED")   // AOD started
    addAction("android.intent.action.DREAMING_STOPPED")   // AOD ended
}
registerReceiver(screenReceiver, filter)
```

In AOD mode:
- Reduce tick to 0.2 Hz (update every 5 seconds) to save battery
- Hide tap zones (touch disabled in AOD anyway)
- Switch to minimal display: hours + minutes only, no seconds
- Set background to fully transparent (AMOLED black = off pixels)

---

## Build & Deploy Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Install via ADB (USB debugging must be on)
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch MainActivity to trigger permission flow
adb shell am start -n com.casio.lockscreen/.MainActivity

# Check if service is running
adb shell dumpsys activity services com.casio.lockscreen

# View logs in real time
adb logcat -s CasioOverlay:D CasioEngine:D

# Uninstall
adb uninstall com.casio.lockscreen
```

---

## Permissions Flow (MainActivity)

1. Check `Settings.canDrawOverlays(context)` → if false, send user to
   `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` intent
2. Once granted, call `startForegroundService(Intent(this, OverlayService::class.java))`
3. App can then be closed — service persists

For auto-start on boot, add a `BroadcastReceiver` for `BOOT_COMPLETED`.

---

## Gradle Dependencies

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-service:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.runtime:runtime")

    // Compose in a Service (needed for ComposeView outside Activity)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
}
```

---

## Samsung-Specific Notes

- On One UI 6.1, `TYPE_APPLICATION_OVERLAY` overlays DO appear on the lock screen
  as long as `FLAG_SHOW_WHEN_LOCKED` is set. Tested behaviour on S24 series.
- Samsung's own ClockFace module will still show underneath — position your overlay
  to cover it, or instruct user to set ClockFace to "None" in Good Lock.
- `FLAG_KEEP_SCREEN_ON` is not needed — AOD manages its own display state. Adding it
  will fight Samsung's AOD and drain battery.
- Good Lock's "Always On Display" settings must have AOD enabled for the overlay
  to show during AOD state. The overlay itself doesn't force AOD on.

---

## Development Phases for Claude Code

### Phase 1 — Scaffold
```
Prompt: "Create an Android Kotlin project with package com.casio.lockscreen.
Use Kotlin DSL Gradle. Target SDK 34, min SDK 29.
Add Jetpack Compose BOM 2024.06.00.
Create an empty MainActivity that checks SYSTEM_ALERT_WINDOW permission
and starts OverlayService as a foreground service."
```

### Phase 2 — Casio Engine
```
Prompt: "In CasioEngine.kt, create a Kotlin class with a StateFlow<CasioState>.
Implement three modes: TIME, STOPWATCH, ALARM_SET.
Add a 1-second coroutine ticker that updates time from system clock.
Add onLeftTap() to cycle modes and onRightTap() for context actions per mode."
```

### Phase 3 — 7-Segment Renderer
```
Prompt: "In SegmentDrawer.kt, write a function drawDigit(canvas, digit: Int, x, y, width, height)
using Android Canvas. Use the standard 7-segment A-G mapping. Draw each segment as a
rounded rectangle. Use color #2D3A1E for lit segments and semi-transparent for unlit."
```

### Phase 4 — Face Composable
```
Prompt: "In CasioFaceView.kt, create a Composable that draws the full Casio F-91W face
using Canvas. Include: LCD green background, 6 digit time display using SegmentDrawer,
3-letter day of week, date DD-MM, two tap zones at bottom for LEFT and RIGHT buttons.
Collect state from CasioEngine via StateFlow."
```

### Phase 5 — Overlay Service
```
Prompt: "In OverlayService.kt, create a ForegroundService that adds a full-screen
ComposeView to WindowManager with TYPE_APPLICATION_OVERLAY and FLAG_SHOW_WHEN_LOCKED.
Register BroadcastReceivers for ACTION_SCREEN_OFF and DREAMING_STARTED to switch
CasioEngine to AOD mode (reduced tick, minimal display)."
```

### Phase 6 — AOD Polish
```
Prompt: "Add AOD mode to CasioFaceView. When aodMode=true: hide seconds,
hide tap zones, set background to Color.Transparent, reduce to HH:MM display only.
Animate the digit position slowly (burn-in prevention, shift by ±10px every 60s)."
```

---

## Known Limitations

- Biometric unlock (fingerprint/face) still works because we use `FLAG_NOT_FOCUSABLE`
- Samsung's own lock screen clock shows underneath — user should disable it via
  Settings → Lock Screen → Clock Style → None
- Physical button interception (Vol Up/Down) is not implemented — all interaction
  is via on-screen tap zones
- App requires manual start after install; auto-start needs battery optimisation
  exemption which user must grant manually on Samsung One UI

---

## File Naming Convention

- Kotlin files: PascalCase (`CasioEngine.kt`)
- Composables: PascalCase functions prefixed with the screen (`CasioFaceView`)
- Resources: snake_case (`casio_background.png`)
- Constants: SCREAMING_SNAKE in a `Constants.kt` object

---

## Quick Reference: ADB Useful Commands

```bash
# Check device is connected
adb devices

# Enable USB debugging via ADB (if not already on)
adb shell settings put global development_settings_enabled 1

# Screenshot
adb exec-out screencap -p > screen.png

# Force lock screen
adb shell input keyevent 26

# Simulate AOD (screen off)
adb shell input keyevent KEYCODE_SLEEP

# Check overlay permission status
adb shell appops get com.casio.lockscreen SYSTEM_ALERT_WINDOW
```
