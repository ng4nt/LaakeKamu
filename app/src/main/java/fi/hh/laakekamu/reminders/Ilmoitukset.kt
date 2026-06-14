package fi.hh.laakekamu.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import fi.hh.laakekamu.MainActivity
import fi.hh.laakekamu.R
import fi.hh.laakekamu.data.dao.MuistutettavaAnnos
import fi.hh.laakekamu.ui.format.formatTime
import java.time.LocalDate

/** The dose reminders' notification channel and notifications. */
object Ilmoitukset {
    /** The channel id. Never change it: Android keeps the user's channel settings under it. */
    const val KANAVA = "laakemuistutukset"

    /** The "open the app" notification's id; reminder ids start at 1, so it can't clash. */
    private const val TIETOKANTA_VIRHE_ID = 0

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

    /**
     * "Aika ottaa lääke" / "A aamulääke 5 mg · klo 8.00" for the dose of [paiva], one per
     * medication (id = its reminder's id), with "Otettu" and "Muistuta 10 min päästä"
     *. The lock screen shows only the public version without the name.
     * A dose missed while the phone was off ([myohassa]) is titled "Myöhässä".
     */
    fun naytaAnnos(
        context: Context,
        annos: MuistutettavaAnnos,
        paiva: LocalDate,
        myohassa: Boolean = false
    ) {
        val text = context.getString(
            R.string.ilmoitus_annos,
            annos.nimi,
            annos.annosmaara,
            annos.yksikko,
            formatTime(annos.aika)
        )
        val notification = builder(context)
            .setContentTitle(
                context.getString(
                    if (myohassa) R.string.ilmoitus_myohassa else R.string.ilmoitus_otsikko
                )
            )
            .setContentText(text)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(julkinen(context))
            .addAction(
                0,
                context.getString(R.string.ilmoitus_otettu),
                toiminto(context, annos.muistutusId, paiva, NotificationActionReceiver.OTETTU)
            )
            .addAction(
                0,
                context.getString(R.string.ilmoitus_muistuta),
                toiminto(context, annos.muistutusId, paiva, NotificationActionReceiver.MUISTUTA)
            )
            .build()
        notify(context, annos.muistutusId.toInt(), notification)
    }

    /** Removes the reminder's notification: its dose was ticked or snoozed. */
    fun poista(context: Context, muistutusId: Long) {
        NotificationManagerCompat.from(context).cancel(muistutusId.toInt())
    }

    /** Removes every notification: "Aloita alusta" deleted the data they are about. */
    fun poistaKaikki(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }

    /**
     * A button's explicit PendingIntent: the data URI
     * `laakekamu://annos/{id}/{day}/{action}` only gives each one its identity; there's no
     * intent-filter for it.
     */
    private fun toiminto(
        context: Context,
        muistutusId: Long,
        paiva: LocalDate,
        action: String
    ): PendingIntent {
        val nimi = action.substringAfterLast('.')
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("laakekamu://annos/$muistutusId/$paiva/$nimi"))
            .putExtra(NotificationActionReceiver.EXTRA_MUISTUTUS_ID, muistutusId)
            .putExtra(AlarmReceiver.EXTRA_PAIVA, paiva.toEpochDay())
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    /**
     * The database couldn't be opened in a receiver: one generic notification with no
     * medication data. Opening the app shows its error screen.
     */
    fun naytaTietokantaVirhe(context: Context) {
        val notification = builder(context)
            .setContentTitle(context.getString(R.string.ilmoitus_huomio))
            .setContentText(context.getString(R.string.ilmoitus_avaa_sovellus))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        notify(context, TIETOKANTA_VIRHE_ID, notification)
    }

    /**
     * Opens the app like its launcher icon does (explicit, no deep-link filter):
     * a running app comes to the front as it was, a new start opens Etusivu.
     */
    fun avaaSovellus(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun builder(context: Context) = NotificationCompat.Builder(context, KANAVA)
        // The medicine icon until the app icon arrives.
        .setSmallIcon(R.drawable.ic_medicine)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(avaaSovellus(context))
        .setAutoCancel(true)

    /** The lock screen version: "LääkeKamu: sinulla on lääkemuistutus", no name or dose. */
    private fun julkinen(context: Context): Notification = builder(context)
        .setContentTitle(context.getString(R.string.app_name))
        .setContentText(context.getString(R.string.ilmoitus_julkinen))
        .build()

    // The permission is checked right before; without it nothing is posted.
    @SuppressLint("MissingPermission")
    private fun notify(context: Context, id: Int, notification: Notification) {
        if (tarvitseeLuvan(context)) return
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
