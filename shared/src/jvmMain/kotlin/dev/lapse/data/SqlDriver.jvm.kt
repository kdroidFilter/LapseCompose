package dev.lapse.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.lapse.db.AppDatabase
import java.io.File
import java.util.Properties

internal actual fun createSqlDriver(): SqlDriver {
    val dir = File(appDataDir(), "Lapse")
    dir.mkdirs()
    val url = "jdbc:sqlite:${File(dir, "lapse.db").absolutePath}"
    return JdbcSqliteDriver(url, Properties(), AppDatabase.Schema)
}

private fun appDataDir(): String {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    val home = System.getProperty("user.home")
    return when {
        os.contains("win") -> System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() } ?: home
        os.contains("mac") -> "$home/Library/Application Support"
        else -> "$home/.local/share"
    }
}
