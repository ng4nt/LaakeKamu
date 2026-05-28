package fi.hh.laakekamu.ui.lisaaoire

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fi.hh.laakekamu.R
import fi.hh.laakekamu.ui.components.LkBackButton
import fi.hh.laakekamu.ui.components.LkDateField
import fi.hh.laakekamu.ui.components.LkDropdown
import fi.hh.laakekamu.ui.components.LkPrimaryButton
import fi.hh.laakekamu.ui.components.LkTextField
import fi.hh.laakekamu.ui.theme.LkDanger

// Figma (no status bar in Figma): the first label 78dp from the top, 41dp between a
// field and the next label (85dp after the side effect dropdown), 80dp from the last field to
// "Valmis!" and 41dp to "Takaisin", as in the medication wizard.
private val TOP_PADDING = 54.dp
private val FIELD_GAP = 41.dp
private val AFTER_DROPDOWN = 85.dp
private val BUTTON_GAP = 80.dp
private val FORM_WIDTH = 310.dp

/**
 * "Lisää uusi oire": the symptom, an optional link to an active medication, the start
 * date (the diary's selected day by default) and an optional end date. [onClose] leaves
 * ("Takaisin" or system back); [onSaved] runs once the entry is saved.
 */
@Composable
fun LisaaOireScreen(
    onClose: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LisaaOireViewModel = viewModel(factory = LisaaOireViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Leave once saved, also when the process was restored after the save.
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    val eiSivuoire = stringResource(R.string.ei_sivuoire)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = TOP_PADDING, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LkTextField(
            label = stringResource(R.string.oire_label),
            value = viewModel.kuvaus,
            onValueChange = viewModel::onKuvausChange,
            placeholder = stringResource(R.string.oire_placeholder),
            error = if (state.kuvausPuuttuu) stringResource(R.string.virhe_oire_puuttuu) else null
        )
        Spacer(Modifier.height(FIELD_GAP))
        LkDropdown(
            label = stringResource(R.string.sivuoire_label),
            options = state.sivuoireet,
            selected = state.sivuoire,
            optionLabel = { id ->
                state.laakkeet.find { it.laakeId == id }?.nimi ?: eiSivuoire
            },
            onSelect = viewModel::onSivuoireSelect,
            placeholder = stringResource(R.string.sivuoire_placeholder),
            error = if (state.loadError) stringResource(R.string.virhe_laakkeet_lataus) else null
        )
        Spacer(Modifier.height(AFTER_DROPDOWN))
        LkDateField(
            label = stringResource(R.string.alkamispaiva_label),
            date = state.aloituspv,
            onDateChange = { date -> if (date != null) viewModel.onAloituspvChange(date) }
        )
        Spacer(Modifier.height(FIELD_GAP))
        LkDateField(
            label = stringResource(R.string.loppumispaiva_label),
            date = state.lopetuspv,
            onDateChange = viewModel::onLopetuspvChange,
            error = if (state.lopetusEnnenAloitusta) {
                stringResource(R.string.virhe_loppu_ennen_alkua)
            } else {
                null
            },
            clearable = true
        )
        Spacer(Modifier.height(BUTTON_GAP))
        if (state.saveError) {
            Text(
                text = stringResource(R.string.virhe_oire_tallennus),
                style = MaterialTheme.typography.bodyMedium,
                color = LkDanger,
                modifier = Modifier.widthIn(max = FORM_WIDTH).fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
        }
        LkPrimaryButton(
            stringResource(R.string.valmis),
            onClick = viewModel::onDone,
            enabled = !state.saving
        )
        Spacer(Modifier.height(FIELD_GAP))
        LkBackButton(stringResource(R.string.takaisin), onClick = onClose)
    }
}
