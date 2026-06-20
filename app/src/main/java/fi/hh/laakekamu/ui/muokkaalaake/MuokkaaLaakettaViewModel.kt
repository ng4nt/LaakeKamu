package fi.hh.laakekamu.ui.muokkaalaake

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

/** Muokkaa lääkettä: the selector, then edit or delete. */
data class MuokkaaLaakettaUiState(
    /** Every medication that isn't deleted, ended ones too, in Finnish alphabetical order. */
    val laakkeet: List<Laake> = emptyList(),
    /** The selected medication; null until one is chosen, or when it's gone meanwhile. */
    val valittu: Laake? = null,
    /** The delete confirmation is open. */
    val vahvistetaan: Boolean = false,
    /** The delete is running; the buttons are disabled meanwhile. */
    val poistetaan: Boolean = false,
    /** The medication is deleted: the screen leaves (also after a restore). */
    val poistettu: Boolean = false,
    /** The delete failed; the selection stays so it can be tried again. */
    val poistoEpaonnistui: Boolean = false,
    /** The medications couldn't be read (e.g. database unavailable). */
    val loadError: Boolean = false
)

/**
 * The selected medication's id and the open dialog are kept in the [SavedStateHandle], so they
 * survive rotation and process death.
 */
class MuokkaaLaakettaViewModel(
    private val handle: SavedStateHandle,
    private val repository: LaakeRepository
) : ViewModel() {
    private val delete = MutableStateFlow(DeleteState())
    private var deleteJob: Job? = null

    private val laakkeet = repository.observeActive()
        .map { Loaded(it) }
        .catch { emit(Loaded(emptyList(), loadError = true)) }

    val uiState: StateFlow<MuokkaaLaakettaUiState> = combine(
        laakkeet,
        handle.getStateFlow<Long?>(KEY_VALITTU, null),
        handle.getStateFlow(KEY_VAHVISTETAAN, false),
        handle.getStateFlow(KEY_POISTETTU, false),
        delete
    ) { loaded, valittuId, vahvistetaan, poistettu, delete ->
        val valittu = loaded.laakkeet.find { it.laakeId == valittuId }
        MuokkaaLaakettaUiState(
            laakkeet = loaded.laakkeet,
            valittu = valittu,
            // Only for a medication that is still there.
            vahvistetaan = vahvistetaan && valittu != null,
            poistetaan = delete.running,
            poistettu = poistettu,
            poistoEpaonnistui = delete.failed,
            loadError = loaded.loadError
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        MuokkaaLaakettaUiState(poistettu = handle.get<Boolean>(KEY_POISTETTU) == true)
    )

    fun onSelect(laake: Laake) {
        handle[KEY_VALITTU] = laake.laakeId
        delete.value = DeleteState()
    }

    /** "Poista lääke": asks first. */
    fun onDeleteClick() {
        if (handle.get<Long>(KEY_VALITTU) != null) handle[KEY_VAHVISTETAAN] = true
    }

    fun onDeleteDismiss() {
        handle[KEY_VAHVISTETAAN] = false
    }

    /**
     * The delete is confirmed: a soft delete that ends the reminders and removes their
     * notifications and alarms. A second tap while deleting, or after, does
     * nothing.
     */
    fun onDeleteConfirm() {
        handle[KEY_VAHVISTETAAN] = false
        val laakeId = handle.get<Long>(KEY_VALITTU) ?: return
        if (deleteJob?.isActive == true || handle.get<Boolean>(KEY_POISTETTU) == true) return
        deleteJob = viewModelScope.launch {
            delete.value = DeleteState(running = true)
            try {
                repository.delete(laakeId)
                handle[KEY_POISTETTU] = true
                delete.value = DeleteState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                delete.value = DeleteState(failed = true)
            }
        }
    }

    private data class Loaded(val laakkeet: List<Laake>, val loadError: Boolean = false)

    private data class DeleteState(val running: Boolean = false, val failed: Boolean = false)

    companion object {
        private const val KEY_VALITTU = "valittu"
        private const val KEY_VAHVISTETAAN = "vahvistetaan"
        private const val KEY_POISTETTU = "poistettu"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LaakeKamuApp
                MuokkaaLaakettaViewModel(createSavedStateHandle(), app.container.laakeRepository)
            }
        }
    }
}
