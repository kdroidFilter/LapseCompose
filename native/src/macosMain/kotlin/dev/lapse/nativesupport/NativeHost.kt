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
import platform.CoreGraphics.CGEventSourceSecondsSinceLastEventType
import platform.CoreGraphics.CGSessionCopyCurrentDictionary
import platform.CoreGraphics.CGWindowListCopyWindowInfo
import platform.CoreGraphics.kCGEventSourceStateHIDSystemState
import platform.CoreGraphics.kCGNullWindowID
import platform.CoreGraphics.kCGWindowLayer
import platform.CoreGraphics.kCGWindowListExcludeDesktopElements
import platform.CoreGraphics.kCGWindowListOptionOnScreenOnly
import platform.CoreGraphics.kCGWindowName
import platform.CoreGraphics.kCGWindowOwnerPID
import platform.posix.CLOCK_MONOTONIC
import platform.posix.CLOCK_UPTIME_RAW
import platform.posix.clock_gettime_nsec_np

private const val LOCKED_KEY = "CGSSessionScreenIsLocked"
private const val ON_CONSOLE_KEY = "kCGSSessionOnConsoleKey"

/** `kCGAnyInputEventType`: a macro cast, so cinterop does not expose it. */
private const val ANY_INPUT_EVENT_TYPE = 0xFFFFFFFFu

actual class NativeHost actual constructor() {
    private var lastSleepDebtMs: Long = sleepDebtMs()
    private var sleeping: Boolean = false

    actual fun activitySnapshot(): ActivitySnapshot {
        refreshSleep()
        return ActivitySnapshot(
            idleMilliseconds = idleMilliseconds(),
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

    private fun idleMilliseconds(): Long {
        val seconds = CGEventSourceSecondsSinceLastEventType(
            kCGEventSourceStateHIDSystemState,
            ANY_INPUT_EVENT_TYPE,
        )
        return if (seconds < 0.0) -1L else (seconds * 1000.0).toLong()
    }

    private fun refreshSleep() {
        val debt = sleepDebtMs()
        sleeping = debt - lastSleepDebtMs > 15_000L
        lastSleepDebtMs = debt
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

/** CLOCK_MONOTONIC keeps counting while asleep, CLOCK_UPTIME_RAW does not: the gap is sleep time. */
private fun sleepDebtMs(): Long =
    (clockMs(CLOCK_MONOTONIC) - clockMs(CLOCK_UPTIME_RAW)).coerceAtLeast(0L)

private fun clockMs(clockId: Int): Long =
    (clock_gettime_nsec_np(clockId.convert()) / 1_000_000uL).toLong()

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
