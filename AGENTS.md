# Lapse

## Project Structure

```
├── native/            # Kotlin/Native + NNA (no Compose)
├── shared/            # business logic, UI, data
└── desktopApp/        # thin Nucleus entry point
```

All application code except the window host lives in `shared`. `desktopApp` only starts Nucleus windows and the tray. Platform APIs that Nucleus does not wrap (idle, lock, sleep, foreground process, boot id) live in `native` (`expect` in `nativeMain`, `actual` in `mingwX64Main` / `macosMain`; Linux later) and are called from the JVM through Nucleus Native Access — never C++. Autostart uses `nucleus.autolaunch`. The native Gradle target is host-conditional (`mingwX64` / `macosArm64` / `macosX64`).

## Build

| Target | Command |
|--------|---------|
| Desktop | `./gradlew :desktopApp:run` |
| Tests | `./gradlew :shared:jvmTest` |
| Stability dump | `./gradlew :shared:stabilityDump` |
