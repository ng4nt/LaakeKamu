package fi.hh.laakekamu.ui.etusivu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fi.hh.laakekamu.LaakeKamuApp
import fi.hh.laakekamu.data.AnnosRepository
import fi.hh.laakekamu.data.entity.Laake
import fi.hh.laakekamu.data.entity.Muistutus
import fi.hh.laakekamu.domain.AnnoksenTila
import fi.hh.laakekamu.domain.KuinkaUsein
import fi.hh.laakekamu.domain.canToggle
import fi.hh.laakekamu.domain.isDueOn
import fi.hh.laakekamu.domain.tilaOf
import fi.hh.laakekamu.ui.format.SUOMI
import java.text.Collator
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One dose on Etusivu (reminder [muistutusId] on [paiva]): "name **dose**" and its state. */
data class AnnosRivi(
    val muistutusId: Long,
    val paiva: LocalDate,
    val nimi: String,
    val annosmaara: String,
    val yksikko: String,
    val tila: AnnoksenTila
)

/** Which icon a time group gets: sun before 11.00, moon from 17.00, none between. */
enum class Vuorokausi { AAMU, PAIVA, ILTA }

/** The doses due at [aika] (minutes after midnight). */
data class AnnosRyhma(val aika: Int, val rivit: List<AnnosRivi>) {
    val vuorokausi: Vuorokausi
        get() = when {
            aika < MORNING_END -> Vuorokausi.AAMU
            aika >= EVENING_START -> Vuorokausi.ILTA
            else -> Vuorokausi.PAIVA
        }
}

private const val MORNING_END = 11 * 60
private const val EVENING_START = 17 * 60

/** Etusivu: this week's strip, the selected day and its doses grouped by time. */
data class EtusivuUiState(
    /** Today; null until the first state has loaded. */
    val tanaan: LocalDate? = null,
    /** The current minute, for the live header "Tänään on … klo 7.00". */
    val nyt: LocalTime? = null,
    /** The selected day: today, or another day of this week picked in the strip. */
    val paiva: LocalDate? = null,
    /** This week, Monday to Sunday. */
    val viikko: List<LocalDate> = emptyList(),
    val ryhmat: List<AnnosRyhma> = emptyList(),
    /** Today and past days can be ticked; future days are read-only. */
    val muokattavissa: Boolean = false,
    val loading: Boolean = true,
    /** The doses couldn't be read (e.g. database unavailable). */
    val loadError: Boolean = false,
    /**
     * Non-zero when a tick or untick couldn't be saved: a new id for each failure, so two
     * failures in a row are two messages. The screen shows it once as a snackbar
     * and then calls [EtusivuViewModel.toggleErrorShown]; the next tap clears it too.
     */
    val toggleErrorId: Long = 0L
) {
    val isEmpty: Boolean get() = !loading && !loadError && ryhmat.isEmpty()

    /** The doses' states in the list's order: the progress dots. */
    val tilat: List<AnnoksenTila> get() = ryhmat.flatMap { ryhma -> ryhma.rivit.map { it.tila } }
}

/**
 * What Etusivu shows of [latest] when the screen last came to the foreground on [shownDay]. A
 * state made on an earlier day, kept from before a pause over midnight, shows as loading until
 * the new day's state arrives. A later day is the midnight rollover while
 * the screen is visible, so it's shown. This compares the state's today, not the selected day,
 * which may be in the past on purpose.
 */
fun shownState(latest: EtusivuUiState, shownDay: LocalDate): EtusivuUiState =
    if (latest.tanaan?.isBefore(shownDay) == true) EtusivuUiState() else latest

/** The week of [day], Monday to Sunday (Finnish weeks start on Monday). */
fun viikko(day: LocalDate): List<LocalDate> {
    val monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return (0L until DAYS_IN_WEEK).map { monday.plusDays(it) }
}

private const val DAYS_IN_WEEK = 7L

/**
 * The doses due on [paiva] at [now], grouped by time and in Finnish name order within a
 * time. A dose is due when its reminder and its medication are both valid that day; as-needed
 * medications have no current reminders. Today and later also need the medication to be active
 * and daily: a medication deleted, or switched to Tarvittaessa, after today's dose was taken
 * keeps a reminder valid until today, and this check hides it (the diary
 * keeps the taken dose). Past days ignore `aktiivinen` and the current frequency, so their
 * history stays.
 * [otetut] are the reminder ids taken on [paiva]; the state follows the dose state rules.
 */
fun etusivunRyhmat(
    paiva: LocalDate,
    now: ZonedDateTime,
    laakkeet: List<Laake>,
    muistutukset: List<Muistutus>,
    otetut: Set<Long>
): List<AnnosRyhma> {
    val today = now.toLocalDate()
    val byId = laakkeet.associateBy { it.laakeId }
    val finnish = Collator.getInstance(SUOMI)
    return muistutukset
        .mapNotNull { muistutus ->
            val laake = byId[muistutus.laakeId] ?: return@mapNotNull null
            val shownFromToday = laake.aktiivinen && laake.kuinkaUsein == KuinkaUsein.PAIVITTAIN
            if (!paiva.isBefore(today) && !shownFromToday) return@mapNotNull null
            val due = isDueOn(
                paiva,
                muistutus.voimassaAlkaen,
                muistutus.voimassaAsti,
                laake.aloituspv,
                laake.lopetuspv
            )
            if (!due) return@mapNotNull null
            val taken = muistutus.muistutusId in otetut
            muistutus.aika to AnnosRivi(
                muistutusId = muistutus.muistutusId,
                paiva = paiva,
                nimi = laake.nimi,
                annosmaara = laake.annosmaara,
                yksikko = laake.yksikko,
                tila = tilaOf(paiva, muistutus.aika, taken, now)
            )
        }
        .groupBy({ it.first }, { it.second })
        .toSortedMap()
        .map { (aika, rivit) -> AnnosRyhma(aika, rivit.sortedWith(compareBy(finnish) { it.nimi })) }
}

/**
 * Etusivu's state. "Now" ticks every minute on the minute, so the header's time is live, a dose
 * turns OTTAMATTA when its time passes and the day follows midnight. A day of this week can
 * be picked in the strip; tapping a row ticks or unticks it unless the day is in the future.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EtusivuViewModel(private val repository: AnnosRepository, private val clock: Clock) :
    ViewModel() {
    // Each refresh() restarts the ticker with a fresh time. Its delay counts uptime, which stops
    // while the phone sleeps, so after a night with the screen off the old delay could still
    // have most of a minute left.
    private val refreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val now: Flow<ZonedDateTime> = refreshes.onStart { emit(Unit) }.flatMapLatest {
        flow {
            while (true) {
                val current = ZonedDateTime.now(clock)
                emit(current)
                delay(MINUTE_MILLIS - current.second * 1_000L - current.nano / 1_000_000L)
            }
        }
    }.shareIn(
        viewModelScope,
        // Don't replay an old tick when the screen comes back (e.g. the next morning).
        SharingStarted.WhileSubscribed(replayExpirationMillis = 0),
        replay = 1
    )

    /** The day picked in the strip and the day it was picked on; null shows today. */
    private val valittu = MutableStateFlow<Valinta?>(null)

    // "Now" and the day shown. A day picked on another day gives way to today, so a new day
    // (and maybe a new week) starts on today. The pick is also cleared: after a clock moved back
    // over midnight (a westward zone change) it would come back when its day came round again
    //.
    private val nakyma = combine(now, valittu) { current, picked ->
        val today = current.toLocalDate()
        if (picked != null && picked.tanaan != today) valittu.compareAndSet(picked, null)
        Nakyma(current, viikko(today), picked?.takeIf { it.tanaan == today }?.paiva ?: today)
    }

    // The taken doses of the day shown, tagged with that day: after a tap on the strip or at
    // midnight, the old day's set can still arrive with the new day.
    private val otetut = nakyma
        .map { it.paiva }
        .distinctUntilChanged()
        .flatMapLatest { day -> repository.observeOtetut(day).map { day to it } }

    private val toggleError = MutableStateFlow(0L)
    private var failures = 0L

    val uiState: StateFlow<EtusivuUiState> = combine(
        nakyma,
        repository.observeLaakkeet(),
        repository.observeMuistutukset(),
        otetut,
        toggleError
    ) { (current, week, paiva), laakkeet, muistutukset, (day, taken), errorId ->
        // Until the new day's set arrives, keep the previous state: with another day's set its
        // doses would show as untaken for a moment.
        if (day != paiva) return@combine null
        val today = current.toLocalDate()
        EtusivuUiState(
            tanaan = today,
            nyt = current.toLocalTime().truncatedTo(ChronoUnit.MINUTES),
            paiva = paiva,
            viikko = week,
            ryhmat = etusivunRyhmat(paiva, current, laakkeet, muistutukset, taken),
            muokattavissa = canToggle(paiva, today),
            loading = false,
            toggleErrorId = errorId
        )
    }
        .filterNotNull()
        // A failed query shows the error, waits for the screen's next start (refresh()) and
        // then reads again. A catch would end the flow, and nothing would restart it while the
        // screen stays subscribed.
        .retryWhen { _, _ ->
            emit(EtusivuUiState(loading = false, loadError = true))
            refreshes.first()
            true
        }
        .stateIn(
            viewModelScope,
            // The last state stays cached during a pause, so a list from the same day is shown at
            // once and keeps its scroll position. One from an earlier day is held
            // back by the screen (shownState).
            SharingStarted.WhileSubscribed(5_000),
            EtusivuUiState()
        )

    /** Today in the injected clock's zone. */
    fun today(): LocalDate = LocalDate.now(clock)

    /**
     * Shows [day], picked in the strip built on [tanaan]. Picking today shows today again, so it
     * follows midnight; another day is shown until the date changes. [tanaan] is the strip's day,
     * not the clock's: a tap just after midnight (or a clock change) before the next tick picked
     * from the old week, which the new day then replaces.
     */
    fun selectDay(day: LocalDate, tanaan: LocalDate) {
        valittu.value = if (day == tanaan) null else Valinta(day, tanaan)
    }

    /**
     * Ticks the dose of [rivi] if the user saw it untaken, otherwise unticks it, on the row's
     * own day (a tap just after midnight still marks the day on screen). Future days are
     * read-only. The row shows the new state when the database flow emits it; a repeated tap
     * before that writes the same state again, which changes nothing.
     */
    fun toggle(rivi: AnnosRivi) {
        if (!canToggle(rivi.paiva, today())) return
        val otettu = rivi.tila != AnnoksenTila.OTETTU
        // A new tap starts without the old error; a failure gets a new id.
        toggleError.value = 0L
        viewModelScope.launch {
            try {
                repository.setOtettu(rivi.muistutusId, rivi.paiva, otettu)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toggleError.value = ++failures
            }
        }
    }

    /**
     * The screen has shown the message for [EtusivuUiState.toggleErrorId] [id]. A newer failure
     * stays, so a late call for an older message can't hide it.
     */
    fun toggleErrorShown(id: Long) {
        toggleError.compareAndSet(id, 0L)
    }

    /** Restarts the minute ticker with the current time; the screen calls this on every start. */
    fun refresh() {
        refreshes.tryEmit(Unit)
    }

    /** A day picked in the strip ([paiva]) on [tanaan]. */
    private data class Valinta(val paiva: LocalDate, val tanaan: LocalDate)

    /** The current time, this week and the day shown. */
    private data class Nakyma(
        val current: ZonedDateTime,
        val week: List<LocalDate>,
        val paiva: LocalDate
    )

    companion object {
        private const val MINUTE_MILLIS = 60_000L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LaakeKamuApp
                EtusivuViewModel(app.container.annosRepository, app.container.clock)
            }
        }
    }
}
