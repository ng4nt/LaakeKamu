package fi.hh.laakekamu.ui.muokkaalaake

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fi.hh.laakekamu.R
import fi.hh.laakekamu.data.entity.Laake
import fi.hh.laakekamu.ui.components.LkBackButton
import fi.hh.laakekamu.ui.components.LkDangerButton
import fi.hh.laakekamu.ui.components.LkDropdown
import fi.hh.laakekamu.ui.components.LkPrimaryButton
import fi.hh.laakekamu.ui.theme.LaakeKamuTheme
import fi.hh.laakekamu.ui.theme.LkDanger
import fi.hh.laakekamu.ui.theme.LkInput
import fi.hh.laakekamu.ui.theme.LkPrimary

// Figma, less the 24dp status bar as in the wizard: the 100dp icon at 149, the label 25dp
// below it, 60dp from the field to "Muokkaa", 46dp between the buttons, "Takaisin" 47dp from
// the bottom of the frame.
private val TOP_PADDING = 125.dp
private val ICON_SIZE = 100.dp
private val ICON_GAP = 25.dp
private val FIELD_GAP = 60.dp
private val BUTTON_GAP = 46.dp
private val BACK_GAP = 16.dp
private val BOTTOM_PADDING = 47.dp
private val FORM_WIDTH = 310.dp

/**
 * Muokkaa lääkettä: choose a medication, then edit it in the wizard ([onEdit])
 * or delete it after a confirmation. [onDeleted] runs once it's deleted.
 */
@Composable
fun MuokkaaLaakettaScreen(
    onClose: () -> Unit,
    onDeleted: () -> Unit,
    onEdit: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MuokkaaLaakettaViewModel = viewModel(factory = MuokkaaLaakettaViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Leave once deleted, also when the process was restored after the delete.
    LaunchedEffect(state.poistettu) {
        if (state.poistettu) onDeleted()
    }
    MuokkaaLaakettaContent(
        state = state,
        onSelect = viewModel::onSelect,
        onEdit = onEdit,
        onDeleteClick = viewModel::onDeleteClick,
        onClose = onClose,
        modifier = modifier
    )
    state.valittu?.takeIf { state.vahvistetaan }?.let { laake ->
        PoistaLaakeDialog(
            nimi = laake.nimi,
            onConfirm = viewModel::onDeleteConfirm,
            onDismiss = viewModel::onDeleteDismiss
        )
    }
}

@Composable
private fun MuokkaaLaakettaContent(
    state: MuokkaaLaakettaUiState,
    onSelect: (Laake) -> Unit,
    onEdit: (Long) -> Unit,
    onDeleteClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val valittu = state.valittu
    val enabled = valittu != null && !state.poistetaan
    Column(
        modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = BOTTOM_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // The form scrolls (landscape, large fonts); "Takaisin" stays at the bottom.
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(TOP_PADDING))
            Icon(
                painter = painterResource(R.drawable.ic_medicine),
                contentDescription = null,
                tint = LkInput,
                modifier = Modifier.size(ICON_SIZE)
            )
            Spacer(Modifier.height(ICON_GAP))
            LkDropdown(
                label = stringResource(R.string.haluan_muokata),
                options = state.laakkeet,
                selected = valittu,
                optionLabel = { it.nimi },
                onSelect = onSelect,
                placeholder = stringResource(R.string.valitse),
                error = if (state.loadError) {
                    stringResource(
                        R.string.virhe_muokkaa_lataus
                    )
                } else {
                    null
                },
                enabled = !state.poistetaan
            )
            Spacer(Modifier.height(FIELD_GAP))
            LkPrimaryButton(
                text = stringResource(R.string.muokkaa),
                onClick = { valittu?.let { onEdit(it.laakeId) } },
                enabled = enabled
            )
            Spacer(Modifier.height(BUTTON_GAP))
            LkDangerButton(
                text = stringResource(R.string.poista_laake),
                onClick = onDeleteClick,
                enabled = enabled
            )
            if (state.poistoEpaonnistui) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.virhe_laake_poisto),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LkDanger,
                    modifier = Modifier.widthIn(max = FORM_WIDTH).fillMaxWidth()
                )
            }
            Spacer(Modifier.height(BUTTON_GAP - BACK_GAP))
        }
        // Part of the gap stays outside the scroll: at 200 % font the form scrolls, and a
        // half-shown "Poista lääke" would otherwise touch "Takaisin".
        Spacer(Modifier.height(BACK_GAP))
        LkBackButton(stringResource(R.string.takaisin), onClick = onClose)
    }
}

/**
 * The confirmation before a medication is deleted: "Poista" in danger, "Peruuta" in
 * primary, like the symptom delete.
 */
@Composable
private fun PoistaLaakeDialog(nimi: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.poista_laake_otsikko, nimi),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = { Text(text = stringResource(R.string.poista_laake_teksti), fontSize = 18.sp) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.poista),
                    color = LkDanger,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.peruuta),
                    color = LkPrimary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    )
}

@Preview(widthDp = 412, heightDp = 893, showBackground = true)
@Composable
private fun MuokkaaLaakettaPreview() {
    LaakeKamuTheme {
        MuokkaaLaakettaContent(
            state = MuokkaaLaakettaUiState(),
            onSelect = {},
            onEdit = {},
            onDeleteClick = {},
            onClose = {}
        )
    }
}
