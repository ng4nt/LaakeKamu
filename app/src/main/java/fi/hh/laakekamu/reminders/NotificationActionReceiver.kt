package fi.hh.laakekamu.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import fi.hh.laakekamu.LaakeKamuApp
import fi.hh.laakekamu.data.TietokantaEiSaatavillaException
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * A reminder's "Otettu" or "Muistuta 10 min päästä" button. Reached only
 * through the notification's explicit PendingIntents (not exported, no intent-filter). The dose day comes from the intent, never "today", so a 23.58 dose
 * confirmed after midnight lands on its own day.
 */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val muistutusId = intent.getLongExtra(EXTRA_MUISTUTUS_ID, -1L)
        val paivaDay = intent.getLongExtra(AlarmReceiver.EXTRA_PAIVA, Long.MIN_VALUE)
        val action = intent.action
        if (muistutusId <= 0 || paivaDay == Long.MIN_VALUE) return
        if (action != OTETTU && action != MUISTUTA) return
        val paiva = LocalDate.ofEpochDay(paivaDay)
        // The button's notification goes at once, also if the work below fails.
        Ilmoitukset.poista(context, muistutusId)
        val container = (context.applicationContext as LaakeKamuApp).container
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + container.ioDispatcher).launch {
            try {
                withTimeout(TIMEOUT_MS) {
                    if (action == OTETTU) {
                        container.scheduler.otettu(muistutusId, paiva)
                    } else {
                        container.scheduler.snooze(muistutusId, paiva)
                    }
                }
            } catch (e: TietokantaEiSaatavillaException) {
                Ilmoitukset.naytaTietokantaVirhe(context)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "$action took too long", e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "$action failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val OTETTU = "fi.hh.laakekamu.action.OTETTU"
        const val MUISTUTA = "fi.hh.laakekamu.action.MUISTUTA"

        /** The reminder whose dose the button is for. */
        const val EXTRA_MUISTUTUS_ID = "fi.hh.laakekamu.extra.MUISTUTUS_ID"

        private const val TIMEOUT_MS = 8_000L
        private const val TAG = "NotificationAction"
    }
}
