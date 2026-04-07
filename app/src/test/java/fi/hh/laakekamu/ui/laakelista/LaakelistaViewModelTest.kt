package fi.hh.laakekamu.ui.laakelista

import fi.hh.laakekamu.data.FakeLaakeRepository
import fi.hh.laakekamu.data.entity.Laake
import fi.hh.laakekamu.domain.Kesto
import fi.hh.laakekamu.domain.KuinkaUsein
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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

@OptIn(ExperimentalCoroutinesApi::class)
class LaakelistaViewModelTest {
    private val today = LocalDate.of(2025, 12, 18)
    private val zone = ZoneId.of("Europe/Helsinki")
    private val clock = Clock.fixed(today.atTime(10, 0).atZone(zone).toInstant(), zone)

    @Before
    fun setMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMain() {
        Dispatchers.resetMain()
    }

    // Grouping

    @Test
    fun dailyMedications_areGroupedByTime_andOneWithTwoTimesIsUnderBoth() {
        val a = laake(1, "A aamulääke")
        val b = laake(2, "B aamulääke")
        val ajat = mapOf(1L to listOf(480, 1200), 2L to listOf(480))

        val state = groupLaakelista(listOf(a, b), ajat, today)

        assertEquals(listOf(480, 1200), state.paivittain.map { it.aika })
        assertEquals(
            listOf("A aamulääke", "B aamulääke"),
            state.paivittain[0].rivit.map {
                it.nimi
            }
        )
        assertEquals(listOf("A aamulääke"), state.paivittain[1].rivit.map { it.nimi })
        assertTrue(state.tarvittaessa.isEmpty())
    }

    @Test
    fun asNeededMedications_haveTheirOwnSection_inTheGivenOrder() {
        val voide = laake(1, "Ihonsuojavoide", KuinkaUsein.TARVITTAESSA)
        val burana = laake(2, "Burana", KuinkaUsein.TARVITTAESSA)

        val state = groupLaakelista(listOf(burana, voide), emptyMap(), today)

        assertTrue(state.paivittain.isEmpty())
        assertEquals(listOf("Burana", "Ihonsuojavoide"), state.tarvittaessa.map { it.nimi })
    }

    @Test
    fun eachLine_hasItsL6Label_andEndedOnesAreMarked() {
        val running = laake(1, "A", end = today.plusDays(13))
        val ended = laake(2, "B", end = today.minusDays(1))
        val continuous = laake(3, "C", KuinkaUsein.TARVITTAESSA)

        val state = groupLaakelista(
            listOf(running, ended, continuous),
            mapOf(1L to listOf(480), 2L to listOf(480)),
            today
        )

        val rivit = state.paivittain.single().rivit
        assertEquals(Kesto.Jaljella(14), rivit[0].kesto)
        assertFalse(rivit[0].paattynyt)
        assertTrue(rivit[1].paattynyt)
        assertEquals(Kesto.Kaytossa(11), state.tarvittaessa.single().kesto)
    }

    @Test
    fun noMedications_isTheEmptyState() {
        val state = groupLaakelista(emptyList(), emptyMap(), today)
        assertTrue(state.isEmpty)
    }

    // ViewModel

    @Test
    fun theListFollowsTheRepository() = runTest {
        val repository = FakeLaakeRepository()
        val vm = LaakelistaViewModel(repository, clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertTrue(vm.uiState.value.isEmpty)

        repository.add(laake(0, "A aamulääke"), listOf(1200, 480))

        val state = vm.uiState.value
        assertEquals(listOf(480, 1200), state.paivittain.map { it.aika })
        assertEquals("5", state.paivittain[0].rivit.single().annosmaara)
    }

    @Test
    fun aFailedQuery_isAnErrorState_notAnEmptyList() = runTest {
        val vm = LaakelistaViewModel(FakeLaakeRepository(failChoices = true), clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        assertTrue(vm.uiState.value.loadError)
        assertFalse(vm.uiState.value.isEmpty && !vm.uiState.value.loadError)
    }

    private fun laake(
        id: Long,
        nimi: String,
        kuinkaUsein: KuinkaUsein = KuinkaUsein.PAIVITTAIN,
        end: LocalDate? = null
    ) = Laake(
        laakeId = id,
        nimi = nimi,
        tyyppiId = 1,
        annosmaara = "5",
        yksikko = "mg",
        kuinkaUsein = kuinkaUsein,
        aloituspv = today.minusDays(10),
        lopetuspv = end
    )
}
