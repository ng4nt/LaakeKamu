package fi.hh.laakekamu.ui.lisaalaake

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
import fi.hh.laakekamu.data.LaakeRepository
import fi.hh.laakekamu.data.entity.Laake
import fi.hh.laakekamu.data.entity.Tyyppi
import fi.hh.laakekamu.data.entity.Yksikko
import fi.hh.laakekamu.domain.Kesto
import fi.hh.laakekamu.domain.KuinkaUsein
import fi.hh.laakekamu.domain.NameError
import fi.hh.laakekamu.domain.isValidDateRange
import fi.hh.laakekamu.domain.kestoOf
import java.time.Clock
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Validation errors of wizard step 1; the screen turns them into Finnish texts. */
data class Step1Errors(
    val nimi: NameError? = null,
    val tyyppi: Boolean = false,
    val annosmaara: Boolean = false,
    val yksikko: Boolean = false
) {
    val isEmpty: Boolean get() = nimi == null && !tyyppi && !annosmaara && !yksikko
}

/** Why one row of the time list is invalid. */
sealed interface AikaError {
    /** The row has no time yet. */
    data object Puuttuu : AikaError

    /** [aika] is already on another row: "Aika klo 8.00 on jo lisätty." */
    data class JoLisatty(val aika: Int) : AikaError
}

/** Validation errors of wizard step 2. [ajat] is keyed by the row's index. */
data class Step2Errors(
    val ajat: Map<Int, AikaError> = emptyMap(),
    val kuinkaUsein: Boolean = false,
    val lopetuspv: Boolean = false
) {
    val isEmpty: Boolean get() = ajat.isEmpty() && !kuinkaUsein && !lopetuspv
}

/** Everything the add/edit wizard shows. */
data class LisaaLaakeUiState(
    val step: Int = 1,
    val tyyppiId: Long? = null,
    val yksikko: String? = null,
    val tyypit: List<Tyyppi> = emptyList(),
    val yksikot: List<Yksikko> = emptyList(),
    val errors: Step1Errors = Step1Errors(),
    /** The time rows, minutes after midnight; null = no time picked yet. Never empty. */
    val ajat: List<Int?> = listOf(null),
    val kuinkaUsein: KuinkaUsein? = null,
    val aloituspv: LocalDate = LocalDate.MIN,
    val lopetuspv: LocalDate? = null,
    /** The duration hint under Lopetuspäivä; null while the dates are invalid. */
    val kesto: Kesto? = null,
    val step2Errors: Step2Errors = Step2Errors(),
    /** The choice lists couldn't be loaded (e.g. database unavailable); the form still works. */
    val loadError: Boolean = false,
    /** "Valmis!" is saving; the button is disabled meanwhile. */
    val saving: Boolean = false,
    /** The last save failed for another reason than the name (e.g. the database). */
    val saveError: Boolean = false,
    /** The medication is saved: the screen leaves (also after a restore). */
    val saved: Boolean = false
) {
    /** Tarvittaessa has no times, so the time rows are disabled. */
    val ajatEnabled: Boolean get() = kuinkaUsein != KuinkaUsein.TARVITTAESSA
}

/**
 * The add/edit medication wizard. Every field lives in the
 * [SavedStateHandle], so the form survives process death: times as an `IntArray` (-1 = no
 * time yet), dates as epoch days. Step 1 is validated before moving on: name (via the
 * repository), type, dose and unit are required. Step 2 rejects a duplicate time when it's
 * picked, checks the date range as the dates change and validates everything on "Valmis!",
 * which then saves the medication. [clock] gives "today" (the default start date and the
 * duration hint).
 */
class LisaaLaakeViewModel(
    private val handle: SavedStateHandle,
    private val repository: LaakeRepository,
    private val clock: Clock
) : ViewModel() {
    private val errors = MutableStateFlow(Step1Errors())
    private val step2Errors = MutableStateFlow(Step2Errors())
    private val saveState = MutableStateFlow(SaveState())
    private var validation: Job? = null
    private var saveJob: Job? = null

    /**
     * The typed texts are Compose state, so a text field gets its new value in the same frame
     * (a round trip through flows can drop fast keystrokes). The handle keeps them
     * for process death.
     */
    var nimi by mutableStateOf(handle[KEY_NIMI] ?: "")
        private set
    var annosmaara by mutableStateOf(handle[KEY_ANNOSMAARA] ?: "")
        private set

    private val step1 = combine(
        handle.getStateFlow(KEY_STEP, 1),
        handle.getStateFlow<Long?>(KEY_TYYPPI_ID, null),
        handle.getStateFlow<String?>(KEY_YKSIKKO, null),
        handle.getStateFlow(KEY_SAVED, false)
    ) { step, tyyppiId, yksikko, saved ->
        LisaaLaakeUiState(step = step, tyyppiId = tyyppiId, yksikko = yksikko, saved = saved)
    }

    private val step2 = combine(
        handle.getStateFlow(KEY_AJAT, intArrayOf(NO_TIME)),
        handle.getStateFlow<String?>(KEY_KUINKA_USEIN, null),
        // Null until the user picks a date, so a restore on a later day defaults to that day
        //.
        handle.getStateFlow<Long?>(KEY_ALOITUSPV, null),
        handle.getStateFlow<Long?>(KEY_LOPETUSPV, null)
    ) { ajat, kuinkaUsein, aloituspv, lopetuspv ->
        Step2Values(ajat, kuinkaUsein, aloituspv, lopetuspv)
    }

    // A failed query becomes an error state here, before the form flows are combined, so the
    // form itself stays live.
    private val choices =
        combine(repository.observeTyypit(), repository.observeYksikot()) { tyypit, yksikot ->
            Choices(tyypit, yksikot)
        }.catch { emit(Choices(emptyList(), emptyList(), loadError = true)) }

    private val feedback = combine(errors, step2Errors, saveState, ::Triple)

    val uiState: StateFlow<LisaaLaakeUiState> = combine(
        step1,
        step2,
        choices,
        feedback
    ) { state, values, loaded, (found1, found2, save) ->
        values.applyTo(state).copy(
            tyypit = loaded.tyypit,
            yksikot = loaded.yksikot,
            loadError = loaded.loadError,
            errors = found1,
            step2Errors = found2,
            saving = save.saving,
            saveError = save.failed
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), restoredState())

    fun onNimiChange(value: String) {
        nimi = value
        handle[KEY_NIMI] = value
        validation?.cancel()
        errors.update { it.copy(nimi = null) }
    }

    fun onTyyppiSelect(tyyppiId: Long) {
        handle[KEY_TYYPPI_ID] = tyyppiId
        validation?.cancel()
        errors.update { it.copy(tyyppi = false) }
    }

    fun onAnnosmaaraChange(value: String) {
        annosmaara = value
        handle[KEY_ANNOSMAARA] = value
        validation?.cancel()
        errors.update { it.copy(annosmaara = false) }
    }

    fun onYksikkoSelect(yksikko: String) {
        handle[KEY_YKSIKKO] = yksikko
        validation?.cancel()
        errors.update { it.copy(yksikko = false) }
    }

    /**
     * "Seuraava vaihe": validates step 1 and moves to step 2 when everything is valid. The
     * fields are read once up front; a field change while the name is being checked cancels the
     * check, and so does a second tap.
     */
    fun onNext() {
        validation?.cancel()
        val name = nimi
        val missing = Step1Errors(
            tyyppi = handle.get<Long>(KEY_TYYPPI_ID) == null,
            annosmaara = annosmaara.isBlank(),
            yksikko = handle.get<String>(KEY_YKSIKKO) == null
        )
        validation = viewModelScope.launch {
            val found = missing.copy(nimi = repository.nameError(name))
            errors.value = found
            if (found.isEmpty) handle[KEY_STEP] = 2
        }
    }

    /** "Takaisin" on step 2 (and system back there) keeps the values. */
    fun onBackToStep1() {
        handle[KEY_STEP] = 1
    }

    /** A time picked on row [index]. A time already on another row is rejected. */
    fun onAikaChange(index: Int, aika: Int) {
        val ajat = ajat()
        if (index !in ajat.indices) return
        val taken = ajat.withIndex().any { (i, other) -> i != index && other == aika }
        if (taken) {
            setAikaError(index, AikaError.JoLisatty(aika))
            return
        }
        handle[KEY_AJAT] = ajat.copyOf().also { it[index] = aika }
        setAikaError(index, null)
    }

    /** "+ Lisää aika": a new empty row at the end. */
    fun onLisaaAika() {
        if (!ajatEnabled()) return
        handle[KEY_AJAT] = ajat() + NO_TIME
    }

    /** ✕ on row [index]. The last row stays (a daily medication needs a time). */
    fun onPoistaAika(index: Int) {
        val ajat = ajat()
        if (ajat.size <= 1 || index !in ajat.indices) return
        handle[KEY_AJAT] = ajat.filterIndexed { i, _ -> i != index }.toIntArray()
        step2Errors.update { errors ->
            val shifted = errors.ajat
                .filterKeys { it != index }
                .mapKeys { (i, _) -> if (i > index) i - 1 else i }
            errors.copy(ajat = shifted)
        }
    }

    /** Tarvittaessa clears and disables the times. */
    fun onKuinkaUseinSelect(value: KuinkaUsein) {
        handle[KEY_KUINKA_USEIN] = value.name
        if (value == KuinkaUsein.TARVITTAESSA) handle[KEY_AJAT] = intArrayOf(NO_TIME)
        step2Errors.update {
            it.copy(
                kuinkaUsein = false,
                ajat = if (value == KuinkaUsein.TARVITTAESSA) emptyMap() else it.ajat
            )
        }
    }

    fun onAloituspvChange(date: LocalDate) {
        handle[KEY_ALOITUSPV] = date.toEpochDay()
        checkDateRange()
    }

    /** Null clears the optional end date. */
    fun onLopetuspvChange(date: LocalDate?) {
        handle[KEY_LOPETUSPV] = date?.toEpochDay()
        checkDateRange()
    }

    /**
     * "Valmis!": validates step 2, then saves the medication in one transaction: a daily
     * one with its times, an as-needed one without. A second tap while saving, or after,
     * does nothing. If the name was taken meanwhile (e.g. in another window), step 1 shows the duplicate-name
     * error again; any other failure keeps the form and shows an error.
     */
    fun onDone() {
        if (saveJob?.isActive == true || handle.get<Boolean>(KEY_SAVED) == true) return
        if (!validateStep2()) return
        val laake = Laake(
            nimi = nimi.trim(),
            tyyppiId = handle.get<Long>(KEY_TYYPPI_ID) ?: return,
            annosmaara = annosmaara.trim(),
            yksikko = handle.get<String>(KEY_YKSIKKO) ?: return,
            kuinkaUsein = kuinkaUsein() ?: return,
            aloituspv = aloituspv(),
            lopetuspv = lopetuspv()
        )
        val times = if (laake.kuinkaUsein == KuinkaUsein.PAIVITTAIN) {
            ajat().filter { it != NO_TIME }
        } else {
            emptyList()
        }
        saveJob = viewModelScope.launch {
            saveState.value = SaveState(saving = true)
            try {
                repository.add(laake, times)
                handle[KEY_SAVED] = true
                saveState.value = SaveState()
            } catch (e: IllegalArgumentException) {
                val nameError = repository.nameError(laake.nimi)
                if (nameError != null) {
                    errors.value = Step1Errors(nimi = nameError)
                    handle[KEY_STEP] = 1
                }
                saveState.value = SaveState(failed = nameError == null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                saveState.value = SaveState(failed = true)
            }
        }
    }

    /** Checks the whole of step 2 and shows its errors. True when it's valid. */
    private fun validateStep2(): Boolean {
        val daily = kuinkaUsein() == KuinkaUsein.PAIVITTAIN
        val ajat = ajat()
        val ajatErrors = if (!daily) {
            emptyMap()
        } else {
            ajat.withIndex().mapNotNull { (i, aika) ->
                when {
                    aika == NO_TIME -> i to AikaError.Puuttuu
                    ajat.indexOf(aika) != i -> i to AikaError.JoLisatty(aika)
                    else -> null
                }
            }.toMap()
        }
        val found = Step2Errors(
            ajat = ajatErrors,
            kuinkaUsein = kuinkaUsein() == null,
            lopetuspv = !isValidDateRange(aloituspv(), lopetuspv())
        )
        step2Errors.value = found
        return found.isEmpty
    }

    private fun today(): LocalDate = LocalDate.now(clock)

    private fun ajat(): IntArray = handle[KEY_AJAT] ?: intArrayOf(NO_TIME)

    private fun kuinkaUsein(): KuinkaUsein? =
        handle.get<String>(KEY_KUINKA_USEIN)?.let(KuinkaUsein::valueOf)

    private fun ajatEnabled(): Boolean = kuinkaUsein() != KuinkaUsein.TARVITTAESSA

    private fun aloituspv(): LocalDate =
        LocalDate.ofEpochDay(handle[KEY_ALOITUSPV] ?: today().toEpochDay())

    private fun lopetuspv(): LocalDate? =
        handle.get<Long>(KEY_LOPETUSPV)?.let(LocalDate::ofEpochDay)

    private fun setAikaError(index: Int, error: AikaError?) {
        step2Errors.update {
            it.copy(ajat = if (error == null) it.ajat - index else it.ajat + (index to error))
        }
    }

    private fun checkDateRange() {
        step2Errors.update { it.copy(lopetuspv = !isValidDateRange(aloituspv(), lopetuspv())) }
    }

    /** The saved values, so the first frame after a restore already shows them. */
    private fun restoredState(): LisaaLaakeUiState {
        val state = LisaaLaakeUiState(
            step = handle[KEY_STEP] ?: 1,
            tyyppiId = handle[KEY_TYYPPI_ID],
            yksikko = handle[KEY_YKSIKKO]
        )
        val values = Step2Values(
            ajat(),
            handle[KEY_KUINKA_USEIN],
            handle[KEY_ALOITUSPV],
            handle[KEY_LOPETUSPV]
        )
        return values.applyTo(state)
    }

    /** Saving with "Valmis!": in progress, or the last attempt [failed]. */
    private data class SaveState(val saving: Boolean = false, val failed: Boolean = false)

    /** The wizard's choice lists, or [loadError] when they couldn't be read. */
    private data class Choices(
        val tyypit: List<Tyyppi>,
        val yksikot: List<Yksikko>,
        val loadError: Boolean = false
    )

    /** Step 2 as stored in the [SavedStateHandle]. */
    private inner class Step2Values(
        val ajat: IntArray,
        val kuinkaUsein: String?,
        val aloituspv: Long?,
        val lopetuspv: Long?
    ) {
        fun applyTo(state: LisaaLaakeUiState): LisaaLaakeUiState {
            val alku = aloituspv?.let(LocalDate::ofEpochDay) ?: today()
            val loppu = lopetuspv?.let(LocalDate::ofEpochDay)
            return state.copy(
                ajat = ajat.map { if (it == NO_TIME) null else it },
                kuinkaUsein = kuinkaUsein?.let(KuinkaUsein::valueOf),
                aloituspv = alku,
                lopetuspv = loppu,
                kesto = if (isValidDateRange(alku, loppu)) kestoOf(alku, loppu, today()) else null
            )
        }
    }

    companion object {
        private const val KEY_STEP = "step"
        private const val KEY_NIMI = "nimi"
        private const val KEY_TYYPPI_ID = "tyyppiId"
        private const val KEY_ANNOSMAARA = "annosmaara"
        private const val KEY_YKSIKKO = "yksikko"
        private const val KEY_AJAT = "ajat"
        private const val KEY_KUINKA_USEIN = "kuinkaUsein"
        private const val KEY_ALOITUSPV = "aloituspv"
        private const val KEY_LOPETUSPV = "lopetuspv"
        private const val KEY_SAVED = "saved"

        /** An empty time row in the saved `IntArray`. */
        private const val NO_TIME = -1

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LaakeKamuApp
                LisaaLaakeViewModel(
                    createSavedStateHandle(),
                    app.container.laakeRepository,
                    app.container.clock
                )
            }
        }
    }
}
