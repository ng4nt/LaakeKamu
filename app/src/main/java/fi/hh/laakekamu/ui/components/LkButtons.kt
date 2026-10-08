package fi.hh.laakekamu.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fi.hh.laakekamu.ui.theme.LaakeKamuTheme
import fi.hh.laakekamu.ui.theme.LkDanger
import fi.hh.laakekamu.ui.theme.LkLight
import fi.hh.laakekamu.ui.theme.LkOnLight
import fi.hh.laakekamu.ui.theme.LkPrimary

// Figma: 310 x 60, radius 30. Small buttons: 150 x 68.
private val WIDE_WIDTH = 310.dp
private val WIDE_HEIGHT = 60.dp
private val SMALL_WIDTH = 150.dp
private val SMALL_HEIGHT = 68.dp
private val OUTLINE_WIDTH = 5.dp
private val PILL = RoundedCornerShape(30.dp)

/** Main action of a form ("Seuraava vaihe", "Valmis!", "Muokkaa"). */
@Composable
fun LkPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    WideButton(text, onClick, modifier, enabled, LkPrimary, Color.White)
}

/** Destructive action ("Poista lääke", "Poista oire"); always followed by a confirm dialog. */
@Composable
fun LkDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    WideButton(text, onClick, modifier, enabled, LkDanger, Color.White)
}

/** "Takaisin": the light button under the main action. */
@Composable
fun LkBackButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    WideButton(text, onClick, modifier, enabled = true, LkLight, LkOnLight)
}

/** The small pill buttons under the lists: "Lisää" ([filled]) and "Muokkaa" (outlined). */
@Composable
fun LkSmallButton(
    text: String,
    onClick: () -> Unit,
    filled: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val size = modifier.widthIn(min = SMALL_WIDTH).heightIn(min = SMALL_HEIGHT)
    if (filled) {
        Button(
            onClick = onClick,
            modifier = size,
            enabled = enabled,
            shape = PILL,
            colors = ButtonDefaults.buttonColors(
                containerColor = LkPrimary,
                contentColor = Color.White
            )
        ) { ButtonText(text) }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = size,
            enabled = enabled,
            shape = PILL,
            border = BorderStroke(OUTLINE_WIDTH, if (enabled) LkPrimary else LkLight),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.White,
                contentColor = LkPrimary
            )
        ) { ButtonText(text) }
    }
}

/** 310dp wide at most (narrower on small screens) and at least 60dp high (taller at big font). */
@Composable
private fun WideButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    container: Color,
    content: Color
) {
    Button(
        onClick = onClick,
        // Cap first, then fill: the other order can't shrink a filled width.
        modifier = modifier.widthIn(max = WIDE_WIDTH).fillMaxWidth().heightIn(min = WIDE_HEIGHT),
        enabled = enabled,
        shape = PILL,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content)
    ) { ButtonText(text) }
}

@Composable
private fun ButtonText(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
}

@Preview(widthDp = 412, showBackground = true)
@Composable
private fun LkButtonsPreview() {
    LaakeKamuTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LkPrimaryButton("Seuraava vaihe", onClick = {})
            LkBackButton("Takaisin", onClick = {})
            LkDangerButton("Poista lääke", onClick = {})
            LkPrimaryButton("Muokkaa", onClick = {}, enabled = false)
            Row(horizontalArrangement = Arrangement.spacedBy(59.dp)) {
                LkSmallButton("Lisää", onClick = {}, filled = true)
                LkSmallButton("Muokkaa", onClick = {}, filled = false)
            }
        }
    }
}
