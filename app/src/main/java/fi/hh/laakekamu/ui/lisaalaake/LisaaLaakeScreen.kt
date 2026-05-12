package fi.hh.laakekamu.ui.lisaalaake

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fi.hh.laakekamu.R
import fi.hh.laakekamu.domain.KuinkaUsein
import fi.hh.laakekamu.domain.NAME_MAX_LENGTH
import fi.hh.laakekamu.domain.NameError
import fi.hh.laakekamu.reminders.Ilmoitukset
import fi.hh.laakekamu.ui.components.LkBackButton
import fi.hh.laakekamu.ui.components.LkDateField
import fi.hh.laakekamu.ui.components.LkDropdown
import fi.hh.laakekamu.ui.components.LkPrimaryButton
import fi.hh.laakekamu.ui.components.LkTextField
import fi.hh.laakekamu.ui.components.LkTimeField
import fi.hh.laakekamu.ui.format.formatTime
import fi.hh.laakekamu.ui.format.kestoTeksti
import fi.hh.laakekamu.ui.theme.LkDanger
import fi.hh.laakekamu.ui.theme.LkOnLight
import fi.hh.laakekamu.ui.theme.LkPrimary

// Figma: first label 78dp from the top of the frame (no status bar in Figma), 41dp
// between a field and the next label, 80dp from the last field to the main button.
private val TOP_PADDING = 54.dp
private val FIELD_GAP = 41.dp
private val BUTTON_GAP = 80.dp
private val LABEL_GAP = 8.dp
private val ROW_GAP = 16.dp
private val FORM_WIDTH = 310.dp
private val CROSS_SIZE = 18.dp
private val CROSS_STROKE = 2.5.dp

/**
 * The add medication wizard. [onClose] leaves it ("Takaisin" on step 1); [onSaved] runs once
 * the medication is saved.
 */
@Composable
fun LisaaLaakeScreen(
    onClose: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LisaaLaakeViewModel = viewModel(factory = LisaaLaakeViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // System back on step 2 returns to step 1 with the values kept.
    BackHandler(enabled = state.step == 2) { viewModel.onBackToStep1() }
    // A daily medication is reminded, so its save asks for notifications if they aren't allowed
    // yet. The answer isn't awaited: the dialog stays over the list, and Home's
    // banner reads the result on its own.
    val context = LocalContext.current
    val askNotifications = rememberLauncherForActivityResult(RequestPermission()) {}
    // Leave once saved, also when the process was restored after the save.
    LaunchedEffect(state.saved) {
        if (state.saved) {
            val daily = state.kuinkaUsein == KuinkaUsein.PAIVITTAIN
            if (daily && Ilmoitukset.tarvitseeLuvan(context)) {
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            onSaved()
        }
    }

    val content = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(start = 16.dp, end = 16.dp, top = TOP_PADDING, bottom = 32.dp)
    if (state.step == 1) {
        Step1(state, viewModel, onClose, modifier.then(content))
    } else {
        Step2(state, viewModel, modifier.then(content))
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
        if (state.loadError) {
            // The full error screen comes with the database error handling.
            Text(
                text = stringResource(R.string.virhe_lataus),
                style = MaterialTheme.typography.bodyMedium,
                color = LkDanger,
                modifier = Modifier.widthIn(max = FORM_WIDTH).fillMaxWidth()
            )
            Spacer(Modifier.height(FIELD_GAP))
        }
        LkTextField(
            label = stringResource(R.string.laake_label),
            value = viewModel.nimi,
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
            value = viewModel.annosmaara,
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

/** Figma: Aika list, Kuinka usein, the dates with the hint, "Valmis!", "Takaisin". */
@Composable
private fun Step2(state: LisaaLaakeUiState, viewModel: LisaaLaakeViewModel, modifier: Modifier) {
    val paivittain = stringResource(R.string.paivittain)
    val tarvittaessa = stringResource(R.string.tarvittaessa)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = FORM_WIDTH).fillMaxWidth()) {
            // Each row carries "Aika N" for TalkBack, so the label itself is skipped.
            Text(
                text = stringResource(R.string.aika_label),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.clearAndSetSemantics {}
            )
            Spacer(Modifier.height(LABEL_GAP))
            state.ajat.forEachIndexed { index, aika ->
                if (index > 0) Spacer(Modifier.height(ROW_GAP))
                TimeRow(state, viewModel, index, aika)
            }
            TextButton(
                onClick = viewModel::onLisaaAika,
                enabled = state.ajatEnabled,
                modifier = Modifier.heightIn(min = 48.dp),
                // Text flush with the fields' left edge.
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.lisaa_aika),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (state.ajatEnabled) LkPrimary else Color.Unspecified
                )
            }
        }
        Spacer(Modifier.height(FIELD_GAP))
        LkDropdown(
            label = stringResource(R.string.kuinka_usein_label),
            options = KuinkaUsein.entries,
            selected = state.kuinkaUsein,
            optionLabel = { if (it == KuinkaUsein.PAIVITTAIN) paivittain else tarvittaessa },
            onSelect = viewModel::onKuinkaUseinSelect,
            placeholder = stringResource(R.string.kuinka_usein_placeholder),
            error = if (state.step2Errors.kuinkaUsein) {
                stringResource(R.string.virhe_kuinka_usein_puuttuu)
            } else {
                null
            }
        )
        Spacer(Modifier.height(FIELD_GAP))
        LkDateField(
            label = stringResource(R.string.aloituspaiva_label),
            date = state.aloituspv,
            onDateChange = { date -> if (date != null) viewModel.onAloituspvChange(date) }
        )
        Spacer(Modifier.height(FIELD_GAP))
        LkDateField(
            label = stringResource(R.string.lopetuspaiva_label),
            date = state.lopetuspv,
            onDateChange = viewModel::onLopetuspvChange,
            error = if (state.step2Errors.lopetuspv) {
                stringResource(R.string.virhe_lopetus_ennen_aloitusta)
            } else {
                null
            },
            clearable = true,
            hint = state.kesto?.let { kestoTeksti(it) }
        )
        Spacer(Modifier.height(BUTTON_GAP))
        if (state.saveError) {
            Text(
                text = stringResource(R.string.virhe_tallennus),
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
        LkBackButton(stringResource(R.string.takaisin), onClick = viewModel::onBackToStep1)
    }
}

/** One time of the Aika list, with a remove button when there is more than one row. */
@Composable
private fun TimeRow(
    state: LisaaLaakeUiState,
    viewModel: LisaaLaakeViewModel,
    index: Int,
    aika: Int?
) {
    Row(verticalAlignment = Alignment.Top) {
        LkTimeField(
            minuteOfDay = aika,
            onTimeChange = { viewModel.onAikaChange(index, it) },
            description = stringResource(R.string.aika_rivi, index + 1),
            placeholder = stringResource(R.string.valitse_aika),
            modifier = Modifier.weight(1f),
            error = aikaErrorText(state.step2Errors.ajat[index]),
            enabled = state.ajatEnabled
        )
        if (state.ajat.size > 1) {
            val description = stringResource(R.string.poista_aika, index + 1)
            IconButton(
                onClick = { viewModel.onPoistaAika(index) },
                // Centred on the 60dp field.
                modifier = Modifier
                    .padding(start = 4.dp, top = 6.dp)
                    .size(48.dp)
                    .semantics { contentDescription = description }
            ) { Cross() }
        }
    }
}

/** The remove cross, drawn as two lines: the Figma has no icon for it. */
@Composable
private fun Cross() {
    Canvas(Modifier.size(CROSS_SIZE)) {
        val stroke = CROSS_STROKE.toPx()
        drawLine(LkOnLight, Offset.Zero, Offset(size.width, size.height), stroke, StrokeCap.Round)
        drawLine(
            LkOnLight,
            Offset(size.width, 0f),
            Offset(0f, size.height),
            stroke,
            StrokeCap.Round
        )
    }
}

@Composable
private fun aikaErrorText(error: AikaError?): String? = when (error) {
    null -> null
    AikaError.Puuttuu -> stringResource(R.string.virhe_aika_puuttuu)
    is AikaError.JoLisatty -> stringResource(R.string.virhe_aika_jo_lisatty, formatTime(error.aika))
}

@Composable
private fun nameErrorText(error: NameError?): String? = when (error) {
    null -> null
    NameError.Blank -> stringResource(R.string.virhe_nimi_puuttuu)
    NameError.TooLong -> stringResource(R.string.virhe_nimi_liian_pitka, NAME_MAX_LENGTH)
    is NameError.Duplicate -> stringResource(R.string.virhe_nimi_jo_lisatty, error.name)
}
