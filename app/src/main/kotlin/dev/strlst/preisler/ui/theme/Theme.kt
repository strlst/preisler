package dev.strlst.preisler.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Brand colors: deep indigo ink, receipt paper, and a hanko-red accent. */
object PreislerColors {
    val Indigo = Color(0xFF1F3A5F)
    val IndigoDeep = Color(0xFF13263F)
    val Paper = Color(0xFFFDF6E3)
    val Hanko = Color(0xFFE4572E)
}

private val LightColors =
    lightColorScheme(
        primary = PreislerColors.Indigo,
        onPrimary = Color.White,
        secondary = PreislerColors.Hanko,
        onSecondary = Color.White,
        background = PreislerColors.Paper,
        onBackground = PreislerColors.IndigoDeep,
        surface = PreislerColors.Paper,
        onSurface = PreislerColors.IndigoDeep,
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF9DB8DD),
        onPrimary = PreislerColors.IndigoDeep,
        secondary = Color(0xFFFF8A65),
        onSecondary = Color.Black,
        background = Color(0xFF0E1A2B),
        onBackground = PreislerColors.Paper,
        surface = Color(0xFF0E1A2B),
        onSurface = PreislerColors.Paper,
    )

@Composable
fun PreislerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
