package fi.hh.laakekamu.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.hh.laakekamu.data.entity.Laake
import fi.hh.laakekamu.data.entity.Laakkeenotto
import fi.hh.laakekamu.domain.KuinkaUsein
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The diary's side: the diary reads the taken doses of a range of days, and a
 * deleted medication's taken doses, reminders and name are still there for it.
 */
@RunWith(AndroidJUnit4::class)
class PaivakirjaRepositoryTest {
    private val zone = ZoneId.of("Europe/Helsinki")
    private val today = LocalDate.of(2025, 12, 18)
    private val clock = Clock.fixed(today.atTime(10, 0).atZone(zone).toInstant(), zone)

    private lateinit var db: AppDatabase
    private lateinit var laakkeet: RoomLaakeRepository
    private lateinit var paivakirja: RoomPaivakirjaRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).addCallback(SeedCallback).build()
        laakkeet = RoomLaakeRepository({ db }, clock)
        paivakirja = RoomPaivakirjaRepository { db }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun observeOtot_returnsOnlyTheRange_inclusive() = runTest {
        val muistutus = addWithReminder("A aamulääke")
        listOf(1L, 2L, 5L).forEach { take(muistutus, today.minusDays(it)) }

        val otot = paivakirja.observeOtot(today.minusDays(2), today.minusDays(1)).first()

        assertEquals(
            setOf(today.minusDays(2), today.minusDays(1)),
            otot.map { it.paivamaara }.toSet()
        )
    }

    @Test
    fun deletedMedication_keepsItsTakenDoseAndName_forTheDiary() = runTest {
        val muistutus = addWithReminder("A aamulääke")
        take(muistutus, today.minusDays(1))
        val laakeId = db.muistutusDao().observeAll().first().single().laakeId

        laakkeet.delete(laakeId)

        val otto = paivakirja.observeOtot(today.minusDays(1), today.minusDays(1)).first().single()
        val reminder = paivakirja.observeMuistutukset().first().single()
        val laake = paivakirja.observeLaakkeet().first().single()
        assertEquals(muistutus, otto.muistutusId)
        assertEquals(otto.muistutusId, reminder.muistutusId)
        assertEquals("A aamulääke", laake.nimi)
        assertFalse(laake.aktiivinen)
    }

    /** Adds a daily medication at 8.00 that started 10 days ago; returns its reminder id. */
    private suspend fun addWithReminder(nimi: String): Long {
        val id = laakkeet.add(
            Laake(
                nimi = nimi,
                tyyppiId = laakkeet.observeTyypit().first().first().tyyppiId,
                annosmaara = "5",
                yksikko = "mg",
                kuinkaUsein = KuinkaUsein.PAIVITTAIN,
                aloituspv = today.minusDays(10),
                lopetuspv = null
            ),
            listOf(480)
        )
        // Starts a new reminder today; move it back so the past days have doses.
        val reminder = db.muistutusDao().getByLaake(id).single()
        db.muistutusDao().setVoimassaAlkaen(reminder.muistutusId, today.minusDays(10))
        return reminder.muistutusId
    }

    private suspend fun take(muistutusId: Long, paiva: LocalDate) {
        db.laakkeenottoDao().insert(
            Laakkeenotto(
                muistutusId = muistutusId,
                paivamaara = paiva,
                ottoaika = paiva.atTime(8, 5).atZone(zone).toInstant()
            )
        )
    }
}
