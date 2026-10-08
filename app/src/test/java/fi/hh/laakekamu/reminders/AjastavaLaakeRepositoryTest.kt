package fi.hh.laakekamu.reminders

import fi.hh.laakekamu.data.FakeLaakeRepository
import fi.hh.laakekamu.data.entity.Laake
import fi.hh.laakekamu.domain.EditedTime
import fi.hh.laakekamu.domain.KuinkaUsein
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * a change always ends with a reschedule, even when the caller (the wizard's
 * viewModelScope) is cancelled after the save, while the reschedule is still running.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AjastavaLaakeRepositoryTest {
    private val fake = FakeLaakeRepository()
    private val gate = CompletableDeferred<Unit>()
    private var started = 0
    private var finished = 0
    private val forgotten = mutableListOf<Pair<List<Long>, Boolean>>()
    private val repository = AjastavaLaakeRepository(
        fake,
        forget = { ids, todayOnly -> forgotten += ids to todayOnly },
        reschedule = {
            started++
            gate.await()
            finished++
        }
    )

    @Test
    fun addReschedulesAfterTheSave() = runTest {
        gate.complete(Unit)
        repository.add(laake(), listOf(480))
        assertEquals(1, fake.added.size)
        assertEquals(1, finished)
    }

    @Test
    fun addFinishesTheRescheduleWhenTheCallerIsCancelled() = runTest(UnconfinedTestDispatcher()) {
        val caller = launch { repository.add(laake(), listOf(480)) }
        assertEquals(1, started)
        caller.cancel()
        gate.complete(Unit)
        caller.join()
        assertEquals(1, fake.added.size)
        assertEquals(1, finished)
    }

    @Test
    fun updateForgetsTodaysDosesOfTheChangedRemindersAndFinishesTheReschedule() =
        runTest(UnconfinedTestDispatcher()) {
            fake.laakkeet.value = listOf(laake())
            fake.changedOnUpdate = listOf(7, 8)
            val caller = launch {
                repository.update(laake().copy(nimi = "Uusi"), listOf(EditedTime(null, 480)))
            }
            caller.cancel()
            gate.complete(Unit)
            caller.join()
            assertEquals(listOf(listOf(7L, 8L) to true), forgotten)
            assertEquals(1, finished)
        }

    @Test
    fun deleteForgetsAndFinishesTheRescheduleWhenTheCallerIsCancelled() =
        runTest(UnconfinedTestDispatcher()) {
            fake.laakkeet.value = listOf(laake())
            val caller = launch { repository.delete(1) }
            caller.cancel()
            gate.complete(Unit)
            caller.join()
            assertEquals(listOf(1L), fake.deleted)
            assertEquals(listOf(emptyList<Long>() to false), forgotten)
            assertEquals(1, finished)
            assertTrue(caller.isCancelled)
        }

    private fun laake() = Laake(
        laakeId = 1,
        nimi = "Burana",
        tyyppiId = 1,
        annosmaara = "1",
        yksikko = "mg",
        kuinkaUsein = KuinkaUsein.PAIVITTAIN,
        aloituspv = LocalDate.of(2026, 10, 1),
        lopetuspv = null
    )
}
