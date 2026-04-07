package fi.hh.laakekamu.ui.laakelista

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fi.hh.laakekamu.LaakeKamuApp
import fi.hh.laakekamu.data.LaakeRepository
import fi.hh.laakekamu.data.entity.Laake
import fi.hh.laakekamu.domain.Kesto
import fi.hh.laakekamu.domain.KuinkaUsein
import fi.hh.laakekamu.domain.kestoOf
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** One medication line: "name **dose**" and its label. */
data class ListaRivi(
    val laakeId: Long,
    val nimi: String,
    val annosmaara: String,
    val yksikko: String,
    val kesto: Kesto
) {
    /** Ended medications are greyed out. */
    val paattynyt: Boolean get() = kesto == Kesto.Paattynyt
}

/** The daily medications taken at [aika] (minutes after midnight). */
data class AikaRyhma(val aika: Int, val rivit: List<ListaRivi>)

/** Lääkelista: "OTA PÄIVITTÄIN" by time, then "TARVITTAESSA". */
data class LaakelistaUiState(
    val paivittain: List<AikaRyhma> = emptyList(),
    val tarvittaessa: List<ListaRivi> = emptyList(),
    val loading: Boolean = true,
    /** The list couldn't be read (e.g. database unavailable). */
    val loadError: Boolean = false
) {
    val isEmpty: Boolean get() = !loading && paivittain.isEmpty() && tarvittaessa.isEmpty()
}

/**
 * Daily medications under each of their current times (a medication with several
 * times appears under each), as-needed ones in their own section, each with its label on
 * [today]. The order of [laakkeet] (Finnish alphabetical) is kept within a group.
 */
fun groupLaakelista(
    laakkeet: List<Laake>,
    ajat: Map<Long, List<Int>>,
    today: LocalDate
): LaakelistaUiState {
    fun rivi(laake: Laake) = ListaRivi(
        laakeId = laake.laakeId,
        nimi = laake.nimi,
        annosmaara = laake.annosmaara,
        yksikko = laake.yksikko,
        kesto = kestoOf(laake.aloituspv, laake.lopetuspv, today)
    )
    val paivittain = laakkeet
        .filter { it.kuinkaUsein == KuinkaUsein.PAIVITTAIN }
        .flatMap { laake -> ajat[laake.laakeId].orEmpty().map { aika -> aika to laake } }
        .groupBy({ it.first }, { rivi(it.second) })
        .toSortedMap()
        .map { (aika, rivit) -> AikaRyhma(aika, rivit) }
    val tarvittaessa = laakkeet.filter { it.kuinkaUsein == KuinkaUsein.TARVITTAESSA }.map(::rivi)
    return LaakelistaUiState(paivittain, tarvittaessa, loading = false)
}

/** Lääkelista's state from the active medications and their current times. */
class LaakelistaViewModel(repository: LaakeRepository, private val clock: Clock) : ViewModel() {
    val uiState: StateFlow<LaakelistaUiState> = combine(
        repository.observeActive(),
        repository.observeCurrentTimes()
    ) { laakkeet, ajat ->
        groupLaakelista(laakkeet, ajat, LocalDate.now(clock))
    }
        // An error state instead of an ended collection.
        .catch { emit(LaakelistaUiState(loading = false, loadError = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LaakelistaUiState())

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LaakeKamuApp
                LaakelistaViewModel(app.container.laakeRepository, app.container.clock)
            }
        }
    }
}
