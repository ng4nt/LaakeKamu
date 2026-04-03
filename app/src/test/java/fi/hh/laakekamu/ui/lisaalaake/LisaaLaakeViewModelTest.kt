package fi.hh.laakekamu.ui.lisaalaake

import androidx.lifecycle.SavedStateHandle
import fi.hh.laakekamu.data.FakeLaakeRepository
import fi.hh.laakekamu.data.entity.Laake
import fi.hh.laakekamu.domain.Kesto
import fi.hh.laakekamu.domain.KuinkaUsein
import fi.hh.laakekamu.domain.NameError
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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

    private val today = LocalDate.of(2025, 12, 18)
    private val zone = ZoneId.of("Europe/Helsinki")
    private val clock = Clock.fixed(today.atTime(10, 0).atZone(zone).toInstant(), zone)

    private fun viewModel(
        handle: SavedStateHandle = SavedStateHandle(),
        repo: FakeLaakeRepository = repository
    ) = LisaaLaakeViewModel(handle, repo, clock)

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
        assertEquals("A aamulääke", restored.nimi)
        assertEquals(1L, state.tyyppiId)
        assertEquals("5", restored.annosmaara)
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
        assertEquals("5", vm.annosmaara)
    }

    @Test
    fun typedText_isThereAtOnce_withoutCollecting() {
        // Text fields read Compose state, not the flow.
        val vm = viewModel()
        vm.onNimiChange("Bu")
        vm.onNimiChange("Bur")
        assertEquals("Bur", vm.nimi)
    }

    @Test
    fun aFailedChoiceQuery_showsTheError_andTheFormStaysLive() = runTest {
        // The error must not end the whole state flow.
        val vm = viewModel(repo = FakeLaakeRepository(failChoices = true)).also { collect(it) }

        assertTrue(vm.uiState.value.loadError)
        vm.onTyyppiSelect(2L)
        vm.onAikaChange(0, 480)

        assertEquals(2L, vm.uiState.value.tyyppiId)
        assertEquals(listOf<Int?>(480), vm.uiState.value.ajat)
    }

    @Test
    fun editingWhileTheNameIsChecked_dropsThatCheck() = runTest {
        // A stale result must not open step 2 or bring back a fixed error.
        val vm = viewModel().also { collect(it) }
        fillValid(vm)
        val gate = CompletableDeferred<Unit>()
        repository.nameErrorGate = gate

        vm.onNext()
        vm.onNimiChange("")
        gate.complete(Unit)

        assertEquals(1, vm.uiState.value.step)
        assertNull(vm.uiState.value.errors.nimi)
    }

    // Step 2: times, frequency and dates

    @Test
    fun step2_startsWithOneEmptyTime_noFrequency_andTodayAsTheStart() = runTest {
        val vm = viewModel().also { collect(it) }

        val state = vm.uiState.value
        assertEquals(listOf<Int?>(null), state.ajat)
        assertNull(state.kuinkaUsein)
        assertEquals(today, state.aloituspv)
        assertNull(state.lopetuspv)
        assertEquals(Kesto.Kaytossa(1), state.kesto)
    }

    @Test
    fun duplicateTime_isRejected_andTheRowKeepsItsValue() = runTest {
        val vm = viewModel().also { collect(it) }
        vm.onAikaChange(0, 480)
        vm.onLisaaAika()
        vm.onAikaChange(1, 1200)

        vm.onAikaChange(1, 480)

        assertEquals(listOf<Int?>(480, 1200), vm.uiState.value.ajat)
        assertEquals(mapOf(1 to AikaError.JoLisatty(480)), vm.uiState.value.step2Errors.ajat)

        vm.onAikaChange(1, 1260)

        assertEquals(listOf<Int?>(480, 1260), vm.uiState.value.ajat)
        assertTrue(vm.uiState.value.step2Errors.ajat.isEmpty())
    }

    @Test
    fun removingARow_keepsTheOtherRowsErrors_onTheirRows() = runTest {
        val vm = viewModel().also { collect(it) }
        vm.onKuinkaUseinSelect(KuinkaUsein.PAIVITTAIN)
        vm.onAikaChange(0, 480)
        vm.onLisaaAika()
        vm.onLisaaAika()
        vm.onDone()
        assertEquals(setOf(1, 2), vm.uiState.value.step2Errors.ajat.keys)

        vm.onPoistaAika(1)

        assertEquals(listOf<Int?>(480, null), vm.uiState.value.ajat)
        assertEquals(mapOf(1 to AikaError.Puuttuu), vm.uiState.value.step2Errors.ajat)
    }

    @Test
    fun theLastRowCantBeRemoved() = runTest {
        val vm = viewModel().also { collect(it) }
        vm.onAikaChange(0, 480)

        vm.onPoistaAika(0)

        assertEquals(listOf<Int?>(480), vm.uiState.value.ajat)
    }

    @Test
    fun tarvittaessa_clearsAndDisablesTheTimes() = runTest {
        val vm = viewModel().also { collect(it) }
        vm.onAikaChange(0, 480)
        vm.onLisaaAika()
        vm.onAikaChange(1, 1200)

        vm.onKuinkaUseinSelect(KuinkaUsein.TARVITTAESSA)
        vm.onLisaaAika()

        val state = vm.uiState.value
        assertEquals(listOf<Int?>(null), state.ajat)
        assertFalse(state.ajatEnabled)
        vm.onDone()
        assertTrue(vm.uiState.value.step2Errors.isEmpty)
    }

    @Test
    fun endBeforeStart_isAnError_assoonAsItIsPicked_andHasNoDurationHint() = runTest {
        val vm = viewModel().also { collect(it) }

        vm.onLopetuspvChange(today.minusDays(1))

        assertTrue(vm.uiState.value.step2Errors.lopetuspv)
        assertNull(vm.uiState.value.kesto)

        vm.onAloituspvChange(today.minusDays(3))

        assertFalse(vm.uiState.value.step2Errors.lopetuspv)
        assertEquals(Kesto.Paattynyt, vm.uiState.value.kesto)
    }

    @Test
    fun durationHint_followsTheL6Labels() = runTest {
        val vm = viewModel().also { collect(it) }

        vm.onLopetuspvChange(today.plusDays(13))
        assertEquals(Kesto.Jaljella(14), vm.uiState.value.kesto)

        vm.onAloituspvChange(today.plusDays(2))
        assertEquals(Kesto.Alkaa(today.plusDays(2)), vm.uiState.value.kesto)

        vm.onLopetuspvChange(null)
        assertEquals(Kesto.Alkaa(today.plusDays(2)), vm.uiState.value.kesto)
    }

    @Test
    fun done_withoutFrequencyOrTimes_showsTheErrors() = runTest {
        val vm = viewModel().also { collect(it) }

        vm.onDone()
        assertTrue(vm.uiState.value.step2Errors.kuinkaUsein)

        vm.onKuinkaUseinSelect(KuinkaUsein.PAIVITTAIN)
        assertFalse(vm.uiState.value.step2Errors.kuinkaUsein)
        vm.onDone()
        assertEquals(mapOf(0 to AikaError.Puuttuu), vm.uiState.value.step2Errors.ajat)
    }

    @Test
    fun done_withAValidDailyForm_hasNoErrors() = runTest {
        val vm = viewModel().also { collect(it) }
        vm.onKuinkaUseinSelect(KuinkaUsein.PAIVITTAIN)
        vm.onAikaChange(0, 480)
        vm.onLisaaAika()
        vm.onAikaChange(1, 1200)
        vm.onLopetuspvChange(today.plusDays(9))

        vm.onDone()
        assertTrue(vm.uiState.value.step2Errors.isEmpty)
    }

    @Test
    fun step2Values_areRestoredAfterProcessDeath() = runTest {
        val handle = SavedStateHandle()
        val first = viewModel(handle).also { collect(it) }
        first.onKuinkaUseinSelect(KuinkaUsein.PAIVITTAIN)
        first.onAikaChange(0, 480)
        first.onLisaaAika()
        first.onAloituspvChange(today.plusDays(1))
        first.onLopetuspvChange(today.plusDays(30))

        val restored =
            viewModel(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
        collect(restored)

        val state = restored.uiState.value
        assertEquals(listOf<Int?>(480, null), state.ajat)
        assertEquals(KuinkaUsein.PAIVITTAIN, state.kuinkaUsein)
        assertEquals(today.plusDays(1), state.aloituspv)
        assertEquals(today.plusDays(30), state.lopetuspv)
    }

    // Saving with "Valmis!"

    @Test
    fun done_savesADailyMedication_withItsTimes_once() = runTest {
        val vm = viewModel().also { collect(it) }
        fillValid(vm, nimi = "  A aamulääke ")
        vm.onNext()
        fillStep2(vm)

        vm.onDone()
        vm.onDone()

        val expected = Laake(
            nimi = "A aamulääke",
            tyyppiId = 1,
            annosmaara = "5",
            yksikko = "mg",
            kuinkaUsein = KuinkaUsein.PAIVITTAIN,
            aloituspv = today,
            lopetuspv = today.plusDays(9)
        )
        assertEquals(listOf(expected to listOf(480, 1200)), repository.added)
        assertTrue(vm.uiState.value.saved)
    }

    @Test
    fun done_savesAnAsNeededMedication_withoutTimes() = runTest {
        val vm = viewModel().also { collect(it) }
        fillValid(vm)
        vm.onNext()
        vm.onKuinkaUseinSelect(KuinkaUsein.TARVITTAESSA)

        vm.onDone()

        assertEquals(emptyList<Int>(), repository.added.single().second)
        assertEquals(KuinkaUsein.TARVITTAESSA, repository.added.single().first.kuinkaUsein)
    }

    @Test
    fun done_withAnInvalidStep2_savesNothing() = runTest {
        val vm = viewModel().also { collect(it) }
        fillValid(vm)
        vm.onNext()

        vm.onDone()

        assertTrue(repository.added.isEmpty())
        assertFalse(vm.uiState.value.saved)
    }

    @Test
    fun nameTakenMeanwhile_returnsToStep1_withTheL5Message() = runTest {
        val vm = viewModel().also { collect(it) }
        fillValid(vm, nimi = "Burana")
        vm.onNext()
        fillStep2(vm)
        repository.laakkeet.value = listOf(laake("burana"))

        vm.onDone()

        assertTrue(repository.added.isEmpty())
        assertEquals(1, vm.uiState.value.step)
        assertEquals(NameError.Duplicate("Burana"), vm.uiState.value.errors.nimi)
        assertFalse(vm.uiState.value.saveError)
    }

    @Test
    fun aFailedSave_keepsTheForm_andShowsTheError() = runTest {
        val vm = viewModel(repo = FakeLaakeRepository(failAdd = true)).also { collect(it) }
        fillValid(vm)
        vm.onNext()
        fillStep2(vm)

        vm.onDone()

        val state = vm.uiState.value
        assertTrue(state.saveError)
        assertFalse(state.saved || state.saving)
        assertEquals(2, state.step)
        assertEquals(listOf<Int?>(480, 1200), state.ajat)
    }

    private fun fillStep2(vm: LisaaLaakeViewModel) {
        vm.onKuinkaUseinSelect(KuinkaUsein.PAIVITTAIN)
        vm.onAikaChange(0, 480)
        vm.onLisaaAika()
        vm.onAikaChange(1, 1200)
        vm.onLopetuspvChange(today.plusDays(9))
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
