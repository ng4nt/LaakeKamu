package fi.hh.laakekamu.ui.lisaaoire

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fi.hh.laakekamu.LaakeKamuApp
import fi.hh.laakekamu.data.OireRepository
import fi.hh.laakekamu.data.entity.Laake
import fi.hh.laakekamu.data.entity.Oiremerkinta
import fi.hh.laakekamu.domain.isValidDateRange
import fi.hh.laakekamu.ui.format.SUOMI
import java.text.Collator
import java.time.Clock
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** "Ei sivuoire" in the side effect dropdown; medication ids start from 1. */
const val EI_SIVUOIRE = 0L

/** Everything the symptom form shows. */
data class LisaaOireUiState(
    /** The active medications a symptom can be linked to, in Finnish name order. */
    val laakkeet: List<Laake> = emptyList(),
    /** The picked side effect: a medication id, [EI_SIVUOIRE], or null before any pick. */
    val sivuoire: Long? = null,
    val aloituspv: LocalDate = LocalDate.MIN,
    val lopetuspv: LocalDate? = null,
    val kuvausPuuttuu: Boolean = false,
    val lopetusEnnenAloitusta: Boolean = false,
    /** The medications couldn't be loaded; the form still works without a link. */
    val loadError: Boolean = false,
    val saving: Boolean = false,
    /** The last save failed (e.g. the database). */
    val saveError: Boolean = false,
    /** The entry is saved: the screen leaves (also after a restore). */
    val saved: Boolean = false
) {
    /** The dropdown's options: "Ei sivuoire" first, then the medications. */
    val sivuoireet: List<Long> get() = listOf(EI_SIVUOIRE) + laakkeet.map { it.laakeId }
}

/**
 * The symptom form. Every field lives in the [SavedStateHandle], so the form survives
 * process death; the start date defaults to the diary's selected day ([ARG_ALKAA], an
 * epoch day) or today. The description is required, the end date is optional and not before
 * the start (checked as the dates change and on "Valmis!", which then saves the entry).
 */
class LisaaOireViewModel(
    private val handle: SavedStateHandle,
    private val repository: OireRepository,
    private val clock: Clock
) : ViewModel() {
    private val saveState = MutableStateFlow(SaveState())
    private val kuvausPuuttuu = MutableStateFlow(false)
    private var saveJob: Job? = null

    /** The typed text is Compose state, so the field gets it in the same frame. */
    var kuvaus by mutableStateOf(handle[KEY_KUVAUS] ?: "")
        private set

    private val laakkeet = repository.observeAktiivisetLaakkeet()
        .map { list ->
            Result.success(list.sortedWith(compareBy(Collator.getInstance(SUOMI)) { it.nimi }))
        }
        .catch { emit(Result.failure(it)) }

    private val fields = combine(
        handle.getStateFlow<Long?>(KEY_SIVUOIRE, null),
        handle.getStateFlow<Long?>(KEY_ALOITUSPV, null),
        handle.getStateFlow<Long?>(KEY_LOPETUSPV, null),
        handle.getStateFlow(KEY_SAVED, false)
    ) { sivuoire, aloituspv, lopetuspv, saved ->
        LisaaOireUiState(
            sivuoire = sivuoire,
            aloituspv = aloituspv(aloituspv),
            lopetuspv = lopetuspv?.let(LocalDate::ofEpochDay),
            saved = saved
        )
    }

    val uiState: StateFlow<LisaaOireUiState> = combine(
        fields,
        laakkeet,
        kuvausPuuttuu,
        saveState
    ) { state, list, missing, save ->
        state.copy(
            laakkeet = list.getOrDefault(emptyList()),
            loadError = list.isFailure,
            kuvausPuuttuu = missing,
            lopetusEnnenAloitusta = !isValidDateRange(state.aloituspv, state.lopetuspv),
            saving = save.saving,
            saveError = save.failed
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        LisaaOireUiState(aloituspv = aloituspv())
    )

    fun onKuvausChange(value: String) {
        kuvaus = value
        handle[KEY_KUVAUS] = value
        if (value.isNotBlank()) kuvausPuuttuu.value = false
    }

    /** [laakeId] is a medication id or [EI_SIVUOIRE]. */
    fun onSivuoireSelect(laakeId: Long) {
        handle[KEY_SIVUOIRE] = laakeId
    }

    fun onAloituspvChange(date: LocalDate) {
        handle[KEY_ALOITUSPV] = date.toEpochDay()
    }

    fun onLopetuspvChange(date: LocalDate?) {
        handle[KEY_LOPETUSPV] = date?.toEpochDay()
    }

    /** "Valmis!": validates and saves the entry once; a failure can be retried. */
    fun onDone() {
        if (saveJob?.isActive == true || handle.get<Boolean>(KEY_SAVED) == true) return
        val aloituspv = aloituspv()
        val lopetuspv = handle.get<Long>(KEY_LOPETUSPV)?.let(LocalDate::ofEpochDay)
        kuvausPuuttuu.value = kuvaus.isBlank()
        if (kuvaus.isBlank() || !isValidDateRange(aloituspv, lopetuspv)) return
        val merkinta = Oiremerkinta(
            kuvaus = kuvaus.trim(),
            laakeId = handle.get<Long>(KEY_SIVUOIRE)?.takeIf { it != EI_SIVUOIRE },
            aloituspv = aloituspv,
            lopetuspv = lopetuspv
        )
        saveJob = viewModelScope.launch {
            saveState.value = SaveState(saving = true)
            try {
                repository.add(merkinta)
                handle[KEY_SAVED] = true
                saveState.value = SaveState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                saveState.value = SaveState(failed = true)
            }
        }
    }

    // The picked date, else the diary's selected day, else today.
    private fun aloituspv(picked: Long? = handle[KEY_ALOITUSPV]): LocalDate = LocalDate.ofEpochDay(
        picked ?: handle[ARG_ALKAA] ?: LocalDate.now(clock).toEpochDay()
    )

    private data class SaveState(val saving: Boolean = false, val failed: Boolean = false)

    companion object {
        /** The navigation argument: the start date's default as an epoch day. */
        const val ARG_ALKAA = "alkaa"

        private const val KEY_KUVAUS = "kuvaus"
        private const val KEY_SIVUOIRE = "sivuoire"
        private const val KEY_ALOITUSPV = "aloituspv"
        private const val KEY_LOPETUSPV = "lopetuspv"
        private const val KEY_SAVED = "saved"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LaakeKamuApp
                LisaaOireViewModel(
                    createSavedStateHandle(),
                    app.container.oireRepository,
                    app.container.clock
                )
            }
        }
    }
}
