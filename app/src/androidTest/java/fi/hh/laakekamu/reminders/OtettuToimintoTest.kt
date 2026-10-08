package fi.hh.laakekamu.reminders

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.hh.laakekamu.data.AppDatabase
import fi.hh.laakekamu.data.RoomLaakeRepository
import fi.hh.laakekamu.data.SeedCallback
import fi.hh.laakekamu.data.entity.Laake
import fi.hh.laakekamu.domain.KuinkaUsein
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "Otettu" on a notification ticks the dose of the
 * notification's day, also after midnight, twice without an error, and not at all when the
 * medication was deleted after the notification was posted.
 */
@RunWith(AndroidJUnit4::class)
class OtettuToimintoTest {
    private val zone = ZoneId.of("Europe/Helsinki")
    private val today = LocalDate.of(2025, 12, 18)
    private val yesterday = today.minusDays(1)

    // 00.10, just after midnight: yesterday's 23.58 dose is confirmed now.
    private val clock = Clock.fixed(today.atTime(0, 10).atZone(zone).toInstant(), zone)

    private lateinit var db: AppDatabase
    private lateinit var laakkeet: RoomLaakeRepository
    private lateinit var scheduler: MuistutusScheduler

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).addCallback(SeedCallback).build()
        laakkeet = RoomLaakeRepository({ db }, clock)
        scheduler = MuistutusScheduler(
            ApplicationProvider.getApplicationContext(),
            { db },
            clock,
            Dispatchers.IO
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** Adds a daily medication at [aika]; returns its id and its reminder's id. */
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
        return laakeId to db.muistutusDao().getByLaake(laakeId).single().muistutusId
    }

    private suspend fun otetut(paiva: LocalDate, id: Long) =
        db.laakkeenottoDao().takenOn(paiva, listOf(id))

    @Test
    fun otettuAfterMidnight_ticksTheNotificationsDay_andTwiceIsFine() = runBlocking {
        // The reminder was added yesterday (starts new ones today, so move it back).
        val (_, id) = lisaa("A iltalääke", 23 * 60 + 58)
        db.muistutusDao().setVoimassaAlkaen(id, yesterday)

        scheduler.otettu(id, yesterday)
        scheduler.otettu(id, yesterday)

        assertEquals(listOf(id), otetut(yesterday, id))
        assertEquals(emptyList<Long>(), otetut(today, id))
    }

    @Test
    fun otettuForADeletedMedication_ticksNothing() = runBlocking {
        val (laakeId, id) = lisaa("B poistettu", 8 * 60)
        laakkeet.delete(laakeId)

        scheduler.otettu(id, today)

        assertEquals(emptyList<Long>(), otetut(today, id))
    }
}
