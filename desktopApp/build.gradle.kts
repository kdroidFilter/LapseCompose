import dev.nucleusframework.desktop.application.dsl.CompressionLevel
import dev.nucleusframework.desktop.application.dsl.NativeImageOptimization
import dev.nucleusframework.desktop.application.dsl.ReleaseChannel
import dev.nucleusframework.desktop.application.dsl.ReleaseType
import dev.nucleusframework.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.nucleus)
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_25) }
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.components.resources)
    implementation(libs.nucleus.application)
    implementation(libs.nucleus.decorated.window.tao)
    implementation(libs.nucleus.decorated.window.material3)
    implementation(libs.nucleus.core.runtime)
    implementation(libs.nucleus.system.color)
    implementation(libs.nucleus.autolaunch)
    implementation(libs.nucleus.global.hotkey)
    implementation(libs.nucleus.native.http)
    implementation(libs.nucleus.updater.runtime)
    implementation(libs.composenativetray)
}

composeCompiler {
    stabilityConfigurationFiles.add(
        rootProject.layout.projectDirectory.file("config/stability-config.conf"),
    )
}

val releaseVersion =
    System.getenv("RELEASE_VERSION")
        ?.removePrefix("v")
        ?.takeIf { it.isNotBlank() && it.first().isDigit() }
        ?: "0.1.1"

val nativePackageVersion = releaseVersion.substringBefore("-")

nucleus.application {
    mainClass = "MainKt"
    // Nucleus `run` forks `javaHome`, not the Java plugin toolchain. Point it at JDK 25
    // so a JBR 21 Gradle daemon (typical from IDEA) does not launch class-file 69 bytecode.
    javaHome =
        javaToolchains
            .launcherFor(java.toolchain)
            .get()
            .metadata.installationPath.asFile.absolutePath

    graalvm {
        isEnabled = true
        optimization = NativeImageOptimization.LEVEL_2
    }

    nativeDistributions {
        // SQLDelight JdbcSqliteDriver → DriverManager. jlink does not infer java.sql.
        modules("java.sql")
        // Zip is the silent macOS updater payload; DMG stays the first-install image.
        targetFormats(TargetFormat.Nsis, TargetFormat.Zip, TargetFormat.Portable, TargetFormat.Dmg)
        packageName = "Lapse"
        packageVersion = releaseVersion
        vendor = "Lapse"
        cleanupNativeLibs = true
        compressionLevel = CompressionLevel.Ultra
        homepage = "https://github.com/kdroidFilter/LapseCompose"

        publish {
            github {
                enabled = true
                owner = "kdroidFilter"
                repo = "LapseCompose"
                channel = ReleaseChannel.Latest
                releaseType = ReleaseType.Release
            }
        }

        windows {
            iconFile.set(project.file("appIcons/WindowsIcon.ico"))
            packageVersion = nativePackageVersion
            upgradeUuid = "a88870bd-23a9-4c9d-9833-cdab459415cc"
            portable {
                compressionLevel = CompressionLevel.Normal
            }
        }
        macOS {
            iconFile.set(project.file("appIcons/MacosIcon.icns"))
            packageVersion = nativePackageVersion
            bundleID = "dev.lapse.desktopApp"
        }
        linux {
            iconFile.set(project.file("appIcons/LinuxIcon.png"))
        }
    }
}
