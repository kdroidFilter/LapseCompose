@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.lapse.nativesupport

import kotlinx.cinterop.pointed
import kotlinx.cinterop.toKString
import platform.posix.DT_DIR
import platform.posix.DT_UNKNOWN
import platform.posix.closedir
import platform.posix.getenv
import platform.posix.opendir
import platform.posix.readdir

private const val REFRESH_MS = 60_000L
private const val MAX_DESKTOP_FILES = 2_000

/**
 * Resolves a human application name from XDG .desktop files, matching Flutter's
 * Windows ProductName lookup. Indexed by desktop id, StartupWMClass, and Exec basename.
 */
internal object LinuxDesktop {
    private val byId = mutableMapOf<String, String>()
    private val byWmClass = mutableMapOf<String, String>()
    private val byExec = mutableMapOf<String, String>()
    private var loadedAtMs = 0L

    fun displayName(executablePath: String, executableName: String, wmClass: String): String {
        refreshIfStale()
        val classKey = wmClass.trim().lowercase()
        if (classKey.isNotEmpty()) {
            byId[classKey]?.let { return it }
            byWmClass[classKey]?.let { return it }
            val lastSegment = classKey.substringAfterLast('.')
            if (lastSegment != classKey) byId[lastSegment]?.let { return it }
        }
        val execKey = fileName(executablePath.ifEmpty { executableName }).lowercase()
        if (execKey.isNotEmpty()) {
            byExec[execKey]?.let { return it }
            byId[execKey]?.let { return it }
        }
        return ""
    }

    private fun refreshIfStale() {
        val now = linuxClockMs(CLOCK_MONOTONIC)
        if (loadedAtMs != 0L && now - loadedAtMs < REFRESH_MS) return
        loadedAtMs = now
        byId.clear()
        byWmClass.clear()
        byExec.clear()
        var remaining = MAX_DESKTOP_FILES
        for (dir in applicationDirs()) {
            remaining = scanDir(dir, remaining, depth = 0)
            if (remaining <= 0) break
        }
    }

    private fun applicationDirs(): List<String> {
        val home = getenv("HOME")?.toKString().orEmpty()
        val dataHome = getenv("XDG_DATA_HOME")?.toKString()?.takeIf { it.isNotEmpty() }
            ?: home.takeIf { it.isNotEmpty() }?.let { "$it/.local/share" }
        val dataDirs = getenv("XDG_DATA_DIRS")?.toKString()?.takeIf { it.isNotEmpty() }
            ?: "/usr/local/share:/usr/share"
        val dirs = buildList {
            if (!dataHome.isNullOrEmpty()) add(dataHome)
            addAll(dataDirs.split(':').filter { it.isNotEmpty() })
            if (home.isNotEmpty()) {
                add("$home/.local/share/flatpak/exports/share")
            }
            add("/var/lib/flatpak/exports/share")
            add("/var/lib/snapd/desktop")
        }
        return dirs.distinct().map { "$it/applications" }
    }

    private fun scanDir(path: String, remaining: Int, depth: Int): Int {
        if (remaining <= 0 || depth > 2) return remaining
        val dir = opendir(path) ?: return remaining
        var left = remaining
        try {
            while (left > 0) {
                val entry = readdir(dir) ?: break
                val name = entry.pointed.d_name.toKString()
                if (name == "." || name == "..") continue
                val child = "$path/$name"
                val type = entry.pointed.d_type.toInt()
                when {
                    name.endsWith(".desktop") -> {
                        ingest(name.removeSuffix(".desktop"), readFile(child) ?: continue)
                        left--
                    }
                    type == DT_DIR || type == DT_UNKNOWN -> {
                        left = scanDir(child, left, depth + 1)
                    }
                }
            }
        } finally {
            closedir(dir)
        }
        return left
    }

    private fun ingest(id: String, text: String) {
        val group = desktopGroup(text) ?: return
        if (group.hidden || group.noDisplay) return
        if (group.type.isNotEmpty() && !group.type.equals("Application", ignoreCase = true)) return
        val name = group.name
        if (name.isEmpty()) return
        putIfMissing(byId, id.lowercase(), name)
        val classKey = group.wmClass.lowercase()
        if (classKey.isNotEmpty()) putIfMissing(byWmClass, classKey, name)
        val execKey = group.execBase.lowercase()
        if (execKey.isNotEmpty() && execKey != "flatpak" && execKey != "env" && execKey != "snap") {
            putIfMissing(byExec, execKey, name)
        }
    }
}

private fun putIfMissing(map: MutableMap<String, String>, key: String, value: String) {
    if (key !in map) map[key] = value
}

private class DesktopKeys(
    val type: String,
    val name: String,
    val wmClass: String,
    val execBase: String,
    val hidden: Boolean,
    val noDisplay: Boolean,
)

private fun desktopGroup(text: String): DesktopKeys? {
    var inEntry = false
    var type = ""
    var name = ""
    var wmClass = ""
    var exec = ""
    var tryExec = ""
    var hidden = false
    var noDisplay = false
    for (raw in text.lineSequence()) {
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith('#') || line.startsWith(';')) continue
        if (line.startsWith('[') && line.endsWith(']')) {
            if (inEntry) break
            inEntry = line.equals("[Desktop Entry]", ignoreCase = true)
            continue
        }
        if (!inEntry) continue
        val eq = line.indexOf('=')
        if (eq <= 0) continue
        val key = line.substring(0, eq).trim()
        val value = line.substring(eq + 1).trim()
        when (key) {
            "Type" -> type = value
            "Name" -> name = value
            "StartupWMClass" -> wmClass = value
            "Exec" -> exec = value
            "TryExec" -> tryExec = value
            "Hidden" -> hidden = value.equals("true", ignoreCase = true)
            "NoDisplay" -> noDisplay = value.equals("true", ignoreCase = true)
        }
    }
    if (!inEntry) return null
    return DesktopKeys(
        type = type,
        name = name,
        wmClass = wmClass,
        execBase = execBasename(tryExec.ifEmpty { exec }),
        hidden = hidden,
        noDisplay = noDisplay,
    )
}

private fun execBasename(exec: String): String {
    val tokens = execTokens(exec)
    if (tokens.isEmpty()) return ""
    var index = 0
    if (tokens[0] == "env") {
        index = 1
        while (index < tokens.size && tokens[index].contains('=')) index++
    }
    if (index >= tokens.size) return ""
    if (tokens[index] == "flatpak" || tokens[index].endsWith("/flatpak")) return ""
    return fileName(tokens[index])
}

private fun execTokens(exec: String): List<String> {
    val result = ArrayList<String>(4)
    var i = 0
    while (i < exec.length) {
        while (i < exec.length && exec[i].isWhitespace()) i++
        if (i >= exec.length) break
        if (exec[i] == '%') break
        if (exec[i] == '"') {
            val end = exec.indexOf('"', i + 1)
            if (end < 0) {
                result.add(exec.substring(i + 1))
                break
            }
            result.add(exec.substring(i + 1, end))
            i = end + 1
        } else {
            val start = i
            while (i < exec.length && !exec[i].isWhitespace()) i++
            result.add(exec.substring(start, i))
        }
    }
    return result
}
