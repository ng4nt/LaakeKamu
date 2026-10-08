package fi.hh.laakekamu.ui.oirepaivakirja

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fi.hh.laakekamu.LaakeKamuApp
import fi.hh.laakekamu.data.OireRepository
import fi.hh.laakekamu.data.PaivakirjaRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Oirepäiväkirja: the calendar's month and selected day, and what the diary shows for them. */
data class OirepaivakirjaUiState(
    val tanaan: LocalDate,
    /** The selected day: today until another day is tapped. */
    val valittu: LocalDate,
    /** The month in the calendar: the selected day's month until ‹ or › is tapped. */
    val kuukausi: YearMonth,
    /** The doses taken on [valittu], by time. */
    val otetut: List<OtettuRivi> = emptyList(),
    /** The days of [kuukausi] whose due doses were all taken (✓). */
    val valmiit: Set<LocalDate> = emptySet(),
    /** The symptoms active on [valittu]. */
    val oireet: List<OireRivi> = emptyList(),
    /** The days of [kuukausi] with an active symptom (•). */
    val oirePaivat: Set<LocalDate> = emptySet(),
    /** The symptom whose deletion waits for the user's confirmation (the dialog is open). */
    val poistettava: OireRivi? = null,
    /** The result of the last deletion, shown once as a snackbar; null when nothing to show. */
    val poistonTulos: PoistonTulos? = null,
    val loading: Boolean = true,
    /** The diary couldn't be read (e.g. database unavailable). */
    val loadError: Boolean = false
) {
    /** The calendar's rows, Monday first (see [kuukaudenViikot]). */
    val viikot: List<List<LocalDate?>> get() = kuukaudenViikot(kuukausi)
}

/** A deletion's result to show: a new [id] for each one, so two in a row are two messages. */
data class PoistonTulos(val id: Long, val onnistui: Boolean)

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
 * The diary's calendar and the selected day's taken doses. ‹ › change the month shown, tapping
 * a day selects it. The selected day and the month are kept in [handle] (as epoch day and
 * epoch month), so they survive process death; until a day is picked the selection is today,
 * which follows the date the screen was last started on ([refresh]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OirepaivakirjaViewModel(
    private val handle: SavedStateHandle,
    private val repository: PaivakirjaRepository,
    private val oireRepository: OireRepository,
    private val clock: Clock
) : ViewModel() {
    private val nyt = MutableStateFlow(ZonedDateTime.now(clock))
    private val refreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val poistonTulos = MutableStateFlow<PoistonTulos?>(null)
    private var poistoja = 0L

    private val valinta: Flow<Valinta> = combine(
        nyt,
        handle.getStateFlow<Long?>(KEY_VALITTU, null),
        handle.getStateFlow<Long?>(KEY_KUUKAUSI, null)
    ) { now, valittu, kuukausi -> valinta(now, valittu, kuukausi) }

    // The database's answer for a selection, tagged with it: after a tap the old day's data
    // can still arrive, and it mustn't show under the new day. Null until the first answer.
    private val tiedot: Flow<Tiedot?> = valinta
        .distinctUntilChanged()
        .flatMapLatest { v ->
            combine(
                repository.observeLaakkeet(),
                repository.observeMuistutukset(),
                repository.observeOtot(v.kuukausi.atDay(1), v.kuukausi.atEndOfMonth()),
                repository.observeOtot(v.valittu, v.valittu),
                repository.observeOireet()
            ) { laakkeet, muistutukset, kuukaudenOtot, paivanOtot, oireet ->
                Tiedot(
                    valittu = v.valittu,
                    kuukausi = v.kuukausi,
                    otetut = otetutRivit(paivanOtot, laakkeet, muistutukset),
                    valmiit = valmiitPaivat(
                        v.kuukausi,
                        v.nyt,
                        laakkeet,
                        muistutukset,
                        kuukaudenOtot
                    ),
                    oireet = oireetPaivalle(v.valittu, v.nyt.toLocalDate(), oireet, laakkeet),
                    oirePaivat = oirePaivat(v.kuukausi, v.nyt.toLocalDate(), oireet)
                )
            }
        }
        // A failed query shows the error and reads again on the screen's next start, like
        // Etusivu.
        .retryWhen { _, _ ->
            emit(VIRHE)
            refreshes.first()
            true
        }
        .onStart<Tiedot?> { emit(null) }

    val uiState: StateFlow<OirepaivakirjaUiState> = combine(
        valinta,
        tiedot,
        handle.getStateFlow<Long?>(KEY_POISTETTAVA, null),
        poistonTulos
    ) { v, data, poistettava, tulos ->
        val base = v.toUiState()
        val state = when {
            data === VIRHE -> base.copy(loading = false, loadError = true)
            data == null || data.valittu != v.valittu || data.kuukausi != v.kuukausi -> base
            else -> base.copy(
                otetut = data.otetut,
                valmiit = data.valmiit,
                oireet = data.oireet,
                oirePaivat = data.oirePaivat,
                loading = false
            )
        }
        // The dialog shows only while its symptom is on the screen.
        state.copy(
            poistettava = state.oireet.find { it.merkintaId == poistettava },
            poistonTulos = tulos
        )
    }.onEach { state ->
        // Once read, a pending symptom that isn't there (deleted elsewhere, or a failed read)
        // is forgotten, so the dialog can't come back later on its own.
        val pending = handle.get<Long>(KEY_POISTETTAVA)
        if (pending != null && state.poistettava == null && !state.loading) {
            handle[KEY_POISTETTAVA] = null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), current().toUiState())

    /** Selects [day] and shows its month. */
    fun selectDay(day: LocalDate) {
        handle[KEY_VALITTU] = day.toEpochDay()
        handle[KEY_KUUKAUSI] = null
    }

    /** ‹: shows the previous month; the selected day stays. */
    fun previousMonth() = showMonth(current().kuukausi.minusMonths(1))

    /** ›: shows the next month; the selected day stays. */
    fun nextMonth() = showMonth(current().kuukausi.plusMonths(1))

    /**
     * Reads the time again and retries after a failed read. The screen calls this on every
     * start, so today follows midnight.
     */
    fun refresh() {
        nyt.value = ZonedDateTime.now(clock)
        refreshes.tryEmit(Unit)
    }

    /** "Poista" on a symptom row: asks for confirmation first (rule 7). */
    fun requestDelete(rivi: OireRivi) {
        handle[KEY_POISTETTAVA] = rivi.merkintaId
    }

    /** "Peruuta" or dismissing the dialog: nothing is deleted. */
    fun cancelDelete() {
        handle[KEY_POISTETTAVA] = null
    }

    /** "Poista" in the dialog: deletes the symptom for good (a hard delete). */
    fun confirmDelete() {
        // Read and cleared from the handle at once: uiState updates a moment later, so a
        // second quick tap must not find the row there (a second delete).
        val id = handle.get<Long>(KEY_POISTETTAVA) ?: return
        handle[KEY_POISTETTAVA] = null
        val rivi = uiState.value.oireet.find { it.merkintaId == id } ?: return
        viewModelScope.launch {
            val onnistui = try {
                oireRepository.delete(rivi.merkinta)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            poistonTulos.value = PoistonTulos(++poistoja, onnistui)
        }
    }

    /** The screen has shown the result [id]; a newer one stays (as on Etusivu). */
    fun poistonTulosShown(id: Long) {
        poistonTulos.update { if (it?.id == id) null else it }
    }

    private fun showMonth(kuukausi: YearMonth) {
        handle[KEY_KUUKAUSI] = kuukausi.toEpochMonth()
    }

    // Read from the handle, so it's right even before the screen collects uiState.
    private fun current() = valinta(nyt.value, handle[KEY_VALITTU], handle[KEY_KUUKAUSI])

    private fun valinta(now: ZonedDateTime, valittu: Long?, kuukausi: Long?): Valinta {
        val day = valittu?.let(LocalDate::ofEpochDay) ?: now.toLocalDate()
        return Valinta(
            nyt = now,
            valittu = day,
            kuukausi = kuukausi?.let(::yearMonthOfEpochMonth) ?: YearMonth.from(day)
        )
    }

    /** The current time, the selected day and the month shown. */
    private data class Valinta(
        val nyt: ZonedDateTime,
        val valittu: LocalDate,
        val kuukausi: YearMonth
    ) {
        fun toUiState() = OirepaivakirjaUiState(nyt.toLocalDate(), valittu, kuukausi)
    }

    /** The diary's data for [valittu] and [kuukausi]. */
    private data class Tiedot(
        val valittu: LocalDate,
        val kuukausi: YearMonth,
        val otetut: List<OtettuRivi>,
        val valmiit: Set<LocalDate>,
        val oireet: List<OireRivi>,
        val oirePaivat: Set<LocalDate>
    )

    companion object {
        private const val KEY_VALITTU = "valittu"
        private const val KEY_KUUKAUSI = "kuukausi"
        private const val KEY_POISTETTAVA = "poistettava"

        /** Marks a failed read; compared by identity. */
        private val VIRHE =
            Tiedot(
                LocalDate.MIN,
                YearMonth.of(0, 1),
                emptyList(),
                emptySet(),
                emptyList(),
                emptySet()
            )

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LaakeKamuApp
                OirepaivakirjaViewModel(
                    createSavedStateHandle(),
                    app.container.paivakirjaRepository,
                    app.container.oireRepository,
                    app.container.clock
                )
            }
        }
    }
}

private val EPOCH = YearMonth.of(1970, 1)

/** Months since January 1970, so a [YearMonth] fits in a [SavedStateHandle] as a Long. */
private fun YearMonth.toEpochMonth(): Long = ChronoUnit.MONTHS.between(EPOCH, this)

private fun yearMonthOfEpochMonth(epochMonth: Long): YearMonth = EPOCH.plusMonths(epochMonth)
