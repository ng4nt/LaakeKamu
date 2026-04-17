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
import fi.hh.laakekamu.domain.isDueOn
import fi.hh.laakekamu.domain.tilaOf
import fi.hh.laakekamu.ui.format.SUOMI
import java.text.Collator
import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn

/** One dose on Etusivu: "name **dose**" and its state. */
data class AnnosRivi(
    val muistutusId: Long,
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

/** Etusivu: the selected day's doses, grouped by time. */
data class EtusivuUiState(
    val paiva: LocalDate? = null,
    val ryhmat: List<AnnosRyhma> = emptyList(),
    val loading: Boolean = true,
    /** The doses couldn't be read (e.g. database unavailable). */
    val loadError: Boolean = false
) {
    val isEmpty: Boolean get() = !loading && !loadError && ryhmat.isEmpty()
}

/**
 * The doses due on [paiva] at [now], grouped by time and in Finnish name order within a
 * time. A dose is due when its reminder and its medication are both valid that day; as-needed
 * medications have no reminders, so they never show. Today and later also need the medication to
 * be active and daily: a medication deleted, or switched to Tarvittaessa, after today's dose was
 * taken keeps a reminder valid until today, and still isn't shown (the diary
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
 * Etusivu's state for today. "Now" ticks every minute on the minute, so a dose turns OTTAMATTA
 * when its time passes and the day follows midnight.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EtusivuViewModel(repository: AnnosRepository, clock: Clock) : ViewModel() {
    private val now: Flow<ZonedDateTime> = flow {
        while (true) {
            val current = ZonedDateTime.now(clock)
            emit(current)
            delay(MINUTE_MILLIS - current.second * 1_000L - current.nano / 1_000_000L)
        }
    }.shareIn(
        viewModelScope,
        // Don't replay an old tick when the screen comes back (e.g. the next morning).
        SharingStarted.WhileSubscribed(replayExpirationMillis = 0),
        replay = 1
    )

    // The taken doses of the day "now" is on; tagged with that day, because just after midnight
    // the old day's set can still arrive with the new time.
    private val otetut = now
        .map { it.toLocalDate() }
        .distinctUntilChanged()
        .flatMapLatest { day -> repository.observeOtetut(day).map { day to it } }

    val uiState: StateFlow<EtusivuUiState> = combine(
        now,
        repository.observeLaakkeet(),
        repository.observeMuistutukset(),
        otetut
    ) { current, laakkeet, muistutukset, (day, taken) ->
        val paiva = current.toLocalDate()
        val otetutTanaan = if (day == paiva) taken else emptySet()
        EtusivuUiState(
            paiva = paiva,
            ryhmat = etusivunRyhmat(paiva, current, laakkeet, muistutukset, otetutTanaan),
            loading = false
        )
    }
        // An error state instead of an ended collection.
        .catch { emit(EtusivuUiState(loading = false, loadError = true)) }
        .stateIn(
            viewModelScope,
            // Back after a pause: start from loading, not from a list of another day.
            SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = 0),
            EtusivuUiState()
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
