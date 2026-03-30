package fi.hh.laakekamu.ui.lisaalaake

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fi.hh.laakekamu.R
import fi.hh.laakekamu.domain.NAME_MAX_LENGTH
import fi.hh.laakekamu.domain.NameError
import fi.hh.laakekamu.ui.components.LkBackButton
import fi.hh.laakekamu.ui.components.LkDropdown
import fi.hh.laakekamu.ui.components.LkPrimaryButton
import fi.hh.laakekamu.ui.components.LkTextField

// Figma: first label 78dp from the top of the frame (no status bar in Figma), 41dp
// between a field and the next label, 80dp from the last field to the main button.
private val TOP_PADDING = 54.dp
private val FIELD_GAP = 41.dp
private val BUTTON_GAP = 80.dp

/** The add medication wizard. [onClose] leaves it ("Takaisin" on step 1). */
@Composable
fun LisaaLaakeScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LisaaLaakeViewModel = viewModel(factory = LisaaLaakeViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // System back on step 2 returns to step 1 with the values kept.
    BackHandler(enabled = state.step == 2) { viewModel.onBackToStep1() }

    val content = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(start = 16.dp, end = 16.dp, top = TOP_PADDING, bottom = 32.dp)
    if (state.step == 1) {
        Step1(state, viewModel, onClose, modifier.then(content))
    } else {
        // Step 2 (times, frequency, dates) is built in the next commit.
        Column(modifier.then(content), horizontalAlignment = Alignment.CenterHorizontally) {
            LkBackButton(stringResource(R.string.takaisin), onClick = viewModel::onBackToStep1)
        }
    }
}

@Composable
private fun Step1(
    state: LisaaLaakeUiState,
    viewModel: LisaaLaakeViewModel,
    onClose: () -> Unit,
    modifier: Modifier
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        LkTextField(
            label = stringResource(R.string.laake_label),
            value = state.nimi,
            onValueChange = viewModel::onNimiChange,
            placeholder = stringResource(R.string.laake_placeholder),
            error = nameErrorText(state.errors.nimi)
        )
        Spacer(Modifier.height(FIELD_GAP))
        LkDropdown(
            label = stringResource(R.string.tyyppi_label),
            options = state.tyypit,
            selected = state.tyypit.find { it.tyyppiId == state.tyyppiId },
            optionLabel = { it.tyyppi },
            onSelect = { viewModel.onTyyppiSelect(it.tyyppiId) },
            placeholder = stringResource(R.string.tyyppi_placeholder),
            error = if (state.errors.tyyppi) stringResource(R.string.virhe_tyyppi_puuttuu) else null
        )
        Spacer(Modifier.height(FIELD_GAP))
        LkTextField(
            label = stringResource(R.string.annosmaara_label),
            value = state.annosmaara,
            onValueChange = viewModel::onAnnosmaaraChange,
            placeholder = stringResource(R.string.annosmaara_placeholder),
            error = if (state.errors.annosmaara) {
                stringResource(R.string.virhe_annosmaara_puuttuu)
            } else {
                null
            }
        )
        Spacer(Modifier.height(FIELD_GAP))
        LkDropdown(
            label = stringResource(R.string.yksikko_label),
            options = state.yksikot,
            selected = state.yksikot.find { it.yksikko == state.yksikko },
            optionLabel = { it.yksikko },
            onSelect = { viewModel.onYksikkoSelect(it.yksikko) },
            placeholder = stringResource(R.string.yksikko_placeholder),
            error = if (state.errors.yksikko) {
                stringResource(
                    R.string.virhe_yksikko_puuttuu
                )
            } else {
                null
            }
        )
        Spacer(Modifier.height(BUTTON_GAP))
        LkPrimaryButton(stringResource(R.string.seuraava_vaihe), onClick = viewModel::onNext)
        Spacer(Modifier.height(FIELD_GAP))
        LkBackButton(stringResource(R.string.takaisin), onClick = onClose)
    }
}

@Composable
private fun nameErrorText(error: NameError?): String? = when (error) {
    null -> null
    NameError.Blank -> stringResource(R.string.virhe_nimi_puuttuu)
    NameError.TooLong -> stringResource(R.string.virhe_nimi_liian_pitka, NAME_MAX_LENGTH)
    is NameError.Duplicate -> stringResource(R.string.virhe_nimi_jo_lisatty, error.name)
}
