@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.lapse.nativesupport

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.get
import kotlinx.cinterop.pointed
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import platform.posix.closedir
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.opendir
import platform.posix.readlink
import platform.posix.readdir

private val lockerCommands = setOf(
    "swaylock",
    "hyprlock",
    "gtklock",
    "i3lock",
    "i3lock-color",
    "waylock",
    "xscreensaver",
    "xlock",
    "slock",
    "kscreenlocker_greet",
    "light-locker",
    "physlock",
)

internal fun fileName(path: String): String {
    val slash = path.lastIndexOf('/')
    return if (slash >= 0) path.substring(slash + 1) else path
}

internal fun readFile(path: String): String? {
    val file = fopen(path, "r") ?: return null
    try {
        return memScoped {
            val buf = allocArray<ByteVar>(4096)
            val n = fread(buf, 1u.convert(), 4095u.convert(), file)
            if (n == 0uL) return@memScoped ""
            buf[n.toInt()] = 0.toByte()
            buf.toKString().trim('\n', '\r', '\u0000', ' ')
        }
    } finally {
        fclose(file)
    }
}

internal fun exePath(pid: Int): String? = memScoped {
    val buf = allocArray<ByteVar>(4096)
    val n = readlink("/proc/$pid/exe", buf, 4095u.convert())
    if (n <= 0) return null
    buf[n.toInt()] = 0.toByte()
    buf.toKString()
}

internal fun processFromPid(pid: Int, windowTitle: String): ForegroundApp? {
    if (pid <= 0) return null
    val path = exePath(pid).orEmpty()
    val comm = readFile("/proc/$pid/comm").orEmpty()
    val executableName = when {
        path.isNotEmpty() -> fileName(path)
        comm.isNotEmpty() -> comm
        else -> return null
    }
    return ForegroundApp(
        processId = pid,
        executablePath = path,
        executableName = executableName,
        displayName = comm.ifEmpty { executableName },
        windowTitle = windowTitle,
    )
}

internal fun lockProcessRunning(): Boolean {
    val dir = opendir("/proc") ?: return false
    try {
        while (true) {
            val entry = readdir(dir) ?: break
            val name = entry.pointed.d_name.toKString()
            if (name.any { it !in '0'..'9' }) continue
            val comm = readFile("/proc/$name/comm") ?: continue
            if (comm in lockerCommands) return true
        }
        return false
    } finally {
        closedir(dir)
    }
}
