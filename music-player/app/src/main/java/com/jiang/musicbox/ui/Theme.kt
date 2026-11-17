package com.jiang.musicbox.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color

/** 可选强调色（默认黛绿 = Jiang Music 原配色） */
val ACCENTS = listOf(
    "黛绿" to Color(0xFF78D6B0),
    "青蓝" to Color(0xFF6EC6E8),
    "樱粉" to Color(0xFFFF8AA8),
    "暖橙" to Color(0xFFFFB86B),
    "雅紫" to Color(0xFFB39DDB),
    "竹青" to Color(0xFF9CCC65),
)

val LocalAccent = staticCompositionLocalOf { Color(0xFF78D6B0) }

/** themeMode: "system" | "dark" | "light" */
@Composable
fun AppTheme(mode: String, accentName: String, content: @Composable () -> Unit) {
    val dark = when (mode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val accent = ACCENTS.firstOrNull { it.first == accentName }?.second
        ?: ACCENTS.first().second
    val scheme = if (dark) darkColorScheme(
        primary = accent,
        background = Color(0xFF0B1010),
        surface = Color(0xFF151D1C),
        surfaceVariant = Color(0xFF17211F),
        onBackground = Color(0xFFEDEFEA),
        onSurface = Color(0xFFEDEFEA),
        onSurfaceVariant = Color(0xFF9AA3A0),
        primaryContainer = Color(0xFF1A3029),
        onPrimaryContainer = Color(0xFFDFF2E9),
    ) else lightColorScheme(
        primary = Color(0xFF2E6E52).copy(red = accent.red * 0.5f + 0.1f, green = accent.green * 0.75f + 0.1f, blue = accent.blue * 0.5f + 0.15f),
        background = Color(0xFFF4F6F3),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFE7EDE9),
        onBackground = Color(0xFF1B201E),
        onSurface = Color(0xFF1B201E),
        onSurfaceVariant = Color(0xFF5A6663),
        primaryContainer = Color(0xFFD8EDE2),
        onPrimaryContainer = Color(0xFF143226),
    )
    CompositionLocalProvider(LocalAccent provides accent) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
