package fi.hh.laakekamu.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import fi.hh.laakekamu.data.AppDatabase
import fi.hh.laakekamu.data.dao.MuistutettavaAnnos
import fi.hh.laakekamu.domain.MuistutusAikataulu
import fi.hh.laakekamu.domain.aikaToLocalTime
import fi.hh.laakekamu.domain.isDueOn
import fi.hh.laakekamu.domain.nextSlotTriggers
import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The dose reminders' alarms: one alarm per
 * reminder time (slot) at its next due occurrence, set with `setAlarmClock` so Doze doesn't
 * delay it. When a slot fires, [slotFired] sets its next occurrence and posts one notification
 * per medication that is due and not taken.
 *
 * Created once in the AppContainer; its Mutex serializes every change to the alarms.
 */
class MuistutusScheduler(
    context: Context,
    private val database: suspend () -> AppDatabase,
    private val clock: Clock,
    private val ioDispatcher: CoroutineDispatcher
) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)
    private val mutex = Mutex()

    /**
     * Sets every slot's alarm to its next occurrence and cancels the slots no longer used (the
     * slots set last time are kept in prefs). Setting a slot again replaces its alarm, so this is
     * safe to run any time: on app start and after every medication change.
     *
     * @throws fi.hh.laakekamu.data.TietokantaEiSaatavillaException if the database can't be
     * opened; callers decide how to report it.
     */
    suspend fun rescheduleAll() {
        // The read is inside the lock too, so an older run can't set what a newer one replaced.
        mutex.withLock {
            val now = ZonedDateTime.now(clock)
            val muistutettavat = database().muistutusDao().getMuistutettavat(now.toLocalDate())
            val slots = nextSlotTriggers(muistutettavat.map { it.aikataulu() }, now)
            withContext(ioDispatcher) {
                slots.forEach { (aika, at) -> setSlot(aika, at) }
                val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val previous = prefs.getStringSet(KEY_SLOTS, emptySet()).orEmpty()
                (previous.map(String::toInt) - slots.keys).forEach(::cancelSlot)
                prefs.edit().putStringSet(KEY_SLOTS, slots.keys.map(Int::toString).toSet()).apply()
            }
        }
    }

    /** [rescheduleAll] after a medication save or delete: a failure is logged, not thrown. */
    suspend fun rescheduleAfterChange() {
        try {
            rescheduleAll()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Rescheduling after a change failed", e)
        }
    }

    /**
     * The slot [aika] of [paiva] fired: sets only this slot's next occurrence
     * first (a full reschedule could replace another slot's alarm that is due but not yet
     * delivered), then posts each medication in the slot that is due on [paiva] and not taken.
     * The dose day comes from the alarm, never "today", so a late delivery after midnight still
     * reminds of the right day's dose.
     */
    suspend fun slotFired(aika: Int, paiva: LocalDate) {
        val db = database()
        val now = ZonedDateTime.now(clock)
        val slotTime = ZonedDateTime.of(paiva, aikaToLocalTime(aika), now.zone)
        // Strictly after this occurrence, so the same alarm isn't set again.
        val after = maxOf(now, slotTime).plusMinutes(1)
        val inSlot = mutex.withLock {
            val found = db.muistutusDao().getMuistutettavat(paiva).filter { it.aika == aika }
            nextSlotTriggers(found.map { it.aikataulu() }, after)[aika]?.let { next ->
                withContext(ioDispatcher) { setSlot(aika, next) }
            }
            found
        }
        val due = inSlot.filter {
            isDueOn(paiva, it.voimassaAlkaen, it.voimassaAsti, it.aloituspv, it.lopetuspv)
        }
        if (due.isEmpty()) return
        val taken = db.laakkeenottoDao().takenOn(paiva, due.map { it.muistutusId }).toSet()
        due.filter { it.muistutusId !in taken }.forEach { Ilmoitukset.naytaAnnos(appContext, it) }
    }

    private fun setSlot(aika: Int, at: ZonedDateTime) {
        val operation = slotIntent(aika, at.toLocalDate(), PendingIntent.FLAG_UPDATE_CURRENT)!!
        val millis = at.toInstant().toEpochMilli()
        if (canScheduleExact()) {
            val info = AlarmManager.AlarmClockInfo(millis, Ilmoitukset.avaaSovellus(appContext))
            alarmManager.setAlarmClock(info, operation)
        } else {
            // API 31-32 with the permission revoked: may be late (the banner is cut).
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation)
        }
    }

    private fun cancelSlot(aika: Int) {
        slotIntent(aika, null, PendingIntent.FLAG_NO_CREATE)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    /** Before Android 12 exact alarms need no permission; on 13+ USE_EXACT_ALARM can't be revoked. */
    private fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /**
     * The slot's AlarmReceiver intent: request code = [aika], an explicit component
     * and a data URI that only gives it its identity. [paiva] is the dose day of the occurrence.
     */
    private fun slotIntent(aika: Int, paiva: LocalDate?, flags: Int): PendingIntent? {
        val intent = Intent(appContext, AlarmReceiver::class.java)
            .setData(Uri.parse("laakekamu://slot/$aika"))
            .putExtra(AlarmReceiver.EXTRA_AIKA, aika)
        paiva?.let { intent.putExtra(AlarmReceiver.EXTRA_PAIVA, it.toEpochDay()) }
        return PendingIntent.getBroadcast(
            appContext,
            aika,
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun MuistutettavaAnnos.aikataulu() =
        MuistutusAikataulu(muistutusId, aika, voimassaAlkaen, voimassaAsti, aloituspv, lopetuspv)

    private companion object {
        const val TAG = "MuistutusScheduler"
        const val PREFS = "muistutukset"
        const val KEY_SLOTS = "slots"
    }
}
