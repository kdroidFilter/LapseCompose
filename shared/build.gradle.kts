import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.metro)
    alias(libs.plugins.sqlDelight)
    alias(libs.plugins.structured.coroutines)
    alias(libs.plugins.stability.analyzer)
}

kotlin {
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.ui)
            api(libs.compose.foundation)
            api(libs.compose.resources)
            api(libs.compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.structured.coroutines.annotations)
            implementation(libs.kotlinx.datetime)
            implementation(libs.sqlDelight.runtime)
            api(libs.androidx.lifecycle.viewmodel)
            api(libs.androidx.lifecycle.runtime)
            implementation(libs.multiplatform.settings)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.nucleus.autolaunch)
            implementation(libs.nucleus.core.runtime)
            implementation(libs.sqlDelight.driver.sqlite)
            implementation(libs.sqlite.jdbc)
            implementation(project(":native"))
        }

        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.sqlDelight.driver.sqlite)
            implementation(libs.sqlite.jdbc)
            implementation(libs.multiplatform.settings.test)
        }
    }
}

sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("dev.lapse.db")
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "lapse.shared.generated.resources"
}

structuredCoroutines {
    useKmpCommonProfile()
}

val stabilityConfig = rootProject.layout.projectDirectory.file("config/stability-config.conf")

composeCompiler {
    stabilityConfigurationFiles.add(stabilityConfig)
}

composeStabilityAnalyzer {
    stabilityConfigurationFiles.add(stabilityConfig)
    traceAll {
        enabled.set(false)
        threshold.set(2)
        variants.set(listOf("debug"))
    }
    stabilityValidation {
        enabled.set(true)
        outputDir.set(layout.projectDirectory.dir("stability"))
        includeTests.set(false)
        failOnStabilityChange.set(true)
    }
}
