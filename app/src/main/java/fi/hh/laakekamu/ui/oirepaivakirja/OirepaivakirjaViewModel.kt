package fi.hh.laakekamu.ui.oirepaivakirja

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fi.hh.laakekamu.LaakeKamuApp
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Oirepäiväkirja: the month shown in the calendar and the selected day. */
data class OirepaivakirjaUiState(
    val tanaan: LocalDate,
    /** The selected day: today until another day is tapped. */
    val valittu: LocalDate,
    /** The month in the calendar: the selected day's month until ‹ or › is tapped. */
    val kuukausi: YearMonth
) {
    /** The calendar's rows, Monday first (see [kuukaudenViikot]). */
    val viikot: List<List<LocalDate?>> get() = kuukaudenViikot(kuukausi)
}

/**
 * The weeks of [kuukausi] as calendar rows, Monday to Sunday (Finnish weeks start on Monday).
 * Days outside the month are null, so every row has 7 cells; a month has 4–6 rows.
 */
fun kuukaudenViikot(kuukausi: YearMonth): List<List<LocalDate?>> {
    val first = kuukausi.atDay(1)
    val leading = first.dayOfWeek.value - DayOfWeek.MONDAY.value
    val cells = List(leading) { null } + (1..kuukausi.lengthOfMonth()).map { kuukausi.atDay(it) }
    return cells.chunked(DAYS_IN_WEEK) { week -> week + List(DAYS_IN_WEEK - week.size) { null } }
}

private const val DAYS_IN_WEEK = 7

/**
 * The diary's calendar: ‹ › change the month shown, tapping a day selects it. The selected day
 * and the month are kept in [handle] (as epoch day and epoch month), so they survive process
 * death; until a day is picked the selection is today, which follows the date the screen was
 * last started on ([refresh]).
 */
class OirepaivakirjaViewModel(private val handle: SavedStateHandle, private val clock: Clock) :
    ViewModel() {
    private val tanaan = MutableStateFlow(LocalDate.now(clock))

    val uiState: StateFlow<OirepaivakirjaUiState> = combine(
        tanaan,
        handle.getStateFlow<Long?>(KEY_VALITTU, null),
        handle.getStateFlow<Long?>(KEY_KUUKAUSI, null)
    ) { today, valittu, kuukausi -> state(today, valittu, kuukausi) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            current()
        )

    /** Selects [day] and shows its month. */
    fun selectDay(day: LocalDate) {
        handle[KEY_VALITTU] = day.toEpochDay()
        handle[KEY_KUUKAUSI] = null
    }

    /** ‹: shows the previous month; the selected day stays. */
    fun previousMonth() = showMonth(current().kuukausi.minusMonths(1))

    /** ›: shows the next month; the selected day stays. */
    fun nextMonth() = showMonth(current().kuukausi.plusMonths(1))

    /** Reads today again; the screen calls this on every start, so today follows midnight. */
    fun refresh() {
        tanaan.value = LocalDate.now(clock)
    }

    private fun showMonth(kuukausi: YearMonth) {
        handle[KEY_KUUKAUSI] = kuukausi.toEpochMonth()
    }

    // Read from the handle, so it's right even before the screen collects uiState.
    private fun current() = state(tanaan.value, handle[KEY_VALITTU], handle[KEY_KUUKAUSI])

    private fun state(today: LocalDate, valittu: Long?, kuukausi: Long?): OirepaivakirjaUiState {
        val day = valittu?.let(LocalDate::ofEpochDay) ?: today
        return OirepaivakirjaUiState(
            tanaan = today,
            valittu = day,
            kuukausi = kuukausi?.let(::yearMonthOfEpochMonth) ?: YearMonth.from(day)
        )
    }

    companion object {
        private const val KEY_VALITTU = "valittu"
        private const val KEY_KUUKAUSI = "kuukausi"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LaakeKamuApp
                OirepaivakirjaViewModel(createSavedStateHandle(), app.container.clock)
            }
        }
    }
}

private val EPOCH = YearMonth.of(1970, 1)

/** Months since January 1970, so a [YearMonth] fits in a [SavedStateHandle] as a Long. */
private fun YearMonth.toEpochMonth(): Long = ChronoUnit.MONTHS.between(EPOCH, this)

private fun yearMonthOfEpochMonth(epochMonth: Long): YearMonth = EPOCH.plusMonths(epochMonth)
