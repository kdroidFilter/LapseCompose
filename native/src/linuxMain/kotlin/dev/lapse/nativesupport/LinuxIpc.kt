@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.lapse.nativesupport

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.linux.sockaddr_un
import platform.posix.AF_UNIX
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_RCVTIMEO
import platform.posix.close
import platform.posix.connect
import platform.posix.getenv
import platform.posix.memcpy
import platform.posix.memset
import platform.posix.recv
import platform.posix.send
import platform.posix.setsockopt
import platform.posix.socket
import platform.posix.timeval

private const val I3_MAGIC = "i3-ipc"
private const val I3_GET_TREE = 4

internal object LinuxIpc {
    fun foregroundApplication(): ForegroundApp? =
        hyprlandForeground() ?: swayForeground()

    private fun hyprlandForeground(): ForegroundApp? {
        val signature = getenv("HYPRLAND_INSTANCE_SIGNATURE")?.toKString() ?: return null
        if (signature.isEmpty()) return null
        val runtime = getenv("XDG_RUNTIME_DIR")?.toKString()
        val candidates = buildList {
            if (!runtime.isNullOrEmpty()) add("$runtime/hypr/$signature/.socket.sock")
            add("/tmp/hypr/$signature/.socket.sock")
        }
        val reply = candidates.firstNotNullOfOrNull { unixCall(it, "activewindow") } ?: return null
        val pid = field(reply, "pid")?.toIntOrNull() ?: return null
        val title = field(reply, "title").orEmpty()
        return processFromPid(pid, title)
    }

    private fun swayForeground(): ForegroundApp? {
        val path = getenv("SWAYSOCK")?.toKString() ?: return null
        if (path.isEmpty()) return null
        val tree = i3Tree(path) ?: return null
        val focused = focusedChunk(tree) ?: return null
        val pid = Regex("\"pid\"\\s*:\\s*(\\d+)").find(focused)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val title = Regex("\"name\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
            .find(focused)
            ?.groupValues
            ?.get(1)
            ?.unescapeJson()
            .orEmpty()
        return processFromPid(pid, title)
    }

    private fun focusedChunk(json: String): String? {
        val match = Regex("\"focused\"\\s*:\\s*true").find(json) ?: return null
        val start = (match.range.first - 800).coerceAtLeast(0)
        val end = (match.range.last + 200).coerceAtMost(json.length)
        return json.substring(start, end)
    }

    private fun field(text: String, key: String): String? {
        val line = text.lineSequence().firstOrNull { it.startsWith("$key:") } ?: return null
        return line.substringAfter(':').trim().ifEmpty { null }
    }

    private fun i3Tree(path: String): String? {
        val fd = unixConnect(path) ?: return null
        try {
            val header = ByteArray(14)
            I3_MAGIC.encodeToByteArray().copyInto(header)
            putUInt32Le(header, 6, 0)
            putUInt32Le(header, 10, I3_GET_TREE)
            if (!sendAll(fd, header)) return null
            val replyHeader = recvExact(fd, 14) ?: return null
            if (replyHeader.decodeToString(0, 6) != I3_MAGIC) return null
            val size = getUInt32Le(replyHeader, 6)
            if (size <= 0 || size > 8_000_000) return null
            val payload = recvExact(fd, size) ?: return null
            return payload.decodeToString()
        } finally {
            close(fd)
        }
    }

    private fun unixCall(path: String, payload: String): String? {
        val fd = unixConnect(path) ?: return null
        try {
            val bytes = payload.encodeToByteArray()
            if (!sendAll(fd, bytes)) return null
            return recvAvailable(fd)
        } finally {
            close(fd)
        }
    }

    private fun unixConnect(path: String): Int? = memScoped {
        if (path.length >= 107) return null
        val fd = socket(AF_UNIX, SOCK_STREAM, 0)
        if (fd < 0) return null
        val timeout = alloc<timeval>()
        timeout.tv_sec = 0
        timeout.tv_usec = 200_000
        setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, timeout.ptr, sizeOf<timeval>().convert())
        val addr = alloc<sockaddr_un>()
        memset(addr.ptr, 0, sizeOf<sockaddr_un>().convert())
        addr.sun_family = AF_UNIX.convert()
        path.encodeToByteArray().usePinned { pinned ->
            memcpy(addr.sun_path, pinned.addressOf(0), path.length.convert())
        }
        if (connect(fd, addr.ptr.reinterpret(), sizeOf<sockaddr_un>().convert()) != 0) {
            close(fd)
            return null
        }
        fd
    }

    private fun sendAll(fd: Int, bytes: ByteArray): Boolean {
        bytes.usePinned { pinned ->
            var sent = 0
            while (sent < bytes.size) {
                val n = send(fd, pinned.addressOf(sent), (bytes.size - sent).convert(), 0)
                if (n <= 0) return false
                sent += n.toInt()
            }
        }
        return true
    }

    private fun recvExact(fd: Int, size: Int): ByteArray? {
        val bytes = ByteArray(size)
        bytes.usePinned { pinned ->
            var received = 0
            while (received < size) {
                val n = recv(fd, pinned.addressOf(received), (size - received).convert(), 0)
                if (n <= 0) return null
                received += n.toInt()
            }
        }
        return bytes
    }

    private fun recvAvailable(fd: Int): String? {
        val bytes = ByteArray(8192)
        val n = bytes.usePinned { pinned ->
            recv(fd, pinned.addressOf(0), 8191u.convert(), 0)
        }
        if (n <= 0) return null
        return bytes.decodeToString(0, n.toInt())
    }

    private fun putUInt32Le(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xff).toByte()
        bytes[offset + 1] = ((value shr 8) and 0xff).toByte()
        bytes[offset + 2] = ((value shr 16) and 0xff).toByte()
        bytes[offset + 3] = ((value shr 24) and 0xff).toByte()
    }

    private fun getUInt32Le(bytes: ByteArray, offset: Int): Int {
        val b0 = bytes[offset].toInt() and 0xff
        val b1 = bytes[offset + 1].toInt() and 0xff
        val b2 = bytes[offset + 2].toInt() and 0xff
        val b3 = bytes[offset + 3].toInt() and 0xff
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    private fun String.unescapeJson(): String =
        replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n")
}
