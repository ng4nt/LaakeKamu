package fi.hh.laakekamu.ui.etusivu

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fi.hh.laakekamu.R
import fi.hh.laakekamu.domain.AnnoksenTila
import fi.hh.laakekamu.ui.theme.LaakeKamuTheme
import fi.hh.laakekamu.ui.theme.LkDanger
import fi.hh.laakekamu.ui.theme.LkLight
import fi.hh.laakekamu.ui.theme.LkPrimary

/** Up to this many doses get a dot each; a day with more gets a plain bar. */
const val MAX_DOTS = 7

/**
 * The share of [tilat] that is taken, in whole percent. Rounded down, so 100 % means that
 * every dose is taken.
 */
fun edistysProsentti(tilat: List<AnnoksenTila>): Int =
    if (tilat.isEmpty()) 0 else tilat.count { it == AnnoksenTila.OTETTU } * 100 / tilat.size

/**
 * How the line between the dots of doses [a] and [b] is drawn: as passed
 * ([AnnoksenTila.OTETTU]) only when neither is upcoming, and as missed when either was missed.
 * A dose ticked ahead of an earlier one leaves the line between them upcoming.
 */
fun viivanTila(a: AnnoksenTila, b: AnnoksenTila): AnnoksenTila = when {
    a == AnnoksenTila.TULOSSA || b == AnnoksenTila.TULOSSA -> AnnoksenTila.TULOSSA
    a == AnnoksenTila.OTTAMATTA || b == AnnoksenTila.OTTAMATTA -> AnnoksenTila.OTTAMATTA
    else -> AnnoksenTila.OTETTU
}

// Figma: 20dp dots on a 5dp line, dots 51…374dp across the 412dp frame (centred here:
// Figma's row sits 6.5dp right of the "40%" under it), the % 7dp under the dots. A missed dot
// is a danger disc with a 10dp white centre; an upcoming one is a primary outline instead of
// Figma's #B3C7F7 fill (1.69:1), thinner than the missed ring.
private val SIDE = 44.5.dp
private val DOT = 20.dp
private val LINE = 5.dp
private val MISSED_CENTRE = 10.dp
private val OUTLINE = 3.dp
private val DOTS_TO_PERCENT = 7.dp

/**
 * The day's progress: a dot per dose in the list's order, or a bar for more than
 * [MAX_DOTS], with the taken percentage under it. TalkBack reads it as one line, e.g.
 * "Edistys 40 %: 2 otettu, 1 ottamatta, 2 tulossa". The missed state isn't colour alone
 *: its dot is a ring, and its row says "ottamatta".
 */
@Composable
fun Edistys(tilat: List<AnnoksenTila>, modifier: Modifier = Modifier) {
    val prosentti = edistysProsentti(tilat)
    val osat = listOf(
        AnnoksenTila.OTETTU to R.string.edistys_otettu,
        AnnoksenTila.OTTAMATTA to R.string.edistys_ottamatta,
        AnnoksenTila.TULOSSA to R.string.edistys_tulossa
    ).mapNotNull { (tila, teksti) ->
        val count = tilat.count { it == tila }
        if (count > 0) stringResource(teksti, count) else null
    }
    val kuvaus = stringResource(R.string.edistys_kuvaus, prosentti, osat.joinToString(", "))
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = kuvaus },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DOTS_TO_PERCENT)
    ) {
        val track = Modifier
            .fillMaxWidth()
            .padding(horizontal = SIDE)
            .height(DOT)
        if (tilat.size <= MAX_DOTS) {
            Canvas(track) { drawDots(tilat) }
        } else {
            Canvas(track) { drawBar(prosentti / 100f) }
        }
        Text(
            text = stringResource(R.string.prosentti, prosentti),
            style = MaterialTheme.typography.bodySmall,
            color = Color.Black
        )
    }
}

/** The dots spread evenly from edge to edge (one dot is centred), joined by the line. */
private fun DrawScope.drawDots(tilat: List<AnnoksenTila>) {
    val radius = DOT.toPx() / 2
    val step = if (tilat.size > 1) (size.width - 2 * radius) / (tilat.size - 1) else 0f
    fun centre(index: Int) = Offset(
        x = if (tilat.size > 1) radius + index * step else size.width / 2,
        y = size.height / 2
    )
    tilat.zipWithNext().forEachIndexed { index, (a, b) ->
        val colour = when (viivanTila(a, b)) {
            AnnoksenTila.OTETTU -> LkPrimary
            AnnoksenTila.OTTAMATTA -> LkDanger
            AnnoksenTila.TULOSSA -> LkLight
        }
        drawLine(colour, centre(index), centre(index + 1), strokeWidth = LINE.toPx())
    }
    tilat.forEachIndexed { index, tila ->
        val centre = centre(index)
        when (tila) {
            AnnoksenTila.OTETTU -> drawCircle(LkPrimary, radius, centre)
            AnnoksenTila.OTTAMATTA -> {
                drawCircle(LkDanger, radius, centre)
                drawCircle(Color.White, MISSED_CENTRE.toPx() / 2, centre)
            }
            AnnoksenTila.TULOSSA -> {
                val outline = OUTLINE.toPx()
                drawCircle(Color.White, radius, centre)
                drawCircle(LkPrimary, radius - outline / 2, centre, style = Stroke(outline))
            }
        }
    }
}

/** A rounded bar in the upcoming dots' style, filled with primary up to [fraction]. */
private fun DrawScope.drawBar(fraction: Float) {
    val outline = OUTLINE.toPx()
    val corner = CornerRadius(size.height / 2)
    val shape = Path().apply { addRoundRect(RoundRect(size.toRect(), corner)) }
    clipPath(shape) {
        drawRect(LkPrimary, size = Size(size.width * fraction, size.height))
    }
    drawRoundRect(
        color = LkPrimary,
        topLeft = Offset(outline / 2, outline / 2),
        size = Size(size.width - outline, size.height - outline),
        cornerRadius = CornerRadius(size.height / 2 - outline / 2),
        style = Stroke(outline)
    )
}

/** Figma (40 %, one missed) and a day with more doses than dots. */
@Preview(widthDp = 412, showBackground = true)
@Composable
private fun EdistysPreview() {
    LaakeKamuTheme {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Edistys(
                listOf(
                    AnnoksenTila.OTETTU,
                    AnnoksenTila.OTTAMATTA,
                    AnnoksenTila.OTETTU,
                    AnnoksenTila.TULOSSA,
                    AnnoksenTila.TULOSSA
                )
            )
            val taken = List(3) { AnnoksenTila.OTETTU }
            Edistys(taken + List(MAX_DOTS + 1 - taken.size) { AnnoksenTila.TULOSSA })
        }
    }
}
