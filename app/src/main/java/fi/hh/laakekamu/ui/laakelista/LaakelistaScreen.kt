package fi.hh.laakekamu.ui.laakelista

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fi.hh.laakekamu.R
import fi.hh.laakekamu.ui.components.LkScreenTitle
import fi.hh.laakekamu.ui.components.LkSmallButton
import fi.hh.laakekamu.ui.format.formatTime
import fi.hh.laakekamu.ui.format.kestoTeksti
import fi.hh.laakekamu.ui.theme.LkMuted
import fi.hh.laakekamu.ui.theme.LkPrimary

// Figma (no status bar in Figma): title 72dp from the top, list from 166dp at x 59,
// bullets indented 30dp, an empty 20sp line between time groups and three before TARVITTAESSA,
// buttons at x 34 and 243 (150 x 68), 56dp above the bottom bar.
private val TITLE_TOP = 48.dp

// The title row's padding plus its 32sp line.
private val TITLE_BLOCK = 92.dp
private val LIST_TOP = 50.dp
private val LIST_START = 59.dp
private val BULLET_INDENT = 14.dp
private val BULLET_WIDTH = 16.dp
private val GROUP_GAP = 28.dp
private val SECTION_GAP = 84.dp
private val BUTTONS_START = 34.dp
private val BUTTONS_END = 19.dp
private val BUTTONS_BOTTOM = 56.dp

/**
 * Lääkelista: daily medications by time, as-needed ones, labels and ended ones
 * greyed out. [onAdd] opens the wizard; [onEdit] opens the edit selector.
 */
@Composable
fun LaakelistaScreen(
    onAdd: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LaakelistaViewModel = viewModel(factory = LaakelistaViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize()) {
        // The title scrolls with the list, so the list keeps its room in landscape and at large
        // font sizes; the buttons stay put.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val viewport = maxHeight
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                LkScreenTitle(
                    text = stringResource(R.string.nav_laakelista),
                    icon = R.drawable.ic_medicine,
                    modifier = Modifier.padding(top = TITLE_TOP)
                )
                when {
                    state.loadError -> CenteredText(stringResource(R.string.virhe_lista), viewport)
                    state.isEmpty -> CenteredText(
                        stringResource(R.string.laakelista_tyhja),
                        viewport
                    )
                    else -> Lista(
                        state,
                        Modifier.padding(
                            start = LIST_START,
                            end = 16.dp,
                            top = LIST_TOP,
                            bottom = 16.dp
                        )
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = BUTTONS_START, end = BUTTONS_END, bottom = BUTTONS_BOTTOM),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LkSmallButton(stringResource(R.string.lisaa), onClick = onAdd, filled = true)
            LkSmallButton(
                stringResource(R.string.muokkaa),
                onClick = onEdit,
                filled = false,
                enabled = !state.isEmpty && !state.loading && !state.loadError
            )
        }
    }
}

@Composable
private fun Lista(state: LaakelistaUiState, modifier: Modifier) {
    Column(modifier) {
        if (state.paivittain.isNotEmpty()) {
            SectionHeading(stringResource(R.string.ota_paivittain))
            state.paivittain.forEachIndexed { index, ryhma ->
                if (index > 0) Spacer(Modifier.height(GROUP_GAP))
                Text(
                    text = stringResource(R.string.klo_aika, formatTime(ryhma.aika)),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.semantics { heading() }
                )
                ryhma.rivit.forEach { Rivi(it) }
            }
        }
        if (state.tarvittaessa.isNotEmpty()) {
            if (state.paivittain.isNotEmpty()) Spacer(Modifier.height(SECTION_GAP))
            SectionHeading(stringResource(R.string.tarvittaessa_otsikko))
            state.tarvittaessa.forEach { Rivi(it) }
        }
    }
}

/** "OTA PÄIVITTÄIN" / "TARVITTAESSA": 20 Bold, underlined. */
@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.semantics { heading() }
    )
}

/** A bulleted "name **dose**" with its label under it, read as one item. */
@Composable
private fun Rivi(rivi: ListaRivi) {
    val nameColor = if (rivi.paattynyt) LkMuted else Color.Black
    val doseColor = if (rivi.paattynyt) LkMuted else LkPrimary
    Row(
        modifier = Modifier
            .padding(start = BULLET_INDENT)
            .semantics(mergeDescendants = true) {}
    ) {
        // Decorative: TalkBack reads the line without the bullet.
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyLarge,
            color = nameColor,
            modifier = Modifier.width(BULLET_WIDTH).clearAndSetSemantics {}
        )
        Column {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = nameColor)) { append(rivi.nimi) }
                    append(" ")
                    withStyle(SpanStyle(color = doseColor, fontWeight = FontWeight.Bold)) {
                        append(stringResource(R.string.annos, rivi.annosmaara, rivi.yksikko))
                    }
                },
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = kestoTeksti(rivi.kesto),
                style = MaterialTheme.typography.bodyMedium,
                color = LkMuted
            )
        }
    }
}

/** A message in the middle of the space left under the title ([viewport] minus the title). */
@Composable
private fun CenteredText(text: String, viewport: Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = viewport - TITLE_BLOCK)
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}
