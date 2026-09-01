# Lapse

## Project Structure

```
├── native/            # Kotlin/Native + NNA (no Compose)
├── shared/            # business logic, UI, data
└── desktopApp/        # thin Nucleus entry point
```

All application code except the window host lives in `shared`. `desktopApp` only starts Nucleus windows and the tray. Boot id comes from `nucleus.system-info`. Idle time is millisecond-precise in `native` on Windows (`GetLastInputInfo`) and macOS (`CGEventSourceSecondsSinceLastEventType`); Linux uses `nucleus.system-info` (seconds). Lock, sleep, and the focused window live in `native` (`expect` in `nativeMain`, `actual` in `mingwX64Main` / `macosMain` / `linuxMain`) and are called from the JVM through Nucleus Native Access — never C++. Windows app names come from the PE `ProductName` / `FileDescription`. Autostart uses `nucleus.autolaunch`. The native Gradle target is host-conditional (`mingwX64` / `macosArm64` / `macosX64` / `linuxX64` / `linuxArm64`).

## Build

| Target | Command |
|--------|---------|
| Desktop | `./gradlew :desktopApp:run` |
| Tests | `./gradlew :shared:jvmTest` |
| Stability dump | `./gradlew :shared:stabilityDump` |
| macOS packages | `./gradlew :desktopApp:packageGraalvmDmg :desktopApp:packageGraalvmZip` |
| Windows package | `./gradlew :desktopApp:packageGraalvmNsis` |

Global hotkey: `Ctrl+Alt+L` toggles the overlay through `nucleus.global-hotkey`, registered in `desktopApp/src/main/kotlin/main.kt` and gated by the `globalHotkey` preference (Settings row + tray checkbox), off by default like autostart. The combo is fixed; the native side maps the AWT key code and the portable modifier flags to Carbon (`ALT` → optionKey, `CONTROL` → controlKey).

Auto-update: `desktopApp/src/main/kotlin/Update.kt` checks the GitHub releases once at startup and downloads in the background. When an installer is waiting the tray icon grows a dot and an "Update now" item; otherwise it installs on quit. Settings and the tray also offer a manual check (`DesktopUpdate.checkNow`, state in `UpdateStatus`); the last tray item is a disabled `NucleusApp.version` label.

UI strings live in `shared/src/commonMain/composeResources/values/strings.xml` (English, the default) with full translations in `values-fr/` and `values-he/`. All three files must keep the same keys. The Hebrew UI is not mirrored — the layout stays LTR.

Release: push a `v*` tag. GitHub Actions (Nucleus `setup-nucleus` / `generate-update-yml` / `publish-release`) builds GraalVM installers for macOS (arm64 + x64) and Windows (x64) and publishes a GitHub Release. Linux is not in the matrix yet.
