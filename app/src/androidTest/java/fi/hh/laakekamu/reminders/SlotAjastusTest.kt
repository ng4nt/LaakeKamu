package fi.hh.laakekamu.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fi.hh.laakekamu.data.AppDatabase
import fi.hh.laakekamu.data.RoomLaakeRepository
import fi.hh.laakekamu.data.SeedCallback
import fi.hh.laakekamu.data.entity.Laake
import fi.hh.laakekamu.data.entity.Laakkeenotto
import fi.hh.laakekamu.domain.KuinkaUsein
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The scheduler's riskiest paths with a fixed clock. `rescheduleAll` cancels the
 * slots no longer used; `slotFired` sets the next occurrence after a late delivery past
 * midnight, and posts only the untaken doses.
 */
@RunWith(AndroidJUnit4::class)
class SlotAjastusTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val zone = ZoneId.of("Europe/Helsinki")

    // Tomorrow, so the alarms are in the future of the real clock (AlarmManager).
    private val today = LocalDate.now(zone).plusDays(1)
    private val yesterday = today.minusDays(1)

    // 00.05: yesterday's 23.50 alarm is delivered late, after midnight.
    private val clock = Clock.fixed(today.atTime(0, 5).atZone(zone).toInstant(), zone)

    private lateinit var db: AppDatabase
    private lateinit var laakkeet: RoomLaakeRepository
    private lateinit var scheduler: MuistutusScheduler

    @Before
    fun setUp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
        Ilmoitukset.luoKanava(context)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(SeedCallback)
            .build()
        laakkeet = RoomLaakeRepository({ db }, clock)
        scheduler = MuistutusScheduler(context, { db }, clock, Dispatchers.IO)
    }

    @After
    fun tearDown() = runBlocking {
        // Nothing left to remind of: cancels every slot this test set.
        db.laakeDao().getActive().forEach { laakkeet.delete(it.laakeId) }
        scheduler.rescheduleAll()
        notifications.cancelAll()
        db.close()
    }

    /** Adds a daily medication at [aika] that started yesterday; returns its id and reminder id. */
    private suspend fun lisaa(nimi: String, aika: Int): Pair<Long, Long> {
        val laakeId = laakkeet.add(
            Laake(
                nimi = nimi,
                tyyppiId = laakkeet.observeTyypit().first().first().tyyppiId,
                annosmaara = "5",
                yksikko = "mg",
                kuinkaUsein = KuinkaUsein.PAIVITTAIN,
                aloituspv = yesterday,
                lopetuspv = null
            ),
            listOf(aika)
        )
        val id = db.muistutusDao().getByLaake(laakeId).single().muistutusId
        db.muistutusDao().setVoimassaAlkaen(id, yesterday)
        return laakeId to id
    }

    /** True while the slot's alarm is set (its PendingIntent exists). */
    private fun slotSet(aika: Int): Boolean {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setData(Uri.parse("laakekamu://slot/$aika"))
        return PendingIntent.getBroadcast(
            context,
            aika,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) != null
    }

    private suspend fun shownIdsWhen(expected: Set<Int>): Set<Int> {
        withTimeoutOrNull(WAIT_MS) {
            while (shownIds() != expected) delay(POLL_MS)
        }
        return shownIds()
    }

    private fun shownIds() = notifications.activeNotifications.map { it.id }.toSet()

    @Test
    fun rescheduleAll_cancelsASlotThatIsNoLongerUsed() = runBlocking {
        lisaa("A aamulääke", 8 * 60)
        val (laakeB, _) = lisaa("B iltalääke", 21 * 60)
        scheduler.rescheduleAll()
        assertTrue(slotSet(8 * 60))
        assertTrue(slotSet(21 * 60))

        laakkeet.delete(laakeB)
        scheduler.rescheduleAll()

        assertTrue(slotSet(8 * 60))
        assertFalse(slotSet(21 * 60))
    }

    @Test
    fun slotFiredAfterMidnight_setsTheNextOccurrenceTonight() = runBlocking {
        scheduler.rescheduleAll() // cancels the slots other tests left (empty database)
        val aika = 23 * 60 + 50
        lisaa("A iltalääke", aika)

        scheduler.slotFired(aika, yesterday)

        val next = alarms.nextAlarmClock
        assertNotNull(next)
        // The alarm clock is the app's own (its show intent), at 23.50 today, not yesterday.
        assertEquals(context.packageName, next!!.showIntent.creatorPackage)
        assertEquals(
            today.atTime(23, 50).atZone(zone).toInstant(),
            Instant.ofEpochMilli(next.triggerTime)
        )
    }

    @Test
    fun slotFired_postsOnlyTheUntakenDose() = runBlocking {
        val aika = 23 * 60 + 50
        val (_, taken) = lisaa("A otettu", aika)
        val (_, untaken) = lisaa("B ottamatta", aika)
        db.laakkeenottoDao().insert(
            Laakkeenotto(muistutusId = taken, paivamaara = yesterday, ottoaika = Instant.now(clock))
        )

        scheduler.slotFired(aika, yesterday)

        // B is the positive control: once it is shown, A would have been shown too.
        assertEquals(setOf(untaken.toInt()), shownIdsWhen(setOf(untaken.toInt())))
        assertNull(notifications.activeNotifications.firstOrNull { it.id == taken.toInt() })
    }

    private companion object {
        const val WAIT_MS = 3_000L
        const val POLL_MS = 50L
    }
}
