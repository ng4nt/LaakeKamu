package fi.hh.laakekamu.ui.tietokanta

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fi.hh.laakekamu.R
import fi.hh.laakekamu.data.TietokannanTila
import fi.hh.laakekamu.ui.components.LkDangerButton
import fi.hh.laakekamu.ui.components.LkPrimaryButton
import fi.hh.laakekamu.ui.theme.LaakeKamuTheme
import fi.hh.laakekamu.ui.theme.LkDanger
import fi.hh.laakekamu.ui.theme.LkPrimary

/**
 * Shows [content] (the app) once the database is open, and "Tietoja ei voitu avata" when it
 * can't be opened. Opening takes a moment on the first start; the screen
 * stays empty meanwhile.
 */
@Composable
fun TietokantaPortti(
    viewModel: TietokantaViewModel = viewModel(factory = TietokantaViewModel.Factory),
    content: @Composable () -> Unit
) {
    val tila by viewModel.tila.collectAsStateWithLifecycle()
    val kaynnissa by viewModel.kaynnissa.collectAsStateWithLifecycle()
    when (tila) {
        TietokannanTila.AVAAMATTA -> Surface(Modifier.fillMaxSize()) {}
        TietokannanTila.AUKI -> content()
        TietokannanTila.EI_SAATAVILLA, TietokannanTila.AVAUS_EPAONNISTUI -> TietokantaVirheScreen(
            voiAloittaaAlusta = tila == TietokannanTila.EI_SAATAVILLA,
            enabled = !kaynnissa,
            onRetry = viewModel::yritaUudelleen,
            onStartOver = viewModel::aloitaAlusta
        )
    }
}

/**
 * "Tietoja ei voitu avata": retry, or start over after a confirmation
 * that says everything will be deleted. [onStartOver] runs only from the dialog's confirm
 * button, never straight from the screen. Without
 * [voiAloittaaAlusta] (the data is probably fine) only the retry is offered.
 */
@Composable
fun TietokantaVirheScreen(
    voiAloittaaAlusta: Boolean,
    enabled: Boolean,
    onRetry: () -> Unit,
    onStartOver: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    Scaffold(modifier) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = stringResource(R.string.tietokanta_virhe_otsikko),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineLarge,
                color = LkPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(
                    if (voiAloittaaAlusta) {
                        R.string.tietokanta_virhe_teksti
                    } else {
                        R.string.tietokanta_virhe_tilapainen
                    }
                ),
                modifier = Modifier.widthIn(max = BUTTON_WIDTH).fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(8.dp))
            LkPrimaryButton(
                text = stringResource(R.string.yrita_uudelleen),
                onClick = onRetry,
                enabled = enabled
            )
            if (voiAloittaaAlusta) {
                LkDangerButton(
                    text = stringResource(R.string.aloita_alusta),
                    onClick = { confirming = true },
                    enabled = enabled
                )
            }
        }
    }
    // A retry may turn the state into one start-over can't fix: the dialog goes then too.
    if (confirming && voiAloittaaAlusta) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = {
                Text(
                    text = stringResource(R.string.aloita_alusta_otsikko),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(text = stringResource(R.string.aloita_alusta_teksti), fontSize = 18.sp)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onStartOver()
                    },
                    enabled = enabled
                ) {
                    Text(stringResource(R.string.aloita_alusta), color = LkDanger, fontSize = 18.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text(stringResource(R.string.peruuta), color = LkPrimary, fontSize = 18.sp)
                }
            }
        )
    }
}

// The wide buttons' width (Figma 310dp), so the text lines up with them.
private val BUTTON_WIDTH = 310.dp

@Preview(widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun TietokantaVirheScreenPreview() {
    LaakeKamuTheme {
        TietokantaVirheScreen(
            voiAloittaaAlusta = true,
            enabled = true,
            onRetry = {},
            onStartOver = {}
        )
    }
}
