package dev.lapse.domain

fun applicationDisplayName(
    executableName: String,
    reportedDisplayName: String,
    windowTitle: String = "",
): String {
    val reported = reportedDisplayName.trim()
    val title = windowTitle.trim()
    val genericMetadata = isGenericApplicationName(reported)
    if (genericMetadata && title.isNotEmpty() && !isGenericApplicationName(title)) {
        return title
    }
    if (reported.isEmpty() || genericMetadata || isExecutableName(reported) ||
        reported.equals(executableName, ignoreCase = true)
    ) {
        return friendlyExecutableName(executableName)
    }
    return reported
}

private fun isGenericApplicationName(value: String): Boolean {
    val normalized = value.lowercase()
    return normalized.isEmpty() ||
        normalized == "application" ||
        normalized == "game" ||
        normalized.contains("unreal engine") ||
        normalized == "unity" ||
        normalized.contains("unity player") ||
        normalized == "electron" ||
        normalized == "chromium" ||
        (normalized.contains("microsoft") &&
            (normalized.contains("windows") || normalized.contains("betriebssystem"))) ||
        normalized.contains("java platform") ||
        normalized.contains("openjdk platform")
}

private fun isExecutableName(value: String): Boolean =
    value.lowercase().trim().endsWith(".exe")

private fun friendlyExecutableName(executableName: String): String {
    var name = executableName.trim().replace(Regex("\\.exe$", RegexOption.IGNORE_CASE), "")
    name = name.replace(
        Regex("(?:client)?[-_](?:win32|win64)(?:[-_](?:shipping|test|development))?.*$", RegexOption.IGNORE_CASE),
        "",
    )
    name = name.replace(Regex("[-_](?:shipping|development|release)$", RegexOption.IGNORE_CASE), "")
    name = name.replace(Regex("[_-]+"), " ")
        .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
        .trim()
    return name.ifEmpty { "Unknown application" }
}
