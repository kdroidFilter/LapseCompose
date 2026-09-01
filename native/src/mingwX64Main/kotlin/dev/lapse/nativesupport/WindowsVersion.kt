@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.lapse.nativesupport

import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import lapse.win32.GetFileVersionInfoSizeW
import lapse.win32.GetFileVersionInfoW
import lapse.win32.VerQueryValueW
import kotlinx.cinterop.ByteVar
import platform.windows.WCHARVar

private const val CACHE_LIMIT = 256

internal object WindowsVersion {
    private val cache = mutableMapOf<String, String>()

    fun displayName(path: String): String {
        cache[path]?.let { return it }
        val name = queryProductName(path) ?: windowsFileName(path)
        if (cache.size >= CACHE_LIMIT) {
            cache.remove(cache.keys.first())
        }
        cache[path] = name
        return name
    }
}

internal fun windowsFileName(path: String): String {
    val slash = path.lastIndexOfAny(charArrayOf('\\', '/'))
    return if (slash >= 0) path.substring(slash + 1) else path
}

private fun queryProductName(path: String): String? = memScoped {
    val ignored = alloc<UIntVar>()
    val size = GetFileVersionInfoSizeW(path, ignored.ptr)
    if (size == 0u) return null
    val data = allocArray<ByteVar>(size.toInt())
    if (GetFileVersionInfoW(path, 0u, size, data) == 0) return null
    val translations = alloc<COpaquePointerVar>()
    val translationSize = alloc<UIntVar>()
    if (VerQueryValueW(data, "\\VarFileInfo\\Translation", translations.ptr, translationSize.ptr) == 0) {
        return null
    }
    if (translationSize.value < 4u) return null
    val trans = translations.value?.reinterpret<UShortVar>() ?: return null
    val lang = hex4(trans[0])
    val codePage = hex4(trans[1])
    for (field in arrayOf("ProductName", "FileDescription")) {
        val query = "\\StringFileInfo\\$lang$codePage\\$field"
        val valuePtr = alloc<COpaquePointerVar>()
        val valueSize = alloc<UIntVar>()
        if (VerQueryValueW(data, query, valuePtr.ptr, valueSize.ptr) == 0) continue
        if (valuePtr.value == null || valueSize.value <= 1u) continue
        val name = valuePtr.value!!.reinterpret<platform.windows.WCHARVar>().toKString().trim()
        if (name.isNotEmpty()) return name
    }
    null
}

private fun hex4(value: UShort): String = value.toInt().toString(16).padStart(4, '0')
