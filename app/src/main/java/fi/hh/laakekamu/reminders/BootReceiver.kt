package fi.hh.laakekamu.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import fi.hh.laakekamu.BuildConfig
import fi.hh.laakekamu.LaakeKamuApp
import fi.hh.laakekamu.data.TietokantaEiSaatavillaException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Sets the reminder alarms again when they may be lost or wrong: after a
 * reboot or an app update, and after the clock, the time zone or the exact-alarm permission
 * changes. After a reboot it also posts the doses missed while the phone was off.
 *
 * Exported for the system's protected broadcasts only; [onReceive] ignores any other action
 *. The work runs after `goAsync()` within 8 s; if the database can't be opened,
 * one generic notification asks the user to open the app.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action !in ACTIONS) return
        val container = (context.applicationContext as LaakeKamuApp).container
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + container.ioDispatcher).launch {
            val start = System.nanoTime()
            try {
                withTimeout(TIMEOUT_MS) {
                    container.scheduler.rescheduleAll()
                    if (action == Intent.ACTION_BOOT_COMPLETED) {
                        container.scheduler.postLateDoses()
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
                if (BuildConfig.DEBUG) {
                    val ms = (System.nanoTime() - start) / NANOS_PER_MS
                    Log.d(TAG, "$action handled in $ms ms")
                }
                pending.finish()
            }
        }
    }

    private companion object {
        /**
         * The manifest's actions. Only the system can send these. The exact-alarm one is a
         * literal because its constant is API 31+ (minSdk 26).
         */
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        )
        const val TIMEOUT_MS = 8_000L
        const val NANOS_PER_MS = 1_000_000L
        const val TAG = "BootReceiver"
    }
}
