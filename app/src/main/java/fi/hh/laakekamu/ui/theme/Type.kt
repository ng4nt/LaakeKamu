package fi.hh.laakekamu.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import fi.hh.laakekamu.R

/** Open Sans 400/700, bundled in res/font (OFL, no network download). */
val OpenSans = FontFamily(
    Font(R.font.open_sans_regular, FontWeight.Normal),
    Font(R.font.open_sans_bold, FontWeight.Bold)
)

private fun openSans(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(
        fontFamily = OpenSans,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = 0.sp
    )

// Sizes from Figma. Figma gives line heights only for
// the week strip (22) and the calendar (40); the others are about 1.3x the font size.
val Typography = Typography(
    // Screen titles (and the calendar year).
    headlineLarge = openSans(size = 32, lineHeight = 40, weight = FontWeight.Bold),
    // Calendar month name.
    headlineMedium = openSans(size = 30, lineHeight = 38),
    // Calendar weekday headers and dates.
    headlineSmall = openSans(size = 25, lineHeight = 40),
    // Dialog titles.
    titleLarge = openSans(size = 20, lineHeight = 28, weight = FontWeight.Bold),
    // Section headings ("OTA PÄIVITTÄIN") and emphasised doses.
    titleMedium = openSans(size = 20, lineHeight = 28, weight = FontWeight.Bold),
    // Labels, placeholders, list rows, week strip.
    bodyLarge = openSans(size = 20, lineHeight = 28),
    // Home rows and time labels, error texts, banners, dialog bodies, snackbars.
    bodyMedium = openSans(size = 18, lineHeight = 26),
    // Home header and the progress percentage (the only 16sp texts).
    bodySmall = openSans(size = 16, lineHeight = 22),
    // Buttons.
    labelLarge = openSans(size = 20, lineHeight = 28, weight = FontWeight.Bold)
)
