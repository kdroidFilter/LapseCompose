@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package dev.lapse.nativesupport

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.AppKit.NSWorkspace
import platform.CoreFoundation.CFArrayGetCount
import platform.CoreFoundation.CFArrayGetValueAtIndex
import platform.CoreFoundation.CFBooleanGetValue
import platform.CoreFoundation.CFDictionaryGetValue
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFNumberGetValue
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringGetCString
import platform.CoreFoundation.CFStringGetLength
import platform.CoreFoundation.CFStringGetMaximumSizeForEncoding
import platform.CoreFoundation.kCFNumberIntType
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreGraphics.CGSessionCopyCurrentDictionary
import platform.CoreGraphics.CGWindowListCopyWindowInfo
import platform.CoreGraphics.kCGNullWindowID
import platform.CoreGraphics.kCGWindowLayer
import platform.CoreGraphics.kCGWindowListExcludeDesktopElements
import platform.CoreGraphics.kCGWindowListOptionOnScreenOnly
import platform.CoreGraphics.kCGWindowName
import platform.CoreGraphics.kCGWindowOwnerPID
import platform.posix.CLOCK_REALTIME
import platform.posix.CLOCK_UPTIME_RAW
import platform.posix.clock_gettime_nsec_np

private const val LOCKED_KEY = "CGSSessionScreenIsLocked"
private const val ON_CONSOLE_KEY = "kCGSSessionOnConsoleKey"

actual class NativeHost actual constructor() {
    private var lastTickMs: Long = uptimeMs()
    private var lastWallMs: Long = wallClockMs()
    private var sleeping: Boolean = false

    actual fun activitySnapshot(): ActivitySnapshot {
        refreshSleep()
        return ActivitySnapshot(
            locked = isLocked(),
            sleeping = sleeping,
        )
    }

    actual fun foregroundApplication(): ForegroundApp? = autoreleasepool {
        val app = NSWorkspace.sharedWorkspace.frontmostApplication ?: return@autoreleasepool null
        val pid = app.processIdentifier
        if (pid == 0) return@autoreleasepool null
        val path = app.executableURL?.path ?: app.bundleURL?.path.orEmpty()
        if (path.isEmpty()) return@autoreleasepool null
        val executableName = fileName(path)
        val displayName = app.localizedName?.takeIf { it.isNotEmpty() } ?: executableName
        ForegroundApp(
            processId = pid,
            executablePath = path,
            executableName = executableName,
            displayName = displayName,
            windowTitle = windowTitle(pid),
        )
    }

    private fun isLocked(): Boolean {
        val dict = CGSessionCopyCurrentDictionary() ?: return false
        try {
            if (cfBoolean(dict, LOCKED_KEY) == true) return true
            return cfBoolean(dict, ON_CONSOLE_KEY) == false
        } finally {
            CFRelease(dict)
        }
    }

    private fun refreshSleep() {
        val tick = uptimeMs()
        val wall = wallClockMs()
        sleeping = wall - lastWallMs - (tick - lastTickMs) > 15_000L
        lastTickMs = tick
        lastWallMs = wall
    }

    private fun windowTitle(pid: Int): String {
        val options = kCGWindowListOptionOnScreenOnly or kCGWindowListExcludeDesktopElements
        val windows = CGWindowListCopyWindowInfo(options, kCGNullWindowID) ?: return ""
        try {
            val count = CFArrayGetCount(windows)
            for (i in 0 until count) {
                val raw = CFArrayGetValueAtIndex(windows, i) ?: continue
                val window: CFDictionaryRef = raw.reinterpret()
                val ownerPid = cfInt(window, kCGWindowOwnerPID) ?: continue
                if (ownerPid != pid) continue
                val layer = cfInt(window, kCGWindowLayer) ?: continue
                if (layer != 0) continue
                return cfString(window, kCGWindowName)
            }
            return ""
        } finally {
            CFRelease(windows)
        }
    }

    private fun fileName(path: String): String {
        val slash = path.lastIndexOf('/')
        return if (slash >= 0) path.substring(slash + 1) else path
    }
}

private fun uptimeMs(): Long =
    (clock_gettime_nsec_np(CLOCK_UPTIME_RAW.convert()) / 1_000_000uL).toLong()

private fun wallClockMs(): Long =
    (clock_gettime_nsec_np(CLOCK_REALTIME.convert()) / 1_000_000uL).toLong()

private fun cfBoolean(dict: CFDictionaryRef?, key: String): Boolean? {
    val cfKey = CFStringCreateWithCString(null, key, kCFStringEncodingUTF8) ?: return null
    try {
        val value = CFDictionaryGetValue(dict, cfKey) ?: return null
        return CFBooleanGetValue(value.reinterpret())
    } finally {
        CFRelease(cfKey)
    }
}

private fun cfInt(dict: CFDictionaryRef?, key: CPointer<out CPointed>?): Int? = memScoped {
    val value = CFDictionaryGetValue(dict, key) ?: return null
    val out = alloc<IntVar>()
    if (!CFNumberGetValue(value.reinterpret(), kCFNumberIntType, out.ptr)) return null
    out.value
}

private fun cfString(dict: CFDictionaryRef?, key: CPointer<out CPointed>?): String {
    val value = CFDictionaryGetValue(dict, key) ?: return ""
    val length = CFStringGetLength(value.reinterpret())
    if (length <= 0) return ""
    return memScoped {
        val maxSize = CFStringGetMaximumSizeForEncoding(length, kCFStringEncodingUTF8) + 1
        val buf = allocArray<ByteVar>(maxSize)
        if (CFStringGetCString(value.reinterpret(), buf, maxSize, kCFStringEncodingUTF8)) {
            buf.toKString()
        } else {
            ""
        }
    }
}
