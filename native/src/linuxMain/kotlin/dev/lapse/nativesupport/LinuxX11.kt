@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.lapse.nativesupport

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import lapse.x11.AtomVar
import lapse.x11.Display
import lapse.x11.False
import lapse.x11.Success
import lapse.x11.Window
import lapse.x11.XA_CARDINAL
import lapse.x11.XA_STRING
import lapse.x11.XA_WINDOW
import lapse.x11.XDefaultRootWindow
import lapse.x11.XErrorEvent
import lapse.x11.XFree
import lapse.x11.XGetWindowProperty
import lapse.x11.XInternAtom
import lapse.x11.XOpenDisplay
import lapse.x11.XSetErrorHandler

private val ignoreXErrors = staticCFunction { _: CPointer<Display>?, _: CPointer<XErrorEvent>? -> 0 }

internal object LinuxX11 {
    private val display: CPointer<Display>? = run {
        XSetErrorHandler(ignoreXErrors)
        XOpenDisplay(null)
    }

    fun foregroundApplication(): ForegroundApp? {
        val dpy = display ?: return null
        val window = activeWindow(dpy) ?: return null
        if (window == 0uL) return null
        val pid = windowPid(dpy, window) ?: return null
        val title = windowTitle(dpy, window)
        return processFromPid(pid, title)
    }

    private fun activeWindow(dpy: CPointer<Display>): Window? {
        val atom = XInternAtom(dpy, "_NET_ACTIVE_WINDOW", False)
        if (atom == 0uL) return null
        return readULong(dpy, XDefaultRootWindow(dpy), atom, XA_WINDOW)
    }

    private fun windowPid(dpy: CPointer<Display>, window: Window): Int? {
        val atom = XInternAtom(dpy, "_NET_WM_PID", False)
        if (atom == 0uL) return null
        return readULong(dpy, window, atom, XA_CARDINAL)?.toInt()
    }

    private fun windowTitle(dpy: CPointer<Display>, window: Window): String {
        val utf8 = XInternAtom(dpy, "UTF8_STRING", False)
        val netName = XInternAtom(dpy, "_NET_WM_NAME", False)
        if (netName != 0uL && utf8 != 0uL) {
            readString(dpy, window, netName, utf8)?.let { return it }
        }
        return readString(dpy, window, XA_STRING, XA_STRING).orEmpty()
    }

    private fun readULong(
        dpy: CPointer<Display>,
        window: Window,
        atom: ULong,
        type: ULong,
    ): ULong? {
        val property = readProperty(dpy, window, atom, type, 1) ?: return null
        val value = property.bytes.reinterpret<ULongVar>().pointed.value
        XFree(property.bytes)
        return value
    }

    private fun readString(
        dpy: CPointer<Display>,
        window: Window,
        atom: ULong,
        type: ULong,
    ): String? {
        val property = readProperty(dpy, window, atom, type, 4096) ?: return null
        val text = property.bytes.reinterpret<kotlinx.cinterop.ByteVar>().toKString()
        XFree(property.bytes)
        return text
    }

    private class RawProperty(val bytes: CPointer<UByteVar>)

    private fun readProperty(
        dpy: CPointer<Display>,
        window: Window,
        atom: ULong,
        type: ULong,
        length: Long,
    ): RawProperty? = memScoped {
        val actualType = alloc<AtomVar>()
        val actualFormat = alloc<IntVar>()
        val nitems = alloc<ULongVar>()
        val bytesAfter = alloc<ULongVar>()
        val prop = alloc<CPointerVar<UByteVar>>()
        val rc = XGetWindowProperty(
            dpy,
            window,
            atom,
            0,
            length,
            False,
            type,
            actualType.ptr,
            actualFormat.ptr,
            nitems.ptr,
            bytesAfter.ptr,
            prop.ptr,
        )
        val data = prop.value
        if (rc != Success || data == null || nitems.value == 0uL) {
            if (data != null) XFree(data)
            return null
        }
        RawProperty(data)
    }
}
