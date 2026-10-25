package com.yasin.cast

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LapisColorScheme = darkColorScheme(
    primary = Color(0xFF69A5FF),
    onPrimary = Color(0xFF07152B),
    primaryContainer = Color(0xFF1A4278),
    onPrimaryContainer = Color(0xFFE0EDFF),
    secondary = Color(0xFF75DDF5),
    onSecondary = Color(0xFF06202B),
    background = Color(0xFF071326),
    onBackground = Color(0xFFEAF3FF),
    surface = Color(0xFF0D1E38),
    onSurface = Color(0xFFEAF3FF),
    surfaceVariant = Color(0xFF142B4D),
    onSurfaceVariant = Color(0xFFAFC4E0),
    outline = Color(0xFF34577F),
    error = Color(0xFFFF8B9B)
)

@Composable
fun YasinCastTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LapisColorScheme, content = content)
}

/** Translucent-looking navy panel with a subtle lapis outline. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xE6112948)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, Color(0x665C9EFF)),
        content = content
    )
}
