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
    primary = Color(0xFF35C7EB),
    onPrimary = Color(0xFF07152B),
    primaryContainer = Color(0xFF203C80),
    onPrimaryContainer = Color(0xFFE0EDFF),
    secondary = Color(0xFF39C9E8),
    onSecondary = Color(0xFF06202B),
    background = Color(0xFF07091F),
    onBackground = Color(0xFFEAF3FF),
    surface = Color(0xFF111735),
    onSurface = Color(0xFFEAF3FF),
    surfaceVariant = Color(0xFF171F45),
    onSurfaceVariant = Color(0xFFAFC4E0),
    outline = Color(0xFF303B68),
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
        colors = CardDefaults.cardColors(containerColor = Color(0xE6161F40)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, Color(0x553F5A9B)),
        content = content
    )
}
