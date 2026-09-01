@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.lapse.nativesupport

import cnames.structs.DBusConnection
import cnames.structs.DBusMessage
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import lapse.dbus.DBUS_TYPE_ARRAY
import lapse.dbus.DBUS_TYPE_BOOLEAN
import lapse.dbus.DBUS_TYPE_OBJECT_PATH
import lapse.dbus.DBUS_TYPE_STRING
import lapse.dbus.DBUS_TYPE_STRUCT
import lapse.dbus.DBUS_TYPE_VARIANT
import lapse.dbus.DBusBusType
import lapse.dbus.DBusError
import lapse.dbus.DBusMessageIter
import lapse.dbus.dbus_bus_get
import lapse.dbus.dbus_connection_send_with_reply_and_block
import lapse.dbus.dbus_error_free
import lapse.dbus.dbus_error_init
import lapse.dbus.dbus_error_is_set
import lapse.dbus.dbus_message_iter_append_basic
import lapse.dbus.dbus_message_iter_get_arg_type
import lapse.dbus.dbus_message_iter_get_basic
import lapse.dbus.dbus_message_iter_init
import lapse.dbus.dbus_message_iter_init_append
import lapse.dbus.dbus_message_iter_next
import lapse.dbus.dbus_message_iter_recurse
import lapse.dbus.dbus_message_new_method_call
import lapse.dbus.dbus_message_unref
import lapse.dbus.dbus_threads_init_default
import platform.posix.getenv
import platform.posix.getuid

private const val CALL_TIMEOUT_MS = 200
private const val LOGIN1 = "org.freedesktop.login1"
private const val LOGIN1_PATH = "/org/freedesktop/login1"
private const val LOGIN1_MANAGER = "org.freedesktop.login1.Manager"
private const val DBUS_PROPERTIES = "org.freedesktop.DBus.Properties"
private const val LOGIN1_SESSION = "org.freedesktop.login1.Session"

private data class ScreensaverService(
    val dest: String,
    val path: String,
    val iface: String,
)

private val screensaverServices = listOf(
    ScreensaverService("org.gnome.ScreenSaver", "/org/gnome/ScreenSaver", "org.gnome.ScreenSaver"),
    ScreensaverService("org.cinnamon.ScreenSaver", "/org/cinnamon/ScreenSaver", "org.cinnamon.ScreenSaver"),
    ScreensaverService("org.mate.ScreenSaver", "/org/mate/ScreenSaver", "org.mate.ScreenSaver"),
    ScreensaverService("org.xfce.ScreenSaver", "/org/xfce/ScreenSaver", "org.xfce.ScreenSaver"),
    ScreensaverService("org.freedesktop.ScreenSaver", "/org/freedesktop/ScreenSaver", "org.freedesktop.ScreenSaver"),
)

internal object LinuxDbus {
    private val session: CPointer<DBusConnection>?
    private val system: CPointer<DBusConnection>?
    private var screensaver: ScreensaverService? = null
    private var screensaverProbed = false

    init {
        dbus_threads_init_default()
        session = bus(DBusBusType.DBUS_BUS_SESSION)
        system = bus(DBusBusType.DBUS_BUS_SYSTEM)
    }

    fun isLocked(): Boolean {
        screensaverActive()?.let { return it }
        if (logindLocked()) return true
        return false
    }

    private fun screensaverActive(): Boolean? {
        val cached = screensaver
        if (cached != null) return callBoolean(session, cached.dest, cached.path, cached.iface, "GetActive")
        if (screensaverProbed) return null
        screensaverProbed = true
        for (service in screensaverServices) {
            val active = callBoolean(session, service.dest, service.path, service.iface, "GetActive") ?: continue
            screensaver = service
            return active
        }
        return null
    }

    private fun logindLocked(): Boolean {
        val path = sessionPath() ?: return false
        return callPropertyBoolean(system, LOGIN1, path, LOGIN1_SESSION, "LockedHint") == true
    }

    private fun sessionPath(): String? {
        getenv("XDG_SESSION_ID")?.toKString()?.takeIf { it.isNotEmpty() }?.let { id ->
            callObjectPath(system, LOGIN1, LOGIN1_PATH, LOGIN1_MANAGER, "GetSession", listOf(id))?.let { return it }
        }
        return seatedSessionPath()
    }

    private fun seatedSessionPath(): String? {
        val reply = call(system, LOGIN1, LOGIN1_PATH, LOGIN1_MANAGER, "ListSessions") ?: return null
        try {
            return memScoped {
                val iter = alloc<DBusMessageIter>()
                if (!dbusTrue(dbus_message_iter_init(reply, iter.ptr))) return@memScoped null
                if (argType(iter.ptr) != typeInt(DBUS_TYPE_ARRAY)) return@memScoped null
                val array = alloc<DBusMessageIter>()
                dbus_message_iter_recurse(iter.ptr, array.ptr)
                val uid = getuid()
                var fallback: String? = null
                while (argType(array.ptr) == typeInt(DBUS_TYPE_STRUCT)) {
                    val row = alloc<DBusMessageIter>()
                    dbus_message_iter_recurse(array.ptr, row.ptr)
                    readString(row.ptr)
                    val rowUid = readUInt(row.ptr)
                    readString(row.ptr)
                    val seat = readString(row.ptr)
                    val path = readString(row.ptr)
                    if (path != null && rowUid == uid) {
                        if (!seat.isNullOrEmpty()) return@memScoped path
                        if (fallback == null) fallback = path
                    }
                    dbus_message_iter_next(array.ptr)
                }
                fallback
            }
        } finally {
            dbus_message_unref(reply)
        }
    }

    private fun bus(type: DBusBusType): CPointer<DBusConnection>? = memScoped {
        val err = alloc<DBusError>()
        dbus_error_init(err.ptr)
        val connection = dbus_bus_get(type, err.ptr)
        if (dbus_error_is_set(err.ptr) != 0.convert<UInt>()) dbus_error_free(err.ptr)
        connection
    }

    private fun callBoolean(
        connection: CPointer<DBusConnection>?,
        dest: String,
        path: String,
        iface: String,
        method: String,
    ): Boolean? {
        val reply = call(connection, dest, path, iface, method) ?: return null
        try {
            return readBoolean(reply)
        } finally {
            dbus_message_unref(reply)
        }
    }

    private fun callObjectPath(
        connection: CPointer<DBusConnection>?,
        dest: String,
        path: String,
        iface: String,
        method: String,
        strings: List<String>,
    ): String? {
        val reply = call(connection, dest, path, iface, method, strings) ?: return null
        try {
            return readObjectPath(reply)
        } finally {
            dbus_message_unref(reply)
        }
    }

    private fun callPropertyBoolean(
        connection: CPointer<DBusConnection>?,
        dest: String,
        path: String,
        iface: String,
        name: String,
    ): Boolean? {
        val reply = call(connection, dest, path, DBUS_PROPERTIES, "Get", listOf(iface, name)) ?: return null
        try {
            return readVariantBoolean(reply)
        } finally {
            dbus_message_unref(reply)
        }
    }

    private fun call(
        connection: CPointer<DBusConnection>?,
        dest: String,
        path: String,
        iface: String,
        method: String,
        strings: List<String> = emptyList(),
    ): CPointer<DBusMessage>? {
        if (connection == null) return null
        val msg = dbus_message_new_method_call(dest, path, iface, method) ?: return null
        return memScoped {
            if (strings.isNotEmpty()) {
                val iter = alloc<DBusMessageIter>()
                dbus_message_iter_init_append(msg, iter.ptr)
                for (value in strings) {
                    val holder = alloc<CPointerVar<ByteVar>>()
                    holder.value = value.cstr.ptr
                    if (!dbusTrue(dbus_message_iter_append_basic(iter.ptr, DBUS_TYPE_STRING, holder.ptr))) {
                        dbus_message_unref(msg)
                        return@memScoped null
                    }
                }
            }
            val err = alloc<DBusError>()
            dbus_error_init(err.ptr)
            val reply = dbus_connection_send_with_reply_and_block(connection, msg, CALL_TIMEOUT_MS, err.ptr)
            dbus_message_unref(msg)
            if (reply == null && dbus_error_is_set(err.ptr) != 0.convert<UInt>()) {
                dbus_error_free(err.ptr)
            }
            reply
        }
    }

    private fun readBoolean(message: CPointer<DBusMessage>): Boolean? = memScoped {
        val iter = alloc<DBusMessageIter>()
        if (!dbusTrue(dbus_message_iter_init(message, iter.ptr))) return null
        if (argType(iter.ptr) != typeInt(DBUS_TYPE_BOOLEAN)) return null
        val value = alloc<UIntVar>()
        dbus_message_iter_get_basic(iter.ptr, value.ptr)
        value.value != 0u
    }

    private fun readVariantBoolean(message: CPointer<DBusMessage>): Boolean? = memScoped {
        val iter = alloc<DBusMessageIter>()
        if (!dbusTrue(dbus_message_iter_init(message, iter.ptr))) return null
        if (argType(iter.ptr) != typeInt(DBUS_TYPE_VARIANT)) return null
        val inner = alloc<DBusMessageIter>()
        dbus_message_iter_recurse(iter.ptr, inner.ptr)
        if (argType(inner.ptr) != typeInt(DBUS_TYPE_BOOLEAN)) return null
        val value = alloc<UIntVar>()
        dbus_message_iter_get_basic(inner.ptr, value.ptr)
        value.value != 0u
    }

    private fun readObjectPath(message: CPointer<DBusMessage>): String? = memScoped {
        val iter = alloc<DBusMessageIter>()
        if (!dbusTrue(dbus_message_iter_init(message, iter.ptr))) return null
        val type = argType(iter.ptr)
        if (type != typeInt(DBUS_TYPE_OBJECT_PATH) && type != typeInt(DBUS_TYPE_STRING)) return null
        val value = alloc<CPointerVar<ByteVar>>()
        dbus_message_iter_get_basic(iter.ptr, value.ptr)
        value.value?.toKString()
    }

    private fun readString(iter: CPointer<DBusMessageIter>): String? {
        val type = argType(iter)
        if (type != typeInt(DBUS_TYPE_STRING) && type != typeInt(DBUS_TYPE_OBJECT_PATH)) {
            dbus_message_iter_next(iter)
            return null
        }
        return memScoped {
            val value = alloc<CPointerVar<ByteVar>>()
            dbus_message_iter_get_basic(iter, value.ptr)
            dbus_message_iter_next(iter)
            value.value?.toKString()
        }
    }

    private fun readUInt(iter: CPointer<DBusMessageIter>): UInt {
        val value = memScoped {
            val out = alloc<UIntVar>()
            dbus_message_iter_get_basic(iter, out.ptr)
            out.value
        }
        dbus_message_iter_next(iter)
        return value
    }
}

private fun dbusTrue(value: UInt): Boolean = value != 0u

private fun dbusTrue(value: Int): Boolean = value != 0

private fun argType(iter: CPointer<DBusMessageIter>): Int =
    dbus_message_iter_get_arg_type(iter)

private fun typeInt(value: Int): Int = value

private fun typeInt(value: UInt): Int = value.toInt()

