package dev.lapse.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import lapse.shared.generated.resources.Res
import lapse.shared.generated.resources.app_icon
import org.jetbrains.compose.resources.painterResource

@Composable
fun appIconPainter(): Painter = painterResource(Res.drawable.app_icon)
