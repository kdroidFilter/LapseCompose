import org.apache.tools.ant.taskdefs.condition.Os

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.nna)
}

kotlin {
    jvmToolchain(25)
    when {
        Os.isFamily(Os.FAMILY_MAC) -> {
            val arch = System.getProperty("os.arch").orEmpty()
            if (arch == "aarch64" || arch == "arm64") macosArm64() else macosX64()
        }
        Os.isFamily(Os.FAMILY_WINDOWS) -> mingwX64()
        else -> error("Native host target is not configured for this OS")
    }
    jvm()
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    sourceSets {
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

kotlinNativeExport {
    nativeLibName = "lapsehost"
}
