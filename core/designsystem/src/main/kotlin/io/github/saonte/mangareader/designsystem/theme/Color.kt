package io.github.saonte.mangareader.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal val Ink = Color(0xFF101413)
internal val Paper = Color(0xFFFDFDFB)
internal val Emerald = Color(0xFF0F766E)
internal val EmeraldLight = Color(0xFF5EEAD4)
internal val Amber = Color(0xFFB45309)
internal val Slate = Color(0xFF475569)
internal val NightPaper = Color(0xFF0B0F0E)
internal val NightInk = Color(0xFFE7E9E8)

internal val LightColors = lightColorScheme(
    primary = Emerald,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Slate,
    onSecondary = Color.White,
    tertiary = Amber,
    onTertiary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF1F5F3),
    onSurfaceVariant = Color(0xFF3F4946),
    outline = Color(0xFF6F7975),
    error = Color(0xFFB3261E),
)

internal val DarkColors = darkColorScheme(
    primary = EmeraldLight,
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF00504A),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFFB6C2BE),
    onSecondary = Color(0xFF1F2A27),
    tertiary = Color(0xFFFCD34D),
    onTertiary = Color(0xFF422C00),
    background = NightPaper,
    onBackground = NightInk,
    surface = NightPaper,
    onSurface = NightInk,
    surfaceVariant = Color(0xFF232B29),
    onSurfaceVariant = Color(0xFFBFC9C5),
    outline = Color(0xFF89938F),
    error = Color(0xFFF2B8B5),
)
