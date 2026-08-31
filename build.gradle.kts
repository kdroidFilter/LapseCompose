plugins {
    alias(libs.plugins.kotlin.multiplatform).apply(false)
    alias(libs.plugins.compose.compiler).apply(false)
    alias(libs.plugins.compose.multiplatform).apply(false)
    alias(libs.plugins.kotlin.jvm).apply(false)
    alias(libs.plugins.metro).apply(false)
    alias(libs.plugins.sqlDelight).apply(false)
    alias(libs.plugins.nna).apply(false)
    alias(libs.plugins.nucleus).apply(false)
    alias(libs.plugins.structured.coroutines).apply(false)
    alias(libs.plugins.stability.analyzer).apply(false)
}
