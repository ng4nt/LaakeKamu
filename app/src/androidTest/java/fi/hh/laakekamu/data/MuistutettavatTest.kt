package fi.hh.laakekamu.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.hh.laakekamu.data.entity.Laake
import fi.hh.laakekamu.data.entity.Muistutus
import fi.hh.laakekamu.domain.KuinkaUsein
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * the scheduler and the alarm read the reminders that can still be due, with their
 * medication's name, dose and period: active daily medications only, ended reminders left
 * out, by time and name.
 */
@RunWith(AndroidJUnit4::class)
class MuistutettavatTest {
    private val zone = ZoneId.of("Europe/Helsinki")
    private val today = LocalDate.of(2025, 12, 18)
    private val clock = Clock.fixed(today.atTime(10, 0).atZone(zone).toInstant(), zone)

    private lateinit var db: AppDatabase
    private lateinit var laakkeet: RoomLaakeRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).addCallback(SeedCallback).build()
        laakkeet = RoomLaakeRepository({ db }, clock)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun laake(nimi: String, kuinkaUsein: KuinkaUsein = KuinkaUsein.PAIVITTAIN) =
        Laake(
            nimi = nimi,
            tyyppiId = laakkeet.observeTyypit().first().first().tyyppiId,
            annosmaara = "5",
            yksikko = "mg",
            kuinkaUsein = kuinkaUsein,
            aloituspv = today,
            lopetuspv = null
        )

    @Test
    fun onlyRemindersThatCanStillBeDue_ofActiveDailyMedications_byTimeAndName() = runBlocking {
        val b = laakkeet.add(laake("B iltalääke"), listOf(20 * 60, 8 * 60))
        laakkeet.add(laake("A aamulääke"), listOf(8 * 60))
        laakkeet.add(laake("Burana", KuinkaUsein.TARVITTAESSA), emptyList())
        val deleted = laakkeet.add(laake("C poistettu"), listOf(9 * 60))
        laakkeet.delete(deleted)
        // A reminder that ended yesterday can't be due any more.
        db.muistutusDao().insert(
            Muistutus(
                laakeId = b,
                aika = 7 * 60,
                voimassaAlkaen = today.minusDays(5),
                voimassaAsti = today.minusDays(1)
            )
        )

        val found = db.muistutusDao().getMuistutettavat(today)

        assertEquals(
            listOf(8 * 60 to "A aamulääke", 8 * 60 to "B iltalääke", 20 * 60 to "B iltalääke"),
            found.map { it.aika to it.nimi }
        )
        val a = found.first()
        assertEquals(listOf("5", "mg"), listOf(a.annosmaara, a.yksikko))
        assertEquals(today, a.aloituspv)
        assertEquals(today, a.voimassaAlkaen)
        assertEquals(null, a.voimassaAsti)
    }
}
