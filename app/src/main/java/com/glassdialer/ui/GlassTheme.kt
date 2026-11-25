package com.glassdialer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class GlassColors(
    val bg: Color, val text: Color, val text2: Color, val line: Color,
    val glass: Color, val key: Color, val keyPressed: Color, val border: Color,
    val chipOn: Color, val avatarBg: Color, val toggleOn: Color, val toggleOnText: Color,
    val blobAlpha: Float, val isDark: Boolean,
)

val CallGreen = Color(0xFF25C26A)
val EndRed = Color(0xFFE5484D)
val TagOrange = Color(0xFFE08A2E)

val LightGlass = GlassColors(
    bg = Color(0xFFEEF0F5), text = Color(0xFF111114), text2 = Color(0xFF5D5F67),
    line = Color(0x12000000), glass = Color(0x6BFFFFFF), key = Color(0x8CFFFFFF),
    keyPressed = Color(0xD9FFFFFF), border = Color(0xD9FFFFFF), chipOn = Color(0xE6FFFFFF),
    avatarBg = Color(0x14000000), toggleOn = Color(0xFF111114), toggleOnText = Color.White,
    blobAlpha = 0.75f, isDark = false,
)

val DarkGlass = GlassColors(
    bg = Color(0xFF07070A), text = Color(0xFFF4F4F6), text2 = Color(0xFFA2A3AB),
    line = Color(0x12FFFFFF), glass = Color(0x6B1C1C22), key = Color(0x14FFFFFF),
    keyPressed = Color(0x26FFFFFF), border = Color(0x29FFFFFF), chipOn = Color(0x2EFFFFFF),
    avatarBg = Color(0x1AFFFFFF), toggleOn = Color(0xFFF4F4F6), toggleOnText = Color(0xFF111114),
    blobAlpha = 0.55f, isDark = true,
)

val LocalGlass = staticCompositionLocalOf { LightGlass }

/** Follows the phone's system theme automatically. */
@Composable
fun GlassTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) DarkGlass else LightGlass
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        CompositionLocalProvider(
            LocalGlass provides colors,
            LocalContentColor provides colors.text,
            content = content,
        )
    }
}
