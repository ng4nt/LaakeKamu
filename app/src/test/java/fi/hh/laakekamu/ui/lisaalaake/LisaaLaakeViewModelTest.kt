package fi.hh.laakekamu.ui.lisaalaake

import androidx.lifecycle.SavedStateHandle
import fi.hh.laakekamu.data.FakeLaakeRepository
import fi.hh.laakekamu.data.entity.Laake
import fi.hh.laakekamu.domain.KuinkaUsein
import fi.hh.laakekamu.domain.NameError
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LisaaLaakeViewModelTest {
    private val repository = FakeLaakeRepository()

    @Before
    fun setMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMain() {
        Dispatchers.resetMain()
    }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        LisaaLaakeViewModel(handle, repository)

    /** Keeps uiState collected, as the screen does. */
    private fun kotlinx.coroutines.test.TestScope.collect(vm: LisaaLaakeViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
    }

    @Test
    fun emptyForm_next_showsEveryError_andStaysOnStep1() = runTest {
        val vm = viewModel().also { collect(it) }

        vm.onNext()

        val state = vm.uiState.value
        assertEquals(1, state.step)
        assertEquals(NameError.Blank, state.errors.nimi)
        assertTrue(state.errors.tyyppi && state.errors.annosmaara && state.errors.yksikko)
    }

    @Test
    fun duplicateName_isReported_withTheTypedName() = runTest {
        repository.laakkeet.value = listOf(laake("Burana"))
        val vm = viewModel().also { collect(it) }
        fillValid(vm, nimi = "burana")

        vm.onNext()

        assertEquals(NameError.Duplicate("burana"), vm.uiState.value.errors.nimi)
        assertEquals(1, vm.uiState.value.step)
    }

    @Test
    fun validForm_next_goesToStep2() = runTest {
        val vm = viewModel().also { collect(it) }
        fillValid(vm)

        vm.onNext()

        assertEquals(2, vm.uiState.value.step)
        assertTrue(vm.uiState.value.errors.isEmpty)
    }

    @Test
    fun changingAField_clearsOnlyItsError() = runTest {
        val vm = viewModel().also { collect(it) }
        vm.onNext()

        vm.onNimiChange("A")

        assertNull(vm.uiState.value.errors.nimi)
        assertTrue(vm.uiState.value.errors.annosmaara)
    }

    @Test
    fun theChoiceListsComeFromTheRepository() = runTest {
        val vm = viewModel().also { collect(it) }
        assertEquals(listOf("tabletti", "kapseli"), vm.uiState.value.tyypit.map { it.tyyppi })
        assertEquals(listOf("mg", "ml"), vm.uiState.value.yksikot.map { it.yksikko })
    }

    @Test
    fun everyFieldIsSaved_andRestoredAfterProcessDeath() = runTest {
        val handle = SavedStateHandle()
        val first = viewModel(handle).also { collect(it) }
        fillValid(first, nimi = "A aamulääke")
        first.onNext()

        // A new ViewModel with the same handle = the system restored the process.
        val restored =
            viewModel(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
        collect(restored)

        val state = restored.uiState.value
        assertEquals("A aamulääke", state.nimi)
        assertEquals(1L, state.tyyppiId)
        assertEquals("5", state.annosmaara)
        assertEquals("mg", state.yksikko)
        assertEquals(2, state.step)
    }

    @Test
    fun backFromStep2_returnsToStep1_keepingTheValues() = runTest {
        val vm = viewModel().also { collect(it) }
        fillValid(vm)
        vm.onNext()

        vm.onBackToStep1()

        assertEquals(1, vm.uiState.value.step)
        assertEquals("5", vm.uiState.value.annosmaara)
    }

    private fun fillValid(vm: LisaaLaakeViewModel, nimi: String = "A aamulääke") {
        vm.onNimiChange(nimi)
        vm.onTyyppiSelect(1L)
        vm.onAnnosmaaraChange("5")
        vm.onYksikkoSelect("mg")
    }

    private fun laake(nimi: String) = Laake(
        laakeId = 1,
        nimi = nimi,
        tyyppiId = 1,
        annosmaara = "5",
        yksikko = "mg",
        kuinkaUsein = KuinkaUsein.PAIVITTAIN,
        aloituspv = LocalDate.of(2025, 12, 1),
        lopetuspv = null
    )
}
