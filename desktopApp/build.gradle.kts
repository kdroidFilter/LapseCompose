import dev.nucleusframework.desktop.application.dsl.CompressionLevel
import dev.nucleusframework.desktop.application.dsl.NativeImageOptimization
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
    implementation(libs.composenativetray)
}

composeCompiler {
    stabilityConfigurationFiles.add(
        rootProject.layout.projectDirectory.file("config/stability-config.conf"),
    )
}

nucleus.application {
    mainClass = "MainKt"

    nativeDistributions {
        // SQLDelight JdbcSqliteDriver → DriverManager. jlink does not infer java.sql.
        modules("java.sql")
        targetFormats(TargetFormat.Nsis, TargetFormat.Zip, TargetFormat.Portable, TargetFormat.Dmg)
        packageName = "Lapse"
        packageVersion = "0.1.0"
        vendor = "Lapse"
        compressionLevel = CompressionLevel.Ultra
        windows {
            iconFile.set(project.file("appIcons/WindowsIcon.ico"))
            packageVersion = "0.1.0"
            portable {
                compressionLevel = CompressionLevel.Normal
            }
        }
        macOS {
            iconFile.set(project.file("appIcons/MacosIcon.icns"))
        }
        linux {
            iconFile.set(project.file("appIcons/LinuxIcon.png"))
        }
    }

    graalvm {
        isEnabled = true
        optimization = NativeImageOptimization.LEVEL_2
    }
}
