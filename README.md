# Lapse

A quiet desktop companion for active-time tracking and session-focused tasks.

This is a Nucleus + Compose Multiplatform rewrite of [zTomz/Lapse](https://github.com/zTomz/Lapse). The original Flutter/C++ runner is replaced by Kotlin, Metro DI, MVVM+MVI, and Nucleus Native Access instead of hand-written C++.

## Run

```powershell
.\gradlew :desktopApp:run
```

```sh
./gradlew :desktopApp:run
```

Requires JDK 25.

## Install

Prebuilt packages (DMG, NSIS) are on [GitHub Releases](https://github.com/kdroidFilter/LapseCompose/releases): macOS (Apple Silicon and Intel) and Windows (x64). Linux packaging is not published yet.

## Packages

GraalVM native installers for the current OS:

```sh
./gradlew :desktopApp:packageGraalvmDmg    # macOS
./gradlew :desktopApp:packageGraalvmZip    # macOS updater payload
./gradlew :desktopApp:packageGraalvmNsis   # Windows
```

Pushing a `v*` tag builds these packages for macOS (Intel, Apple Silicon) and Windows (x64), then publishes them as a GitHub Release.

## Layout

```
native/       Kotlin/Native (lock, sleep, foreground app) via NNA; idle + boot id from nucleus.system-info
shared/       domain, persistence, Metro graph, ViewModel, Compose UI
desktopApp/   Nucleus entry: overlay + dashboard + tray + autolaunch
```

## Stack

- Nucleus 2.5.12
- Nucleus Native Access 0.7.1
- Kotlin 2.4.10 / Compose Multiplatform 1.12
- Metro DI, AndroidX ViewModel, unidirectional `AppState` + `AppIntent`

## Remaining Flutter parity

The session model, overlay, dashboard, and Linux/mac ports match the original. These gaps vs [zTomz/Lapse](https://github.com/zTomz/Lapse) are still open:

Windows native:

- Application display names. Flutter reads `ProductName` / `FileDescription` from the PE version resource. Compose still uses the executable file name, so the Applications page often shows `Code.exe` instead of "Visual Studio Code".
- Lock. Flutter handles `WTS_SESSION_LOCK` / `UNLOCK`. Compose polls `OpenInputDesktop` and treats a non-`Default` desktop as locked.
- Sleep. Flutter listens for `WM_POWERBROADCAST` suspend/resume. Windows and macOS still infer sleep when wall time jumps more than 15s vs the tick counter. Linux uses `CLOCK_BOOTTIME` instead.
- Idle resolution. Flutter uses `GetLastInputInfo` in milliseconds. Idle time here comes from `nucleus.system-info` in whole seconds.

Tests and data:

- No Compose UI tests. Flutter had widget tests for the overlay and dashboard.
- Flutter stores `%LOCALAPPDATA%\Lapse\state.json`. Compose uses SQLite (`lapse.db`) plus Java Preferences and does not import that file, so an existing Flutter install starts from a blank session.
