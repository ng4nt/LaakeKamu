package fi.hh.laakekamu.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Always the light Figma scheme: no dark theme and no dynamic colour, so the app looks the
// same on every phone and the measured contrasts stay valid.
private val LaakeKamuColorScheme = lightColorScheme(
    primary = LkPrimary,
    onPrimary = Color.White,
    secondary = LkNavBar,
    onSecondary = Color.White,
    tertiary = LkLight,
    onTertiary = LkOnLight,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = LkInput,
    onSurfaceVariant = Color.Black,
    outline = LkPrimary,
    error = LkDanger,
    onError = Color.White
)

@Composable
fun LaakeKamuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LaakeKamuColorScheme,
        typography = Typography,
        content = content
    )
}
