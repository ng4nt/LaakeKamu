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
    onError = Color.White,
    // Roles the M3 pickers, menus and snackbar use; left out, they fall back to M3's lavender
    // defaults, which aren't Figma tokens (focused time field, dialog and menu backgrounds).
    primaryContainer = LkLight,
    onPrimaryContainer = LkOnLight,
    secondaryContainer = LkLight,
    onSecondaryContainer = LkOnLight,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = LkInput,
    outlineVariant = LkInput,
    inverseSurface = LkOnLight,
    inverseOnSurface = Color.White,
    inversePrimary = LkLight,
    // Elevated surfaces (the time dialog) would otherwise get a blue tint of primary.
    surfaceTint = Color.White
)

@Composable
fun LaakeKamuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LaakeKamuColorScheme,
        typography = Typography,
        content = content
    )
}
