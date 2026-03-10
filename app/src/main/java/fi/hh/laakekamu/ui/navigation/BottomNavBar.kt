package fi.hh.laakekamu.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fi.hh.laakekamu.ui.theme.LaakeKamuTheme
import fi.hh.laakekamu.ui.theme.LkNavBar

// Figma bottom bar: 85dp high, 60dp icons 34dp from the edges.
private val BAR_HEIGHT = 85.dp
private val ICON_SIZE = 60.dp
private val EDGE_PADDING = 34.dp

/**
 * Bottom bar of the main screens. The selected tab is primary and the others white, as in
 * Figma (the white icons' 2.28:1 contrast is a documented, author-accepted issue).
 */
@Composable
fun BottomNavBar(
    currentRoute: String?,
    onSelect: (MainDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(LkNavBar)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(BAR_HEIGHT)
            .padding(horizontal = EDGE_PADDING)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MainDestination.entries.forEach { destination ->
            val selected = destination.route == currentRoute
            Box(
                modifier = Modifier
                    .size(ICON_SIZE)
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = { onSelect(destination) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(destination.icon),
                    contentDescription = stringResource(destination.label),
                    tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.size(ICON_SIZE)
                )
            }
        }
    }
}

@Preview(widthDp = 412)
@Composable
private fun BottomNavBarPreview() {
    LaakeKamuTheme {
        BottomNavBar(currentRoute = MainDestination.LAAKELISTA.route, onSelect = {})
    }
}
