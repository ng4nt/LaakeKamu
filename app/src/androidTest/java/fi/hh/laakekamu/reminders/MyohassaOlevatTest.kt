package fi.hh.laakekamu.reminders

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.hh.laakekamu.data.AppDatabase
import fi.hh.laakekamu.data.RoomLaakeRepository
import fi.hh.laakekamu.data.SeedCallback
import fi.hh.laakekamu.data.entity.Laake
import fi.hh.laakekamu.data.entity.Laakkeenotto
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
 * After a reboot at 00.30 the "Myöhässä" doses are the untaken ones of the last 2
 * hours, yesterday's with yesterday's date; taken and older doses are left out.
 */
@RunWith(AndroidJUnit4::class)
class MyohassaOlevatTest {
    private val zone = ZoneId.of("Europe/Helsinki")
    private val today = LocalDate.of(2025, 12, 18)
    private val yesterday = today.minusDays(1)
    private val clock = Clock.fixed(today.atTime(0, 30).atZone(zone).toInstant(), zone)

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

    /** Adds a medication started yesterday with a reminder at [aika] valid since yesterday. */
    private suspend fun lisaa(nimi: String, aika: Int): Long {
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
        // Starts a new reminder today; these were added yesterday.
        db.muistutusDao().setVoimassaAlkaen(id, yesterday)
        return id
    }

    @Test
    fun lateDosesAfterMidnight_areTheUntakenOnesOfTheLastTwoHours() = runBlocking {
        val myohassa = lisaa("A iltalääke", 23 * 60)
        val otettu = lisaa("B otettu", 22 * 60 + 45)
        lisaa("C vanha", 22 * 60)
        val tanaan = lisaa("D yölääke", 10)
        db.laakkeenottoDao().insert(
            Laakkeenotto(
                muistutusId = otettu,
                paivamaara = yesterday,
                ottoaika = clock.instant()
            )
        )

        val late = scheduler.myohassaOlevat().map { (annos, paiva) -> annos.muistutusId to paiva }

        assertEquals(listOf(myohassa to yesterday, tanaan to today), late)
    }
}
