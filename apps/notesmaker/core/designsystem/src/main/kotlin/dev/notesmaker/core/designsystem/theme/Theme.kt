package dev.notesmaker.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F5DA8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E2FF),
    onPrimaryContainer = Color(0xFF001A40),
    secondary = Color(0xFF56607A),
    tertiary = Color(0xFF8A4F7D),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFACC7FF),
    onPrimary = Color(0xFF002E68),
    primaryContainer = Color(0xFF0F448F),
    onPrimaryContainer = Color(0xFFD7E2FF),
    secondary = Color(0xFFBEC6E3),
    tertiary = Color(0xFFFCB0E8),
)

/**
 * NotesMaker's Material 3 theme. Uses the wallpaper-based dynamic colours on Android 12+ unless
 * [dynamicColor] is false (for screenshots and tests), and the brand palette below that.
 */
@Composable
fun NotesMakerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = colorScheme(darkTheme, dynamicColor), content = content)
}

@Composable
private fun colorScheme(darkTheme: Boolean, dynamicColor: Boolean): ColorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    darkTheme -> DarkColors
    else -> LightColors
}
