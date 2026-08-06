package fi.hh.laakekamu.ui.lisaaoire

import androidx.lifecycle.SavedStateHandle
import fi.hh.laakekamu.data.OireRepository
import fi.hh.laakekamu.data.entity.Laake
import fi.hh.laakekamu.data.entity.Oiremerkinta
import fi.hh.laakekamu.domain.KuinkaUsein
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
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

@OptIn(ExperimentalCoroutinesApi::class)
class LisaaOireViewModelTest {
    private val zone = ZoneId.of("Europe/Helsinki")
    private val today = LocalDate.of(2025, 12, 18)
    private val clock = Clock.fixed(today.atTime(10, 0).atZone(zone).toInstant(), zone)
    private val selected = LocalDate.of(2025, 12, 10)

    @Before
    fun setMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMain() {
        Dispatchers.resetMain()
    }

    @Test
    fun startDate_isTheDiarysSelectedDay_orTodayWithoutOne() = runTest {
        val fromDiary = collecting(viewModel())
        val withoutDay = collecting(viewModel(handle = SavedStateHandle()))

        assertEquals(selected, fromDiary.uiState.value.aloituspv)
        assertEquals(today, withoutDay.uiState.value.aloituspv)
    }

    @Test
    fun sideEffectOptions_eiSivuoireFirst_thenActiveMedicationsInFinnishOrder() = runTest {
        val repo = FakeRepository(listOf(laake(1, "Öljy"), laake(2, "Burana"), laake(3, "Ärtyvä")))

        val state = collecting(viewModel(repo)).uiState.value

        // Finnish order: ... Z, Å, Ä, Ö.
        assertEquals(listOf(EI_SIVUOIRE, 2L, 3L, 1L), state.sivuoireet)
        assertNull(state.sivuoire)
    }

    @Test
    fun emptyDescription_showsTheError_andTypingClearsIt() = runTest {
        val repo = FakeRepository()
        val vm = collecting(viewModel(repo))

        vm.onKuvausChange("   ")
        vm.onDone()

        assertTrue(vm.uiState.value.kuvausPuuttuu)
        assertEquals(emptyList<Oiremerkinta>(), repo.added)
        vm.onKuvausChange("Huimaus")
        assertFalse(vm.uiState.value.kuvausPuuttuu)
    }

    @Test
    fun endBeforeStart_showsTheError_andDoesntSave() = runTest {
        val repo = FakeRepository()
        val vm = collecting(viewModel(repo))
        vm.onKuvausChange("Huimaus")

        vm.onLopetuspvChange(selected.minusDays(1))
        assertTrue(vm.uiState.value.lopetusEnnenAloitusta)
        vm.onDone()

        assertEquals(emptyList<Oiremerkinta>(), repo.added)
        vm.onLopetuspvChange(selected)
        assertFalse(vm.uiState.value.lopetusEnnenAloitusta)
    }

    @Test
    fun valid_savesTrimmedWithTheLinkedMedication_andLeaves() = runTest {
        val repo = FakeRepository(listOf(laake(1, "A aamulääke")))
        val vm = collecting(viewModel(repo))
        vm.onKuvausChange("  Huimaus ")
        vm.onSivuoireSelect(1)
        vm.onLopetuspvChange(selected.plusDays(2))

        vm.onDone()

        assertEquals(
            listOf(Oiremerkinta(0, "Huimaus", 1, selected, selected.plusDays(2))),
            repo.added
        )
        assertTrue(vm.uiState.value.saved)
    }

    @Test
    fun eiSivuoire_orNoPick_savesWithoutALink() = runTest {
        val repo = FakeRepository(listOf(laake(1, "A aamulääke")))
        val picked = collecting(viewModel(repo))
        picked.onKuvausChange("Päänsärky")
        picked.onSivuoireSelect(EI_SIVUOIRE)
        picked.onDone()
        val untouched = collecting(viewModel(repo))
        untouched.onKuvausChange("Väsymys")
        untouched.onDone()

        assertEquals(listOf(null, null), repo.added.map { it.laakeId })
    }

    @Test
    fun aDoubleTap_savesOnce() = runTest {
        val repo = FakeRepository(gate = CompletableDeferred())
        val vm = collecting(viewModel(repo))
        vm.onKuvausChange("Huimaus")

        vm.onDone()
        assertTrue(vm.uiState.value.saving)
        vm.onDone()
        repo.gate?.complete(Unit)

        assertEquals(1, repo.added.size)
        assertTrue(vm.uiState.value.saved)
    }

    @Test
    fun aFailedSave_showsTheError_andCanBeRetried() = runTest {
        val repo = FakeRepository(failSaves = 1)
        val vm = collecting(viewModel(repo))
        vm.onKuvausChange("Huimaus")

        vm.onDone()
        assertTrue(vm.uiState.value.saveError)
        assertFalse(vm.uiState.value.saved)
        vm.onDone()

        assertFalse(vm.uiState.value.saveError)
        assertTrue(vm.uiState.value.saved)
        assertEquals(1, repo.added.size)
    }

    @Test
    fun medicationsThatCantLoad_leaveTheFormUsable() = runTest {
        val repo = FakeRepository(failLoad = true)
        val vm = collecting(viewModel(repo))

        assertTrue(vm.uiState.value.loadError)
        assertEquals(listOf(EI_SIVUOIRE), vm.uiState.value.sivuoireet)
        vm.onKuvausChange("Huimaus")
        vm.onDone()
        assertTrue(vm.uiState.value.saved)
    }

    @Test
    fun aPickThatIsNoLongerActive_isShownAsNoPick_andSavedWithoutALink() = runTest {
        val repo = FakeRepository(listOf(laake(1, "A aamulääke")))
        val vm = collecting(viewModel(repo))
        vm.onKuvausChange("Huimaus")
        vm.onSivuoireSelect(1)

        repo.laakkeet.value = emptyList()
        assertNull(vm.uiState.value.sivuoire)
        vm.onDone()

        assertEquals(listOf<Long?>(null), repo.added.map { it.laakeId })
    }

    @Test
    fun theForm_survivesProcessDeath() = runTest {
        val repo = FakeRepository(listOf(laake(1, "A")))
        val handle = SavedStateHandle(mapOf(LisaaOireViewModel.ARG_ALKAA to selected.toEpochDay()))
        val vm = viewModel(repo, handle)
        vm.onKuvausChange("Huimaus")
        vm.onSivuoireSelect(1)
        vm.onAloituspvChange(selected.minusDays(3))
        vm.onLopetuspvChange(selected)

        val saved = handle.keys().associateWith { handle.get<Any?>(it) }
        val restored = collecting(viewModel(repo, SavedStateHandle(saved)))

        assertEquals("Huimaus", restored.kuvaus)
        val state = restored.uiState.value
        assertEquals(1L, state.sivuoire)
        assertEquals(selected.minusDays(3), state.aloituspv)
        assertEquals(selected, state.lopetuspv)
    }

    /** Keeps uiState subscribed for the rest of the test, as the screen does. */
    private fun TestScope.collecting(vm: LisaaOireViewModel) = vm.also {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.uiState.collect {} }
    }

    private fun viewModel(
        repository: OireRepository = FakeRepository(),
        handle: SavedStateHandle =
            SavedStateHandle(mapOf(LisaaOireViewModel.ARG_ALKAA to selected.toEpochDay()))
    ) = LisaaOireViewModel(handle, repository, clock)

    private fun laake(id: Long, nimi: String) = Laake(
        laakeId = id,
        nimi = nimi,
        tyyppiId = 1,
        annosmaara = "5",
        yksikko = "mg",
        kuinkaUsein = KuinkaUsein.PAIVITTAIN,
        aloituspv = today.minusDays(10),
        lopetuspv = null
    )

    /**
     * Symptoms in memory. The first [failSaves] saves fail; [failLoad] fails the medications;
     * a save waits for [gate] when one is given.
     */
    private class FakeRepository(
        laakkeet: List<Laake> = emptyList(),
        private var failSaves: Int = 0,
        private val failLoad: Boolean = false,
        val gate: CompletableDeferred<Unit>? = null
    ) : OireRepository {
        val laakkeet = MutableStateFlow(laakkeet)
        val added = mutableListOf<Oiremerkinta>()

        override fun observeAll(): Flow<List<Oiremerkinta>> = MutableStateFlow(added.toList())

        override fun observeAktiivisetLaakkeet(): Flow<List<Laake>> = flow {
            if (failLoad) error("database unavailable")
            laakkeet.collect { emit(it) }
        }

        override suspend fun add(merkinta: Oiremerkinta): Long {
            gate?.await()
            if (failSaves > 0) {
                failSaves--
                error("database unavailable")
            }
            added += merkinta
            return added.size.toLong()
        }

        override suspend fun update(merkinta: Oiremerkinta) = Unit

        override suspend fun delete(merkinta: Oiremerkinta) = Unit
    }
}
