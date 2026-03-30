package fi.hh.laakekamu.ui.laakelista

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fi.hh.laakekamu.R
import fi.hh.laakekamu.ui.components.LkSmallButton
import fi.hh.laakekamu.ui.components.PlaceholderScreen

/**
 * Lääkelista. For now the title and the "Lisää" button (Figma: 34dp from the left edge,
 * 56dp above the bottom bar); the list itself is built later.
 */
@Composable
fun LaakelistaScreen(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        PlaceholderScreen(title = stringResource(R.string.nav_laakelista))
        LkSmallButton(
            text = stringResource(R.string.lisaa),
            onClick = onAdd,
            filled = true,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 34.dp, bottom = 56.dp)
        )
    }
}
