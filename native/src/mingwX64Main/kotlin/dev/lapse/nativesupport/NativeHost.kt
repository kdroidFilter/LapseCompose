@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.lapse.nativesupport

import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.windows.CloseDesktop
import platform.windows.CloseHandle
import platform.windows.DWORDVar
import platform.windows.FILETIME
import platform.windows.GetForegroundWindow
import platform.windows.GetLastInputInfo
import platform.windows.GetSystemTimeAsFileTime
import platform.windows.GetTickCount
import platform.windows.GetTickCount64
import platform.windows.GetUserObjectInformationW
import platform.windows.GetWindowTextLengthW
import platform.windows.GetWindowTextW
import platform.windows.GetWindowThreadProcessId
import platform.windows.LASTINPUTINFO
import platform.windows.OpenInputDesktop
import platform.windows.OpenProcess
import platform.windows.PROCESS_QUERY_LIMITED_INFORMATION
import platform.windows.QueryFullProcessImageNameW
import platform.windows.UOI_NAME
import platform.windows.WCHARVar

private const val DESKTOP_READOBJECTS = 1u

actual class NativeHost actual constructor() {
    private var lastTickMs: Long = GetTickCount64().toLong()
    private var lastWallMs: Long = wallClockMs()
    private var sleeping: Boolean = false

    init {
        WindowsSession.ensureStarted()
    }

    actual fun activitySnapshot(): ActivitySnapshot {
        refreshSleep()
        return ActivitySnapshot(
            idleMilliseconds = idleMilliseconds(),
            locked = WindowsSession.locked ?: isLockedDesktop(),
            sleeping = WindowsSession.sleeping ?: sleeping,
        )
    }

    actual fun foregroundApplication(): ForegroundApp? = memScoped {
        val hwnd = GetForegroundWindow() ?: return null
        val pid = alloc<DWORDVar>()
        GetWindowThreadProcessId(hwnd, pid.ptr)
        if (pid.value == 0u) return null
        val process = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION.toUInt(), 0, pid.value) ?: return null
        try {
            val pathBuf = allocArray<WCHARVar>(32768)
            val pathSize = alloc<DWORDVar>()
            pathSize.value = 32768u
            if (QueryFullProcessImageNameW(process, 0u, pathBuf, pathSize.ptr) == 0) return null
            val path = pathBuf.toKString()
            val executableName = windowsFileName(path)
            val titleLength = GetWindowTextLengthW(hwnd)
            val title = if (titleLength > 0) {
                val titleBuf = allocArray<WCHARVar>(titleLength + 1)
                GetWindowTextW(hwnd, titleBuf, titleLength + 1)
                titleBuf.toKString()
            } else {
                ""
            }
            ForegroundApp(
                processId = pid.value.toInt(),
                executablePath = path,
                executableName = executableName,
                displayName = WindowsVersion.displayName(path),
                windowTitle = title,
            )
        } finally {
            CloseHandle(process)
        }
    }

    private fun idleMilliseconds(): Long = memScoped {
        val info = alloc<LASTINPUTINFO>()
        info.cbSize = sizeOf<LASTINPUTINFO>().convert()
        if (GetLastInputInfo(info.ptr) == 0) return 0L
        val now = GetTickCount().toLong() and 0xFFFFFFFFL
        val last = info.dwTime.toLong() and 0xFFFFFFFFL
        val idle = now - last
        if (idle < 0) idle + 0x1_0000_0000L else idle
    }

    private fun isLockedDesktop(): Boolean = memScoped {
        val desk = OpenInputDesktop(0u, 0, DESKTOP_READOBJECTS) ?: return true
        val name = allocArray<WCHARVar>(256)
        val needed = alloc<DWORDVar>()
        val ok = GetUserObjectInformationW(
            desk,
            UOI_NAME,
            name,
            (256 * 2).toUInt(),
            needed.ptr,
        )
        CloseDesktop(desk)
        if (ok == 0) return false
        name.toKString() != "Default"
    }

    private fun refreshSleep() {
        val tick = GetTickCount64().toLong()
        val wall = wallClockMs()
        val tickDelta = tick - lastTickMs
        val wallDelta = wall - lastWallMs
        sleeping = wallDelta - tickDelta > 15_000L
        lastTickMs = tick
        lastWallMs = wall
    }

    private fun wallClockMs(): Long = memScoped {
        val fileTime = alloc<FILETIME>()
        GetSystemTimeAsFileTime(fileTime.ptr)
        val high = fileTime.dwHighDateTime.toULong()
        val low = fileTime.dwLowDateTime.toULong()
        ((high shl 32) or low).toLong() / 10_000L
    }
}
