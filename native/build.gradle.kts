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
        Os.isFamily(Os.FAMILY_WINDOWS) -> {
            val mingw = mingwX64()
            mingw.compilations.getByName("main").cinterops {
                val win32 by creating {
                    defFile(project.file("src/nativeInterop/cinterop/win32.def"))
                }
            }
        }
        else -> {
            val arch = System.getProperty("os.arch").orEmpty()
            val linux = if (arch == "aarch64" || arch == "arm64") linuxArm64() else linuxX64()
            linux.compilations.getByName("main").cinterops {
                val x11 by creating {
                    defFile(project.file("src/nativeInterop/cinterop/x11.def"))
                }
                val dbus by creating {
                    defFile(project.file("src/nativeInterop/cinterop/dbus.def"))
                }
            }
        }
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
