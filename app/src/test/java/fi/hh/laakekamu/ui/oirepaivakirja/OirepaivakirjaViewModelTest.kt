package fi.hh.laakekamu.ui.oirepaivakirja

import androidx.lifecycle.SavedStateHandle
import fi.hh.laakekamu.ui.format.formatWeekdayShort
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OirepaivakirjaViewModelTest {
    private val helsinki = ZoneId.of("Europe/Helsinki")
    private val today = LocalDate.of(2025, 12, 18)

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
        val vm = viewModel(handle)
        vm.selectDay(LocalDate.of(2025, 11, 3))
        vm.nextMonth()

        val saved = handle.keys().associateWith { handle.get<Any?>(it) }
        val restored = collecting(viewModel(SavedStateHandle(saved)))

        assertEquals(LocalDate.of(2025, 11, 3), restored.uiState.value.valittu)
        assertEquals(YearMonth.of(2025, 12), restored.uiState.value.kuukausi)
    }

    @Test
    fun refresh_afterMidnight_movesTheDefaultSelectionToTheNewDay() = runTest {
        val clock = MutableClock(today)
        val vm = collecting(OirepaivakirjaViewModel(SavedStateHandle(), clock))
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
        val vm = collecting(OirepaivakirjaViewModel(SavedStateHandle(), clock))
        vm.selectDay(LocalDate.of(2025, 12, 1))

        clock.day = today.plusDays(1)
        vm.refresh()

        assertEquals(LocalDate.of(2025, 12, 1), vm.uiState.value.valittu)
        assertEquals(today.plusDays(1), vm.uiState.value.tanaan)
    }

    /** Keeps uiState subscribed for the rest of the test, as the screen does. */
    private fun TestScope.collecting(vm: OirepaivakirjaViewModel) = vm.also {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.uiState.collect {} }
    }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        OirepaivakirjaViewModel(handle, MutableClock(today))

    /** A clock at noon of [day] that a test can move to another day. */
    private inner class MutableClock(var day: LocalDate) : Clock() {
        override fun getZone(): ZoneId = helsinki
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = day.atTime(12, 0).atZone(helsinki).toInstant()
    }
}
