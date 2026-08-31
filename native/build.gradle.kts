plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.nna)
}

kotlin {
    jvmToolchain(25)
    mingwX64()
    jvm()
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

kotlinNativeExport {
    nativeLibName = "lapsehost"
}
