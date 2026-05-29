package fi.hh.laakekamu.ui.oirepaivakirja

import androidx.lifecycle.SavedStateHandle
import fi.hh.laakekamu.data.PaivakirjaRepository
import fi.hh.laakekamu.data.entity.Laake
import fi.hh.laakekamu.data.entity.Laakkeenotto
import fi.hh.laakekamu.data.entity.Muistutus
import fi.hh.laakekamu.data.entity.Oiremerkinta
import fi.hh.laakekamu.domain.KuinkaUsein
import fi.hh.laakekamu.ui.format.formatWeekdayShort
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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
class OirepaivakirjaViewModelTest {
    private val helsinki = ZoneId.of("Europe/Helsinki")
    private val today = LocalDate.of(2025, 12, 18)
    private val yesterday = today.minusDays(1)

    @Before
    fun setMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMain() {
        Dispatchers.resetMain()
    }

    // The month grid (Monday first)

    @Test
    fun december2025_startsOnMonday_fiveRows() {
        val viikot = kuukaudenViikot(YearMonth.of(2025, 12))

        assertEquals(5, viikot.size)
        assertEquals(LocalDate.of(2025, 12, 1), viikot[0][0])
        assertEquals(LocalDate.of(2025, 12, 18), viikot[2][3])
        assertEquals(listOf(29, 30, 31), viikot[4].filterNotNull().map { it.dayOfMonth })
        assertTrue(viikot.all { it.size == 7 })
    }

    @Test
    fun monthStartingOnSunday_hasSixRows_andLeadingBlanks() {
        // 1.3.2026 is a Sunday.
        val viikot = kuukaudenViikot(YearMonth.of(2026, 3))

        assertEquals(6, viikot.size)
        assertEquals(List(6) { null }, viikot[0].take(6))
        assertEquals(LocalDate.of(2026, 3, 1), viikot[0][6])
        assertEquals(
            listOf(LocalDate.of(2026, 3, 30), LocalDate.of(2026, 3, 31)),
            viikot[5].take(2)
        )
    }

    @Test
    fun februaryStartingOnMonday_hasFourRows() {
        val viikot = kuukaudenViikot(YearMonth.of(2021, 2))

        assertEquals(4, viikot.size)
        assertEquals(28, viikot.flatten().filterNotNull().size)
    }

    @Test
    fun weekdayHeaders_areFinnish_mondayFirst() {
        val monday = LocalDate.of(2024, 1, 1)

        val headers = (0L until 7L).map { formatWeekdayShort(monday.plusDays(it)) }

        assertEquals(listOf("ma", "ti", "ke", "to", "pe", "la", "su"), headers)
    }

    // Selection and month navigation

    @Test
    fun start_selectsToday_andShowsItsMonth() = runTest {
        val state = collecting(viewModel()).uiState.value

        assertEquals(today, state.valittu)
        assertEquals(today, state.tanaan)
        assertEquals(YearMonth.of(2025, 12), state.kuukausi)
    }

    @Test
    fun arrows_changeTheMonth_acrossYears_keepingTheSelection() = runTest {
        val vm = collecting(viewModel())

        vm.nextMonth()
        assertEquals(YearMonth.of(2026, 1), vm.uiState.value.kuukausi)
        vm.previousMonth()
        vm.previousMonth()

        assertEquals(YearMonth.of(2025, 11), vm.uiState.value.kuukausi)
        assertEquals(today, vm.uiState.value.valittu)
    }

    @Test
    fun selectingADay_showsItsMonth() = runTest {
        val vm = collecting(viewModel())
        vm.nextMonth()
        vm.nextMonth()

        vm.selectDay(LocalDate.of(2026, 1, 5))

        assertEquals(LocalDate.of(2026, 1, 5), vm.uiState.value.valittu)
        assertEquals(YearMonth.of(2026, 1), vm.uiState.value.kuukausi)
    }

    @Test
    fun selectionAndMonth_surviveProcessDeath() = runTest {
        val handle = SavedStateHandle()
        val vm = viewModel(handle = handle)
        vm.selectDay(LocalDate.of(2025, 11, 3))
        vm.nextMonth()

        val saved = handle.keys().associateWith { handle.get<Any?>(it) }
        val restored = collecting(viewModel(handle = SavedStateHandle(saved)))

        assertEquals(LocalDate.of(2025, 11, 3), restored.uiState.value.valittu)
        assertEquals(YearMonth.of(2025, 12), restored.uiState.value.kuukausi)
    }

    @Test
    fun refresh_afterMidnight_movesTheDefaultSelectionToTheNewDay() = runTest {
        val clock = MutableClock(today)
        val vm = collecting(viewModel(clock = clock))
        assertEquals(today, vm.uiState.value.valittu)

        clock.day = LocalDate.of(2026, 1, 1)
        vm.refresh()

        val state = vm.uiState.value
        assertEquals(LocalDate.of(2026, 1, 1), state.valittu)
        assertEquals(YearMonth.of(2026, 1), state.kuukausi)
    }

    @Test
    fun refresh_keepsAPickedDay() = runTest {
        val clock = MutableClock(today)
        val vm = collecting(viewModel(clock = clock))
        vm.selectDay(LocalDate.of(2025, 12, 1))

        clock.day = today.plusDays(1)
        vm.refresh()

        assertEquals(LocalDate.of(2025, 12, 1), vm.uiState.value.valittu)
        assertEquals(today.plusDays(1), vm.uiState.value.tanaan)
    }

    // The diary's data in the state

    @Test
    fun state_showsTheSelectedDaysDoses_andTheMonthsChecks() = runTest {
        val repo = FakeRepository(
            laakkeet = listOf(laake(1, "A aamulääke")),
            muistutukset = listOf(muistutus(1, 1, 480)),
            otot = listOf(otto(1, yesterday), otto(1, today))
        )
        val vm = collecting(viewModel(repo))

        val state = vm.uiState.value
        assertFalse(state.loading)
        assertEquals(listOf("A aamulääke"), state.otetut.map { it.nimi })
        assertEquals(setOf(yesterday, today), state.valmiit)
    }

    @Test
    fun selectingAnotherDay_showsItsDoses_andATickUpdatesTheCheck() = runTest {
        val repo = FakeRepository(
            laakkeet = listOf(laake(1, "A aamulääke")),
            muistutukset = listOf(muistutus(1, 1, 480)),
            otot = listOf(otto(1, yesterday))
        )
        val vm = collecting(viewModel(repo))

        vm.selectDay(today.minusDays(2))
        assertEquals(emptyList<OtettuRivi>(), vm.uiState.value.otetut)

        repo.otot.value += otto(1, today)
        assertEquals(setOf(yesterday, today), vm.uiState.value.valmiit)
    }

    @Test
    fun previousMonth_readsThatMonthsChecks() = runTest {
        val repo = FakeRepository(
            laakkeet = listOf(laake(1, "A")),
            muistutukset = listOf(muistutus(1, 1, 480)),
            otot = listOf(otto(1, yesterday))
        )
        val vm = collecting(viewModel(repo))

        vm.previousMonth()

        // November: every day is before the medication started (8.12.).
        assertEquals(emptySet<LocalDate>(), vm.uiState.value.valmiit)
        assertEquals(YearMonth.of(2025, 11), repo.lastMonthRead)
    }

    @Test
    fun aNewSymptom_showsOnItsDays_andInTheSelectedDaysList() = runTest {
        val repo = FakeRepository(laakkeet = listOf(laake(1, "A aamulääke")))
        val vm = collecting(viewModel(repo))

        repo.oireet.value = listOf(Oiremerkinta(1, "Huimaus", 1, yesterday, today))

        val state = vm.uiState.value
        assertEquals(listOf(OireRivi(1, "Huimaus", "A aamulääke")), state.oireet)
        assertEquals(setOf(yesterday, today), state.oirePaivat)
    }

    @Test
    fun aFailedRead_showsTheError_andRefreshReadsAgain() = runTest {
        val repo = FakeRepository(failures = 1)
        val vm = collecting(viewModel(repo))
        assertTrue(vm.uiState.value.loadError)

        vm.refresh()

        assertFalse(vm.uiState.value.loadError)
        assertFalse(vm.uiState.value.loading)
    }

    /** Keeps uiState subscribed for the rest of the test, as the screen does. */
    private fun TestScope.collecting(vm: OirepaivakirjaViewModel) = vm.also {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.uiState.collect {} }
    }

    private fun viewModel(
        repository: PaivakirjaRepository = FakeRepository(),
        handle: SavedStateHandle = SavedStateHandle(),
        clock: Clock = MutableClock(today)
    ) = OirepaivakirjaViewModel(handle, repository, clock)

    private fun laake(
        id: Long,
        nimi: String,
        kuinkaUsein: KuinkaUsein = KuinkaUsein.PAIVITTAIN,
        start: LocalDate = today.minusDays(10)
    ) = Laake(
        laakeId = id,
        nimi = nimi,
        tyyppiId = 1,
        annosmaara = "5",
        yksikko = "mg",
        kuinkaUsein = kuinkaUsein,
        aloituspv = start,
        lopetuspv = null
    )

    private fun muistutus(
        id: Long,
        laakeId: Long,
        aika: Int,
        alkaen: LocalDate = today.minusDays(10),
        asti: LocalDate? = null
    ) = Muistutus(
        muistutusId = id,
        laakeId = laakeId,
        aika = aika,
        voimassaAlkaen = alkaen,
        voimassaAsti = asti
    )

    private fun otto(muistutusId: Long, paiva: LocalDate) = Laakkeenotto(
        muistutusId = muistutusId,
        paivamaara = paiva,
        ottoaika = paiva.atTime(21, 0).atZone(helsinki).toInstant()
    )

    /** The diary's data in memory; the first [failures] reads of the medications fail. */
    private class FakeRepository(
        laakkeet: List<Laake> = emptyList(),
        muistutukset: List<Muistutus> = emptyList(),
        otot: List<Laakkeenotto> = emptyList(),
        private var failures: Int = 0
    ) : PaivakirjaRepository {
        val laakkeet = MutableStateFlow(laakkeet)
        val muistutukset = MutableStateFlow(muistutukset)
        val otot = MutableStateFlow(otot)
        val oireet = MutableStateFlow(emptyList<Oiremerkinta>())
        var lastMonthRead: YearMonth? = null

        override fun observeLaakkeet(): Flow<List<Laake>> = flow {
            if (failures > 0) {
                failures--
                error("database unavailable")
            }
            laakkeet.collect { emit(it) }
        }

        override fun observeMuistutukset(): Flow<List<Muistutus>> = muistutukset

        override fun observeOireet(): Flow<List<Oiremerkinta>> = oireet

        override fun observeOtot(alku: LocalDate, loppu: LocalDate): Flow<List<Laakkeenotto>> {
            if (alku != loppu) lastMonthRead = YearMonth.from(alku)
            return otot.map { rows -> rows.filter { it.paivamaara in alku..loppu } }
        }
    }

    /** A clock at noon of [day] that a test can move to another day. */
    private inner class MutableClock(var day: LocalDate) : Clock() {
        override fun getZone(): ZoneId = helsinki
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = day.atTime(12, 0).atZone(helsinki).toInstant()
    }
}
