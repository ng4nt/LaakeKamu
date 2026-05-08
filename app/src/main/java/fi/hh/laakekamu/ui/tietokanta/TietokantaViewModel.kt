package fi.hh.laakekamu.ui.tietokanta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fi.hh.laakekamu.LaakeKamuApp
import fi.hh.laakekamu.data.TietokannanTila
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Opens the database when the app starts, so the screens appear only once it's open, and runs
 * the error screen's actions. [tila] is the container's state; [avaa]
 * opens the database and [aloitaAlusta] deletes it and starts over. Both report a failure
 * through [tila] by throwing.
 */
class TietokantaViewModel(
    val tila: StateFlow<TietokannanTila>,
    private val avaa: suspend () -> Unit,
    private val aloitaAlusta: suspend () -> Unit
) : ViewModel() {
    private val busy = MutableStateFlow(false)

    /** True while an open or a start-over runs; the error screen's buttons wait meanwhile. */
    val kaynnissa: StateFlow<Boolean> = busy.asStateFlow()

    init {
        run(avaa)
    }

    /** "Yritä uudelleen". */
    fun yritaUudelleen() {
        run(avaa)
    }

    /** "Aloita alusta", only after the user has confirmed it in the dialog. */
    fun aloitaAlusta() {
        run(aloitaAlusta)
    }

    // One action at a time: a double tap on the confirm button doesn't start over twice.
    private fun run(action: suspend () -> Unit) {
        if (!busy.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The container has set EI_SAATAVILLA; the error screen stays.
            } finally {
                busy.value = false
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as LaakeKamuApp).container
                TietokantaViewModel(
                    container.tietokannanTila,
                    avaa = { container.database() },
                    aloitaAlusta = { container.startOver() }
                )
            }
        }
    }
}
