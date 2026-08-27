package fi.hh.laakekamu.reminders

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.hh.laakekamu.data.AppDatabase
import fi.hh.laakekamu.data.RoomLaakeRepository
import fi.hh.laakekamu.data.SeedCallback
import fi.hh.laakekamu.data.entity.Laake
import fi.hh.laakekamu.domain.EditedTime
import fi.hh.laakekamu.domain.KuinkaUsein
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * deleting a medication cancels the snooze of its dose, so the phone
 * isn't woken for a medication that is gone. An edit does the same for a time it removes.
 */
@RunWith(AndroidJUnit4::class)
class PoistettuLaakeTest {
    // The real clock: the snooze is set 10 minutes from now and must not fire during the test.
    private val clock = Clock.systemDefaultZone()

    private lateinit var db: AppDatabase
    private lateinit var scheduler: MuistutusScheduler
    private lateinit var laakkeet: AjastavaLaakeRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).addCallback(SeedCallback).build()
        scheduler = MuistutusScheduler(
            ApplicationProvider.getApplicationContext(),
            { db },
            clock,
            Dispatchers.IO
        )
        // No reschedule: these tests don't touch the app's slot alarms.
        laakkeet = AjastavaLaakeRepository(RoomLaakeRepository({ db }, clock), scheduler::forget) {}
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun laake() = Laake(
        nimi = "A aamulääke",
        tyyppiId = laakkeet.observeTyypit().first().first().tyyppiId,
        annosmaara = "5",
        yksikko = "mg",
        kuinkaUsein = KuinkaUsein.PAIVITTAIN,
        aloituspv = LocalDate.now(clock),
        lopetuspv = null
    )

    @Test
    fun delete_cancelsTheSnooze() = runBlocking {
        val laakeId = laakkeet.add(laake(), listOf(8 * 60))
        val muistutusId = db.muistutusDao().getByLaake(laakeId).single().muistutusId
        scheduler.snooze(muistutusId, LocalDate.now(clock))
        assertTrue(scheduler.hasSnooze(muistutusId))

        laakkeet.delete(laakeId)

        assertFalse(scheduler.hasSnooze(muistutusId))
    }

    @Test
    fun edit_removingATime_cancelsItsSnooze() = runBlocking {
        val laakeId = laakkeet.add(laake(), listOf(8 * 60, 20 * 60))
        val rows = db.muistutusDao().getByLaake(laakeId).associateBy { it.aika }
        val kept = rows.getValue(8 * 60).muistutusId
        val removed = rows.getValue(20 * 60).muistutusId
        scheduler.snooze(kept, LocalDate.now(clock))
        scheduler.snooze(removed, LocalDate.now(clock))

        laakkeet.update(laake().copy(laakeId = laakeId), listOf(EditedTime(kept, 8 * 60)))

        assertTrue(scheduler.hasSnooze(kept))
        assertFalse(scheduler.hasSnooze(removed))
        scheduler.forget(listOf(kept))
    }
}
