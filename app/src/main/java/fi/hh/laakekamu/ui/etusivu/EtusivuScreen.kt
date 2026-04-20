package fi.hh.laakekamu.ui.etusivu

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fi.hh.laakekamu.R
import fi.hh.laakekamu.domain.AnnoksenTila
import fi.hh.laakekamu.ui.components.LkPrimaryButton
import fi.hh.laakekamu.ui.format.formatTime
import fi.hh.laakekamu.ui.theme.LkDanger
import fi.hh.laakekamu.ui.theme.LkPrimary

// Figma: group heading 18sp with a 24dp sun/moon at x 34, rows with a 45dp checkbox at
// x 31 and the text 18dp after it, 28dp between rows (the evening group), about 40dp between
// groups.
private val START = 31.dp
private val TIME_ICON = 24.dp
private val TIME_ICON_GAP = 11.dp
private val HEADING_TO_ROW = 12.dp
private val ROW_GAP = 28.dp
private val GROUP_GAP = 40.dp
private val CHECKBOX = 45.dp
private val CHECKBOX_TEXT_GAP = 18.dp
private val MISSED_BORDER = 5.dp

/**
 * Etusivu: today's doses grouped by time with sun/moon headings and their state. The week
 * strip and header, the progress dots, ticking and the permission banners
 * come above and around this list. [onAdd] opens the wizard from the empty state.
 */
@Composable
fun EtusivuScreen(
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EtusivuViewModel = viewModel(factory = EtusivuViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = START, end = 16.dp, top = 24.dp, bottom = 24.dp)
    ) {
        when {
            state.loadError -> Message(stringResource(R.string.virhe_etusivu))
            state.isEmpty -> EmptyState(onAdd)
            else -> state.ryhmat.forEachIndexed { index, ryhma ->
                if (index > 0) Spacer(Modifier.height(GROUP_GAP))
                RyhmaOtsikko(ryhma)
                ryhma.rivit.forEachIndexed { rowIndex, rivi ->
                    Spacer(Modifier.height(if (rowIndex == 0) HEADING_TO_ROW else ROW_GAP))
                    AnnosRow(rivi)
                }
            }
        }
    }
}

/** "klo 8.00" with the sun (before 11.00) or the moon (from 17.00). */
@Composable
private fun RyhmaOtsikko(ryhma: AnnosRyhma) {
    Row(
        modifier = Modifier.padding(start = 3.dp).semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = when (ryhma.vuorokausi) {
            Vuorokausi.AAMU -> R.drawable.ic_sun
            Vuorokausi.ILTA -> R.drawable.ic_moon
            Vuorokausi.PAIVA -> null
        }
        if (icon != null) {
            // Decorative: the time says it.
            Image(painterResource(icon), contentDescription = null, Modifier.size(TIME_ICON))
            Spacer(Modifier.width(TIME_ICON_GAP))
        }
        Text(
            text = stringResource(R.string.klo_aika, formatTime(ryhma.aika)),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** One dose: its checkbox, "name **dose**" and, when missed, "ottamatta". */
@Composable
private fun AnnosRow(rivi: AnnosRivi) {
    val missed = rivi.tila == AnnoksenTila.OTTAMATTA
    val tila = when (rivi.tila) {
        AnnoksenTila.OTETTU -> stringResource(R.string.tila_otettu)
        AnnoksenTila.OTTAMATTA -> stringResource(R.string.ottamatta)
        AnnoksenTila.TULOSSA -> stringResource(R.string.tila_tulossa)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { stateDescription = tila },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(missed)
        Spacer(Modifier.width(CHECKBOX_TEXT_GAP))
        Column {
            Text(
                text = buildAnnotatedString {
                    append(rivi.nimi)
                    append(" ")
                    withStyle(SpanStyle(color = LkPrimary, fontWeight = FontWeight.Bold)) {
                        append(stringResource(R.string.annos, rivi.annosmaara, rivi.yksikko))
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black
            )
            if (missed) {
                // The row's state already says "ottamatta" to TalkBack.
                Text(
                    text = stringResource(R.string.ottamatta),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LkDanger,
                    modifier = Modifier.clearAndSetSemantics {}
                )
            }
        }
    }
}

/**
 * The Figma checkbox frame (fi-rs-stop). Missed: a 5dp danger frame with "!" (colour isn't the
 * only signal). The taken state with its check comes with ticking.
 */
@Composable
private fun Checkbox(missed: Boolean) {
    if (missed) {
        Box(
            // Decorative: TalkBack reads the row's state instead of the "!".
            modifier = Modifier
                .size(CHECKBOX)
                .border(MISSED_BORDER, LkDanger)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "!",
                style = MaterialTheme.typography.titleLarge,
                color = LkDanger
            )
        }
    } else {
        Image(
            painter = painterResource(R.drawable.ic_checkbox),
            contentDescription = null,
            modifier = Modifier.size(CHECKBOX)
        )
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 120.dp, end = START - 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Message(stringResource(R.string.ei_laakkeita_paivalle))
        Spacer(Modifier.height(32.dp))
        LkPrimaryButton(stringResource(R.string.lisaa_laake), onClick = onAdd)
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}
