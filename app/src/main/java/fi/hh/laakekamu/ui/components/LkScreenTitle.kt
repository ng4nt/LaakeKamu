package fi.hh.laakekamu.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fi.hh.laakekamu.ui.theme.LkPrimary

// Figma: the title and its 35dp icon, about 9dp apart, centred as a group.
private val ICON_SIZE = 35.dp
private val ICON_GAP = 9.dp

/** A main screen's title (32 Bold primary) with its icon after it, read as one heading. */
@Composable
fun LkScreenTitle(text: String, @DrawableRes icon: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { heading() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = text, style = MaterialTheme.typography.headlineLarge, color = LkPrimary)
        Spacer(Modifier.width(ICON_GAP))
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = LkPrimary,
            modifier = Modifier.size(ICON_SIZE)
        )
    }
}
