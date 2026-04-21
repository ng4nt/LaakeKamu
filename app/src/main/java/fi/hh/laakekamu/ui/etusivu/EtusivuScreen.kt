package fi.hh.laakekamu.ui.etusivu

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
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
import fi.hh.laakekamu.ui.theme.LkNavBar
import fi.hh.laakekamu.ui.theme.LkPrimary

// Figma: group heading 18sp with a 24dp sun/moon at x 34, rows with a 45dp checkbox at
// x 31 and the text 18dp after it, 28dp between rows (the evening group), about 40dp between
// groups. A row is a touch target of at least 48dp, so the gaps around it are 1.5dp smaller
// on each side to keep the checkboxes where Figma has them.
private val START = 31.dp
private val TIME_ICON = 24.dp
private val TIME_ICON_GAP = 11.dp
private val HEADING_TO_ROW = 10.5.dp
private val ROW_GAP = 25.dp
private val GROUP_GAP = 38.5.dp
private val ROW_MIN_HEIGHT = 48.dp
private val CHECKBOX = 45.dp
private val CHECKBOX_TEXT_GAP = 18.dp
private val MISSED_BORDER = 5.dp

// Figma: the fi-rs-check icon sits 5.63dp from the frame's top left corner.
private val CHECK_INSET = 5.63.dp

/**
 * Etusivu: today's doses grouped by time with sun/moon headings and their state; tapping
 * a row ticks or unticks it. The week strip and header, the progress dots
 * and the permission banners come above and around this list. [onAdd] opens the wizard
 * from the empty state.
 */
@Composable
fun EtusivuScreen(
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EtusivuViewModel = viewModel(factory = EtusivuViewModel.Factory)
) {
    val latest by viewModel.uiState.collectAsStateWithLifecycle()
    // A list kept from before a pause over midnight is another day's: show loading until
    // today's arrives.
    val state = if (viewModel.isCurrent(latest)) latest else EtusivuUiState()
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = START, end = 16.dp, top = 24.dp, bottom = 24.dp)
    ) {
        when {
            state.loadError -> Message(stringResource(R.string.virhe_etusivu))
            state.isEmpty -> EmptyState(onAdd)
            else -> {
                if (state.toggleError) ToggleError()
                state.ryhmat.forEachIndexed { index, ryhma ->
                    if (index > 0) Spacer(Modifier.height(GROUP_GAP))
                    RyhmaOtsikko(ryhma)
                    ryhma.rivit.forEachIndexed { rowIndex, rivi ->
                        Spacer(Modifier.height(if (rowIndex == 0) HEADING_TO_ROW else ROW_GAP))
                        AnnosRow(rivi, onToggle = { viewModel.toggle(rivi) })
                    }
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

/** A failed tick: shown above the doses and read out by TalkBack when it appears. */
@Composable
private fun ToggleError() {
    Text(
        text = stringResource(R.string.virhe_merkinta),
        style = MaterialTheme.typography.bodyMedium,
        color = LkDanger,
        modifier = Modifier
            .padding(bottom = 24.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
    )
}

/**
 * One dose: its checkbox, "name **dose**" and, when missed, "ottamatta". The whole row is
 * the checkbox: tapping it calls [onToggle], and TalkBack reads the state instead of
 * "checked".
 */
@Composable
private fun AnnosRow(rivi: AnnosRivi, onToggle: () -> Unit) {
    val missed = rivi.tila == AnnoksenTila.OTTAMATTA
    val tila = when (rivi.tila) {
        AnnoksenTila.OTETTU -> stringResource(R.string.tila_otettu)
        AnnoksenTila.OTTAMATTA -> stringResource(R.string.ottamatta)
        AnnoksenTila.TULOSSA -> stringResource(R.string.tila_tulossa)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_MIN_HEIGHT)
            .toggleable(
                value = rivi.tila == AnnoksenTila.OTETTU,
                role = Role.Checkbox,
                onValueChange = { onToggle() }
            )
            .semantics { stateDescription = tila },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(rivi.tila)
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
 * The Figma checkbox frame (fi-rs-stop). Taken: the navBar fill with the fi-rs-check.
 * Missed: a 5dp danger frame with "!" (colour isn't the only signal). Decorative:
 * TalkBack reads the row's state.
 */
@Composable
private fun Checkbox(tila: AnnoksenTila) {
    when (tila) {
        AnnoksenTila.OTETTU -> Box(Modifier.size(CHECKBOX).background(LkNavBar)) {
            Image(painterResource(R.drawable.ic_checkbox), contentDescription = null)
            Image(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                modifier = Modifier.padding(start = CHECK_INSET, top = CHECK_INSET)
            )
        }
        AnnoksenTila.OTTAMATTA -> Box(
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
        AnnoksenTila.TULOSSA -> Image(
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
