package fi.hh.laakekamu.reminders

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import fi.hh.laakekamu.R

/** The notification channel of the dose reminders. */
object Ilmoitukset {
    /** The channel id. Never change it: Android keeps the user's channel settings under it. */
    const val KANAVA = "laakemuistutukset"

    /**
     * Creates the "Lääkemuistutukset" channel with high importance, so a reminder pops up and
     * makes a sound. Creating it again only updates its name; the importance, sound and
     * vibration are then the user's to change in Android's settings.
     */
    fun luoKanava(context: Context) {
        val kanava = NotificationChannelCompat.Builder(
            KANAVA,
            NotificationManagerCompat.IMPORTANCE_HIGH
        ).setName(context.getString(R.string.kanava_muistutukset)).build()
        NotificationManagerCompat.from(context).createNotificationChannel(kanava)
    }

    /**
     * True on Android 13+ while `POST_NOTIFICATIONS` isn't granted, so the app must ask for it. Older versions have no such runtime permission.
     */
    fun tarvitseeLuvan(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
}
