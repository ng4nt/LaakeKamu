package fi.hh.laakekamu.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import fi.hh.laakekamu.BuildConfig
import fi.hh.laakekamu.LaakeKamuApp
import fi.hh.laakekamu.data.TietokantaEiSaatavillaException
import fi.hh.laakekamu.domain.isValidReminderTime
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * A reminder slot's alarm fired. Reached only through the scheduler's
 * explicit PendingIntent (not exported, no intent-filter). The work runs after
 * `goAsync()` in its own scope and must finish within 8 s; if the database can't be opened,
 * one generic notification asks the user to open the app, with no medication
 * data.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val aika = intent.getIntExtra(EXTRA_AIKA, -1)
        val paiva = intent.getLongExtra(EXTRA_PAIVA, NO_DAY)
        if (!isValidReminderTime(aika) || paiva == NO_DAY) return
        val container = (context.applicationContext as LaakeKamuApp).container
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + container.ioDispatcher).launch {
            val start = System.nanoTime()
            try {
                withTimeout(TIMEOUT_MS) {
                    container.scheduler.slotFired(aika, LocalDate.ofEpochDay(paiva))
                }
            } catch (e: TietokantaEiSaatavillaException) {
                Ilmoitukset.naytaTietokantaVirhe(context)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Slot $aika took too long", e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Slot $aika failed", e)
            } finally {
                if (BuildConfig.DEBUG) {
                    val ms = (System.nanoTime() - start) / NANOS_PER_MS
                    Log.d(TAG, "Slot $aika handled in $ms ms")
                }
                pending.finish()
            }
        }
    }

    companion object {
        /** The slot's time, minutes after midnight. */
        const val EXTRA_AIKA = "fi.hh.laakekamu.extra.AIKA"

        /** The dose day as an epoch day (never "today"). */
        const val EXTRA_PAIVA = "fi.hh.laakekamu.extra.PAIVA"

        private const val NO_DAY = Long.MIN_VALUE
        private const val TIMEOUT_MS = 8_000L
        private const val NANOS_PER_MS = 1_000_000L
        private const val TAG = "AlarmReceiver"
    }
}
