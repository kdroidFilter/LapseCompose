@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.lapse.nativesupport

import kotlin.concurrent.AtomicInt
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.wcstr
import lapse.win32.NOTIFY_FOR_THIS_SESSION
import lapse.win32.WTSRegisterSessionNotification
import lapse.win32.WTSUnRegisterSessionNotification
import platform.windows.CreateThread
import platform.windows.CreateWindowExW
import platform.windows.DefWindowProcW
import platform.windows.DispatchMessageW
import platform.windows.ERROR_CLASS_ALREADY_EXISTS
import platform.windows.GetLastError
import platform.windows.GetMessageW
import platform.windows.GetModuleHandleW
import platform.windows.HWND_MESSAGE
import platform.windows.LPARAM
import platform.windows.LRESULT
import platform.windows.MSG
import platform.windows.PBT_APMRESUMEAUTOMATIC
import platform.windows.PBT_APMRESUMESUSPEND
import platform.windows.PBT_APMSUSPEND
import platform.windows.RegisterClassW
import platform.windows.TranslateMessage
import platform.windows.TRUE
import platform.windows.UINT
import platform.windows.WM_POWERBROADCAST
import platform.windows.WM_WTSSESSION_CHANGE
import platform.windows.WNDCLASSW
import platform.windows.WPARAM

private const val UNKNOWN = 0
private const val FLAG_YES = 1
private const val FLAG_NO = -1
private const val WTS_LOCK = 7
private const val WTS_UNLOCK = 8

/**
 * Hidden message window matching Flutter's notification HWND: WTS lock/unlock
 * and WM_POWERBROADCAST suspend/resume. Flags stay unknown until the first event
 * so callers can fall back to OpenInputDesktop / wall-clock inference.
 */
internal object WindowsSession {
    private val lockedState = AtomicInt(UNKNOWN)
    private val sleepingState = AtomicInt(UNKNOWN)
    private val started = AtomicInt(0)

    val locked: Boolean?
        get() = decode(lockedState.value)

    val sleeping: Boolean?
        get() = decode(sleepingState.value)

    fun ensureStarted() {
        if (!started.compareAndSet(0, 1)) return
        val thread = CreateThread(
            null,
            0u,
            staticCFunction { _ ->
                runNotificationLoop()
                0u
            },
            null,
            0u,
            null,
        )
        if (thread == null) started.value = 0
    }

    fun onLockChanged(locked: Boolean) {
        lockedState.value = if (locked) FLAG_YES else FLAG_NO
    }

    fun onSleepChanged(sleeping: Boolean) {
        sleepingState.value = if (sleeping) FLAG_YES else FLAG_NO
    }
}

private fun decode(value: Int): Boolean? = when (value) {
    FLAG_YES -> true
    FLAG_NO -> false
    else -> null
}

private val notificationProc = staticCFunction {
        hwnd: platform.windows.HWND?,
        message: UINT,
        wParam: WPARAM,
        lParam: LPARAM,
    ->
    handleNotification(hwnd, message, wParam, lParam)
}

private fun handleNotification(
    hwnd: platform.windows.HWND?,
    message: UINT,
    wParam: WPARAM,
    lParam: LPARAM,
): LRESULT {
    when (message.toInt()) {
        WM_WTSSESSION_CHANGE -> {
            when (wParam.toInt()) {
                WTS_LOCK -> WindowsSession.onLockChanged(true)
                WTS_UNLOCK -> WindowsSession.onLockChanged(false)
            }
            return 0
        }
        WM_POWERBROADCAST -> {
            when (wParam.toInt()) {
                PBT_APMSUSPEND -> WindowsSession.onSleepChanged(true)
                PBT_APMRESUMEAUTOMATIC, PBT_APMRESUMESUSPEND -> WindowsSession.onSleepChanged(false)
            }
            return TRUE.toLong()
        }
    }
    return DefWindowProcW(hwnd, message, wParam, lParam)
}

private fun runNotificationLoop() = memScoped {
    val className = "LAPSE_NOTIFICATION_WINDOW".wcstr
    val wc = alloc<WNDCLASSW>()
    wc.lpfnWndProc = notificationProc
    wc.hInstance = GetModuleHandleW(null)
    wc.lpszClassName = className.ptr
    val atom = RegisterClassW(wc.ptr)
    if (atom == 0u.toUShort() && GetLastError() != ERROR_CLASS_ALREADY_EXISTS.toUInt()) return@memScoped
    val hwnd = CreateWindowExW(
        0u,
        "LAPSE_NOTIFICATION_WINDOW",
        "Lapse notifications",
        0u,
        0,
        0,
        0,
        0,
        HWND_MESSAGE,
        null,
        GetModuleHandleW(null),
        null,
    ) ?: return@memScoped
    WTSRegisterSessionNotification(hwnd, NOTIFY_FOR_THIS_SESSION.toUInt())
    val msg = alloc<MSG>()
    while (GetMessageW(msg.ptr, null, 0u, 0u) > 0) {
        TranslateMessage(msg.ptr)
        DispatchMessageW(msg.ptr)
    }
    WTSUnRegisterSessionNotification(hwnd)
}
