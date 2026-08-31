# Lapse

A quiet Windows companion for active-time tracking and session-focused tasks.

This is a Nucleus + Compose Multiplatform rewrite of [zTomz/Lapse](https://github.com/zTomz/Lapse). The original Flutter/C++ runner is replaced by Kotlin, Metro DI, MVVM+MVI, and Nucleus Native Access instead of hand-written C++.

## Run

```powershell
.\gradlew :desktopApp:run
```

Requires JDK 25.

## Layout

```
native/       Kotlin/Native Win32 (idle, lock, sleep, foreground app, boot id) via NNA
shared/       domain, persistence, Metro graph, ViewModel, Compose UI
desktopApp/   Nucleus entry: overlay + dashboard + tray + autolaunch
```

## Stack

- Nucleus 2.5.11
- Nucleus Native Access 0.7.0
- Kotlin 2.4.10 / Compose Multiplatform 1.12
- Metro DI, AndroidX ViewModel, unidirectional `AppState` + `AppIntent`
