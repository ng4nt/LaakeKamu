package fi.hh.laakekamu.ui.tietokanta

import fi.hh.laakekamu.data.TietokannanTila
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** opening at start, retry, and a start-over that runs only once. */
@OptIn(ExperimentalCoroutinesApi::class)
class TietokantaViewModelTest {
    private val tila = MutableStateFlow(TietokannanTila.AVAAMATTA)
    private var opens = 0
    private var startOvers = 0

    @Before
    fun setMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMain() {
        Dispatchers.resetMain()
    }

    @Test
    fun theDatabaseIsOpenedAtStart_andAFailureKeepsTheErrorScreenUntilARetryWorks() = runTest {
        var fails = true
        val vm = TietokantaViewModel(
            tila,
            avaa = {
                opens++
                if (fails) {
                    tila.value = TietokannanTila.EI_SAATAVILLA
                    throw IllegalStateException("no key")
                }
                tila.value = TietokannanTila.AUKI
            },
            aloitaAlusta = { startOvers++ }
        )
        assertEquals(1, opens)
        assertEquals(TietokannanTila.EI_SAATAVILLA, vm.tila.value)
        assertFalse(vm.kaynnissa.value)

        fails = false
        vm.yritaUudelleen()
        assertEquals(2, opens)
        assertEquals(TietokannanTila.AUKI, vm.tila.value)
        assertEquals(0, startOvers)
    }

    @Test
    fun aSecondTapWhileStartingOver_doesNothing() = runTest {
        val done = CompletableDeferred<Unit>()
        val vm = TietokantaViewModel(
            tila,
            avaa = { opens++ },
            aloitaAlusta = {
                startOvers++
                done.await()
            }
        )
        vm.aloitaAlusta()
        assertTrue(vm.kaynnissa.value)
        vm.aloitaAlusta()
        vm.yritaUudelleen()
        assertEquals(1, startOvers)
        assertEquals(1, opens)

        done.complete(Unit)
        assertFalse(vm.kaynnissa.value)
    }
}
