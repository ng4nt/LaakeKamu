package fi.hh.laakekamu.ui.lisaalaake

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fi.hh.laakekamu.LaakeKamuApp
import fi.hh.laakekamu.data.LaakeRepository
import fi.hh.laakekamu.data.entity.Tyyppi
import fi.hh.laakekamu.data.entity.Yksikko
import fi.hh.laakekamu.domain.NameError
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

/** Everything the add/edit wizard shows. */
data class LisaaLaakeUiState(
    val step: Int = 1,
    val nimi: String = "",
    val tyyppiId: Long? = null,
    val annosmaara: String = "",
    val yksikko: String? = null,
    val tyypit: List<Tyyppi> = emptyList(),
    val yksikot: List<Yksikko> = emptyList(),
    val errors: Step1Errors = Step1Errors(),
    /** The choice lists couldn't be loaded (e.g. database unavailable). */
    val loadError: Boolean = false
)

/**
 * The add/edit medication wizard. Every field lives in the
 * [SavedStateHandle], so the form survives process death. Step 1 is validated before
 * moving on: name (via the repository), type, dose and unit are required.
 */
class LisaaLaakeViewModel(
    private val handle: SavedStateHandle,
    private val repository: LaakeRepository
) : ViewModel() {
    private val errors = MutableStateFlow(Step1Errors())

    private val form = combine(
        handle.getStateFlow(KEY_STEP, 1),
        handle.getStateFlow(KEY_NIMI, ""),
        handle.getStateFlow<Long?>(KEY_TYYPPI_ID, null),
        handle.getStateFlow(KEY_ANNOSMAARA, ""),
        handle.getStateFlow<String?>(KEY_YKSIKKO, null)
    ) { step, nimi, tyyppiId, annosmaara, yksikko ->
        LisaaLaakeUiState(step, nimi, tyyppiId, annosmaara, yksikko)
    }

    val uiState: StateFlow<LisaaLaakeUiState> = combine(
        form,
        repository.observeTyypit(),
        repository.observeYksikot(),
        errors
    ) { state, tyypit, yksikot, errors ->
        state.copy(tyypit = tyypit, yksikot = yksikot, errors = errors)
    }
        .catch { emit(restoredState().copy(loadError = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), restoredState())

    fun onNimiChange(value: String) {
        handle[KEY_NIMI] = value
        errors.update { it.copy(nimi = null) }
    }

    fun onTyyppiSelect(tyyppiId: Long) {
        handle[KEY_TYYPPI_ID] = tyyppiId
        errors.update { it.copy(tyyppi = false) }
    }

    fun onAnnosmaaraChange(value: String) {
        handle[KEY_ANNOSMAARA] = value
        errors.update { it.copy(annosmaara = false) }
    }

    fun onYksikkoSelect(yksikko: String) {
        handle[KEY_YKSIKKO] = yksikko
        errors.update { it.copy(yksikko = false) }
    }

    /** "Seuraava vaihe": validates step 1 and moves to step 2 when everything is valid. */
    fun onNext() {
        viewModelScope.launch {
            val found = Step1Errors(
                nimi = repository.nameError(handle[KEY_NIMI] ?: ""),
                tyyppi = handle.get<Long>(KEY_TYYPPI_ID) == null,
                annosmaara = handle.get<String>(KEY_ANNOSMAARA).isNullOrBlank(),
                yksikko = handle.get<String>(KEY_YKSIKKO) == null
            )
            errors.value = found
            if (found.isEmpty) handle[KEY_STEP] = 2
        }
    }

    /** "Takaisin" on step 2 (and system back there) keeps the values. */
    fun onBackToStep1() {
        handle[KEY_STEP] = 1
    }

    /** The saved values, so the first frame after a restore already shows them. */
    private fun restoredState() = LisaaLaakeUiState(
        step = handle[KEY_STEP] ?: 1,
        nimi = handle[KEY_NIMI] ?: "",
        tyyppiId = handle[KEY_TYYPPI_ID],
        annosmaara = handle[KEY_ANNOSMAARA] ?: "",
        yksikko = handle[KEY_YKSIKKO]
    )

    companion object {
        private const val KEY_STEP = "step"
        private const val KEY_NIMI = "nimi"
        private const val KEY_TYYPPI_ID = "tyyppiId"
        private const val KEY_ANNOSMAARA = "annosmaara"
        private const val KEY_YKSIKKO = "yksikko"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LaakeKamuApp
                LisaaLaakeViewModel(createSavedStateHandle(), app.container.laakeRepository)
            }
        }
    }
}
