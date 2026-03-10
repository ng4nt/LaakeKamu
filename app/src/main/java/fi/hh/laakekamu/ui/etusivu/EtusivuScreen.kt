package fi.hh.laakekamu.ui.etusivu

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fi.hh.laakekamu.R
import fi.hh.laakekamu.ui.components.PlaceholderScreen

@Composable
fun EtusivuScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(title = stringResource(R.string.nav_etusivu), modifier = modifier)
}
