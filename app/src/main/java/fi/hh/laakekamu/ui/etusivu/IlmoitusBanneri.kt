package fi.hh.laakekamu.ui.etusivu

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import fi.hh.laakekamu.R
import fi.hh.laakekamu.ui.components.LkSmallButton
import fi.hh.laakekamu.ui.theme.LkInput

/**
 * Whether the app may show notifications, read again on every resume, so coming back from
 * Android's settings or the permission dialog updates it. Covers every
 * Android version: before 13 the user can still turn the app's notifications off.
 */
@Composable
fun rememberIlmoituksetSallittu(): Boolean {
    val context = LocalContext.current
    var sallittu by remember { mutableStateOf(sallittu(context)) }
    LifecycleResumeEffect(Unit) {
        sallittu = sallittu(context)
        onPauseOrDispose {}
    }
    return sallittu
}

private fun sallittu(context: Context) =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

/**
 * "Muistutukset ovat pois päältä" with "Salli ilmoitukset", which opens the app's notification
 * settings. The button stays under the text so a large font
 * can't squeeze it.
 */
@Composable
fun IlmoitusBanneri(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(LkInput)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.muistutukset_pois),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Black
        )
        LkSmallButton(
            text = stringResource(R.string.salli_ilmoitukset),
            onClick = { avaaIlmoitusasetukset(context) },
            filled = false,
            modifier = Modifier.align(Alignment.End)
        )
    }
}

/** The app's own notification page in Android's settings (API 26+). */
private fun avaaIlmoitusasetukset(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    context.startActivity(intent)
}
