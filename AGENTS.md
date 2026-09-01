# Lapse

## Project Structure

```
├── native/            # Kotlin/Native + NNA (no Compose)
├── shared/            # business logic, UI, data
└── desktopApp/        # thin Nucleus entry point
```

All application code except the window host lives in `shared`. `desktopApp` only starts Nucleus windows and the tray. Idle time and boot id come from `nucleus.system-info`. Lock, sleep, and the focused window live in `native` (`expect` in `nativeMain`, `actual` in `mingwX64Main` / `macosMain` / `linuxMain`) and are called from the JVM through Nucleus Native Access — never C++. Autostart uses `nucleus.autolaunch`. The native Gradle target is host-conditional (`mingwX64` / `macosArm64` / `macosX64` / `linuxX64` / `linuxArm64`).

## Build

| Target | Command |
|--------|---------|
| Desktop | `./gradlew :desktopApp:run` |
| Tests | `./gradlew :shared:jvmTest` |
| Stability dump | `./gradlew :shared:stabilityDump` |
| macOS packages | `./gradlew :desktopApp:packageGraalvmDmg :desktopApp:packageGraalvmZip` |
| Windows package | `./gradlew :desktopApp:packageGraalvmNsis` |

Release: push a `v*` tag. GitHub Actions (Nucleus `setup-nucleus` / `generate-update-yml` / `publish-release`) builds GraalVM installers for macOS (arm64 + x64) and Windows (x64) and publishes a GitHub Release. Linux is not in the matrix yet.
