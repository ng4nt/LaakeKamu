package fi.hh.laakekamu.ui.muokkaalaake

import androidx.lifecycle.SavedStateHandle
import fi.hh.laakekamu.data.FakeLaakeRepository
import fi.hh.laakekamu.data.entity.Laake
import fi.hh.laakekamu.domain.KuinkaUsein
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Choose, confirm, delete; the selection and dialog survive a restore. */
@OptIn(ExperimentalCoroutinesApi::class)
class MuokkaaLaakettaViewModelTest {
    private val a = laake(1, "A aamulääke")
    private val b = laake(2, "B iltalääke")
    private val repository = FakeLaakeRepository().apply { laakkeet.value = listOf(a, b) }

    @Before
    fun setMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMain() {
        Dispatchers.resetMain()
    }

    @Test
    fun nothingIsSelectedAtFirst_soNothingCanBeDeleted() = runTest {
        val vm = collecting(MuokkaaLaakettaViewModel(SavedStateHandle(), repository))

        vm.onDeleteClick()

        assertEquals(listOf(a, b), vm.uiState.value.laakkeet)
        assertNull(vm.uiState.value.valittu)
        assertFalse(vm.uiState.value.vahvistetaan)
    }

    @Test
    fun deleteAsksFirst_andCancelDeletesNothing() = runTest {
        val vm = collecting(MuokkaaLaakettaViewModel(SavedStateHandle(), repository))
        vm.onSelect(b)

        vm.onDeleteClick()
        assertTrue(vm.uiState.value.vahvistetaan)
        vm.onDeleteDismiss()

        assertFalse(vm.uiState.value.vahvistetaan)
        assertTrue(repository.deleted.isEmpty())
    }

    @Test
    fun confirmedDelete_deletesTheSelectedOnce_andLeaves() = runTest {
        val vm = collecting(MuokkaaLaakettaViewModel(SavedStateHandle(), repository))
        vm.onSelect(b)
        vm.onDeleteClick()

        vm.onDeleteConfirm()
        vm.onDeleteConfirm()

        assertEquals(listOf(2L), repository.deleted)
        assertTrue(vm.uiState.value.poistettu)
        assertFalse(vm.uiState.value.vahvistetaan)
        assertEquals(listOf(a), vm.uiState.value.laakkeet)
    }

    @Test
    fun failedDelete_showsTheError_andKeepsTheSelection() = runTest {
        repository.deleteFails = true
        val vm = collecting(MuokkaaLaakettaViewModel(SavedStateHandle(), repository))
        vm.onSelect(a)
        vm.onDeleteClick()

        vm.onDeleteConfirm()

        assertTrue(vm.uiState.value.poistoEpaonnistui)
        assertFalse(vm.uiState.value.poistettu)
        assertEquals(a, vm.uiState.value.valittu)
    }

    @Test
    fun selectionAndOpenDialog_surviveProcessDeath() = runTest {
        val handle = SavedStateHandle()
        val first = collecting(MuokkaaLaakettaViewModel(handle, repository))
        first.onSelect(b)
        first.onDeleteClick()

        val restored =
            collecting(MuokkaaLaakettaViewModel(SavedStateHandle(handle.toMap()), repository))

        assertEquals(b, restored.uiState.value.valittu)
        assertTrue(restored.uiState.value.vahvistetaan)
    }

    @Test
    fun loadError_isShown() = runTest {
        val failing = FakeLaakeRepository(failChoices = true)
        val vm = collecting(MuokkaaLaakettaViewModel(SavedStateHandle(), failing))

        assertTrue(vm.uiState.value.loadError)
    }

    private fun TestScope.collecting(vm: MuokkaaLaakettaViewModel) = vm.also {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.uiState.collect {} }
    }

    private fun SavedStateHandle.toMap(): Map<String, Any?> = keys().associateWith { get<Any?>(it) }

    private fun laake(id: Long, nimi: String) = Laake(
        laakeId = id,
        nimi = nimi,
        tyyppiId = 1,
        annosmaara = "5",
        yksikko = "mg",
        kuinkaUsein = KuinkaUsein.PAIVITTAIN,
        aloituspv = LocalDate.of(2025, 12, 1),
        lopetuspv = null
    )
}
