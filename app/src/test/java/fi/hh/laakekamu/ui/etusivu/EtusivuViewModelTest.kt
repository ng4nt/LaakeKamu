package fi.hh.laakekamu.ui.etusivu

import fi.hh.laakekamu.data.AnnosRepository
import fi.hh.laakekamu.data.entity.Laake
import fi.hh.laakekamu.data.entity.Muistutus
import fi.hh.laakekamu.domain.AnnoksenTila
import fi.hh.laakekamu.domain.KuinkaUsein
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EtusivuViewModelTest {
    private val zone = ZoneId.of("Europe/Helsinki")
    private val today = LocalDate.of(2025, 12, 18)
    private val yesterday = today.minusDays(1)

    /** 10.00 today. */
    private val now = ZonedDateTime.of(today.atTime(10, 0), zone)

    @Before
    fun setMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMain() {
        Dispatchers.resetMain()
    }

    // Which doses are due

    @Test
    fun today_showsCurrentReminders_groupedByTime_inFinnishOrder() {
        val b = laake(1, "Öljy")
        val a = laake(2, "Burana")
        val reminders = listOf(muistutus(1, 1, 480), muistutus(2, 2, 480), muistutus(3, 2, 1200))

        val ryhmat = etusivunRyhmat(today, now, listOf(b, a), reminders, emptySet())

        assertEquals(listOf(480, 1200), ryhmat.map { it.aika })
        assertEquals(listOf("Burana", "Öljy"), ryhmat[0].rivit.map { it.nimi })
        assertEquals(listOf(Vuorokausi.AAMU, Vuorokausi.ILTA), ryhmat.map { it.vuorokausi })
    }

    @Test
    fun endedNotStartedAndOutOfRangeReminders_areNotDue() {
        val running = laake(1, "A")
        val ended = laake(2, "B", end = yesterday)
        val reminders = listOf(
            muistutus(1, 1, 480, asti = yesterday),
            muistutus(2, 1, 540, alkaen = today.plusDays(1)),
            muistutus(3, 2, 600)
        )

        assertTrue(
            etusivunRyhmat(today, now, listOf(running, ended), reminders, emptySet()).isEmpty()
        )
    }

    @Test
    fun aDeletedMedication_isGoneToday_butKeepsItsPastDays() {
        // Deleted today after taking its dose: the reminder is valid until today.
        val deleted = laake(1, "A").copy(aktiivinen = false)
        val reminders = listOf(muistutus(1, 1, 480, asti = today))

        assertTrue(etusivunRyhmat(today, now, listOf(deleted), reminders, setOf(1L)).isEmpty())
        val open = listOf(muistutus(1, 1, 480))
        val tomorrow = etusivunRyhmat(today.plusDays(1), now, listOf(deleted), open, emptySet())
        assertTrue(tomorrow.isEmpty())
        val past = etusivunRyhmat(yesterday, now, listOf(deleted), reminders, emptySet())
        assertEquals(listOf("A"), past.single().rivit.map { it.nimi })
    }

    @Test
    fun aMedicationSwitchedToAsNeededAfterTakingToday_isGoneToday() {
        // Its reminder is valid until today because today's dose was taken; still
        // hides it on Home today, as a delete would.
        val switched = laake(1, "A", kuinkaUsein = KuinkaUsein.TARVITTAESSA)
        val reminders = listOf(muistutus(1, 1, 480, asti = today))

        assertTrue(etusivunRyhmat(today, now, listOf(switched), reminders, setOf(1L)).isEmpty())
        assertEquals(
            1,
            etusivunRyhmat(yesterday, now, listOf(switched), reminders, emptySet()).size
        )
    }

    @Test
    fun aMedicationNowAsNeeded_keepsItsPastDailyDoses() {
        // Switched to Tarvittaessa today: its reminder ended yesterday.
        val switched = laake(1, "A", kuinkaUsein = KuinkaUsein.TARVITTAESSA)
        val reminders = listOf(muistutus(1, 1, 480, asti = yesterday))

        assertTrue(etusivunRyhmat(today, now, listOf(switched), reminders, emptySet()).isEmpty())
        assertEquals(
            1,
            etusivunRyhmat(yesterday, now, listOf(switched), reminders, emptySet()).size
        )
    }

    // States

    @Test
    fun states_followL2_atTenOClock() {
        val laake = laake(1, "A")
        val reminders = listOf(muistutus(1, 1, 480), muistutus(2, 1, 540), muistutus(3, 1, 720))

        val ryhmat = etusivunRyhmat(today, now, listOf(laake), reminders, setOf(2L))

        assertEquals(
            listOf(AnnoksenTila.OTTAMATTA, AnnoksenTila.OTETTU, AnnoksenTila.TULOSSA),
            ryhmat.map { it.rivit.single().tila }
        )
        assertEquals(Vuorokausi.PAIVA, ryhmat[2].vuorokausi)
    }

    @Test
    fun theProgressDots_followTheListsOrder_byTimeThenName() {
        val b = laake(1, "B")
        val a = laake(2, "A")
        val reminders = listOf(muistutus(1, 1, 1200), muistutus(2, 1, 480), muistutus(3, 2, 480))
        val state = EtusivuUiState(
            ryhmat = etusivunRyhmat(today, now, listOf(b, a), reminders, setOf(2L)),
            loading = false
        )

        // 8.00 A (missed), 8.00 B (taken), 20.00 B (upcoming).
        assertEquals(
            listOf(AnnoksenTila.OTTAMATTA, AnnoksenTila.OTETTU, AnnoksenTila.TULOSSA),
            state.tilat
        )
    }

    @Test
    fun sunAndMoon_switchAt11And17() {
        assertEquals(Vuorokausi.AAMU, AnnosRyhma(10 * 60 + 59, emptyList()).vuorokausi)
        assertEquals(Vuorokausi.PAIVA, AnnosRyhma(11 * 60, emptyList()).vuorokausi)
        assertEquals(Vuorokausi.PAIVA, AnnosRyhma(16 * 60 + 59, emptyList()).vuorokausi)
        assertEquals(Vuorokausi.ILTA, AnnosRyhma(17 * 60, emptyList()).vuorokausi)
    }

    // ViewModel

    @Test
    fun theViewModel_showsTodayFromTheRepository() = runTest {
        val repository = FakeAnnosRepository()
        repository.laakkeet.value = listOf(laake(1, "A"))
        repository.muistutukset.value = listOf(muistutus(1, 1, 480), muistutus(2, 1, 1200))
        repository.otetut.value = mapOf(today to setOf(1L))
        val vm = EtusivuViewModel(repository, Clock.fixed(now.toInstant(), zone))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        val state = vm.uiState.value
        assertEquals(today, state.paiva)
        assertEquals(
            listOf(AnnoksenTila.OTETTU, AnnoksenTila.TULOSSA),
            state.ryhmat.map { it.rivit.single().tila }
        )
    }

    @Test
    fun noDosesToday_isTheEmptyState_andAFailedQueryAnErrorState() = runTest {
        val empty = EtusivuViewModel(FakeAnnosRepository(), Clock.fixed(now.toInstant(), zone))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { empty.uiState.collect {} }
        assertTrue(empty.uiState.value.isEmpty)

        val failing = EtusivuViewModel(
            FakeAnnosRepository().apply { failReads = true },
            Clock.fixed(now.toInstant(), zone)
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            failing.uiState.collect {}
        }
        assertTrue(failing.uiState.value.loadError)
    }

    @Test
    fun aFailedQuery_showsTheError_andTheNextStartOfTheScreenReadsAgain() = runTest {
        // The error used to end the state flow, so Home stayed on it.
        val (repository, vm) = viewModelWith(muistutus(1, 1, 480))
        repository.failReads = true
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertTrue(vm.uiState.value.loadError)

        repository.failReads = false
        vm.refresh()
        assertFalse(vm.uiState.value.loadError)
        assertEquals("A", onlyRow(vm).nimi)
    }

    @Test
    fun aPause_keepsTheSameDaysList_butOneFromBeforeMidnightIsHeldBack() = runTest {
        val clock = MutableClock(now.toInstant(), zone)
        val repository = FakeAnnosRepository()
        repository.laakkeet.value = listOf(laake(1, "A"))
        repository.muistutukset.value = listOf(muistutus(1, 1, 480))
        val vm = EtusivuViewModel(repository, clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            .cancel()
        advanceTimeBy(5_001)

        // The same day: the list is still there, so the screen keeps its scroll.
        assertEquals(1, shownState(vm.uiState.value, vm.today()).ryhmat.size)

        // The next morning it's yesterday's list, shown as loading until the new day's arrives
        //.
        clock.instant = today.plusDays(1).atTime(7, 0).atZone(zone).toInstant()
        assertTrue(shownState(vm.uiState.value, vm.today()).loading)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(today.plusDays(1), shownState(vm.uiState.value, vm.today()).paiva)
    }

    @Test
    fun shownState_holdsBackOnlyAStateFromAnEarlierDay() {
        val list = EtusivuUiState(tanaan = today, paiva = today, loading = false)
        assertTrue(shownState(list, today.plusDays(1)).loading)
        assertEquals(list, shownState(list, today))
        // Midnight while the screen is visible: the new day replaces the day it was shown on.
        assertEquals(list, shownState(list, yesterday))
        // A past day picked in the strip today isn't stale.
        val picked = list.copy(paiva = yesterday)
        assertEquals(picked, shownState(picked, today))
        val error = EtusivuUiState(loading = false, loadError = true)
        assertEquals(error, shownState(error, today))
    }

    @Test
    fun atMidnight_theNewDayStarts_withoutTodaysTicks() = runTest {
        val clock = MutableClock(today.atTime(23, 59).atZone(zone).toInstant(), zone)
        val repository = FakeAnnosRepository()
        repository.laakkeet.value = listOf(laake(1, "A"))
        repository.muistutukset.value = listOf(muistutus(1, 1, 480))
        repository.otetut.value = mapOf(today to setOf(1L))
        val vm = EtusivuViewModel(repository, clock)
        val states = mutableListOf<EtusivuUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.toList(states)
        }
        assertEquals(AnnoksenTila.OTETTU, onlyRow(vm).tila)

        val tomorrow = today.plusDays(1)
        clock.instant = tomorrow.atStartOfDay(zone).toInstant()
        advanceTimeBy(60_001)

        assertEquals(tomorrow, vm.uiState.value.paiva)
        assertEquals(AnnoksenTila.TULOSSA, onlyRow(vm).tila)
        // No state on the new day showed today's tick (the day tag on the taken set).
        val newDay = states.filter { it.paiva == tomorrow }.flatMap { it.ryhmat }
        assertTrue(newDay.flatMap { it.rivit }.none { it.tila == AnnoksenTila.OTETTU })
    }

    // Week strip and header

    @Test
    fun theWeek_isMondayToSunday() {
        val week = (15..21).map { LocalDate.of(2025, 12, it) }
        assertEquals(week, viikko(today)) // A Thursday.
        assertEquals(week, viikko(week.first()))
        assertEquals(week, viikko(week.last()))
    }

    @Test
    fun theState_hasTheWeek_today_andTheCurrentMinute() = runTest {
        val clock = MutableClock(now.plusSeconds(42).toInstant(), zone)
        val (_, vm) = viewModelWith(clock, muistutus(1, 1, 480))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        val state = vm.uiState.value
        assertEquals(viikko(today), state.viikko)
        assertEquals(today, state.tanaan)
        assertEquals(today, state.paiva)
        assertEquals(LocalTime.of(10, 0), state.nyt)
        assertTrue(state.muokattavissa)
    }

    @Test
    fun aPickedPastDay_showsItsOwnTicks_andCanBeTicked() = runTest {
        val (repository, vm) = viewModelWith(muistutus(1, 1, 480))
        repository.otetut.value = mapOf(today to setOf(1L))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.selectDay(yesterday, today)

        assertEquals(yesterday, vm.uiState.value.paiva)
        assertTrue(vm.uiState.value.muokattavissa)
        assertEquals(AnnoksenTila.OTTAMATTA, onlyRow(vm).tila)
        vm.toggle(onlyRow(vm))
        assertEquals(listOf(Write(1, yesterday, true)), repository.writes)
        assertEquals(AnnoksenTila.OTETTU, onlyRow(vm).tila)
    }

    @Test
    fun aFutureDay_isReadOnly() = runTest {
        val (repository, vm) = viewModelWith(muistutus(1, 1, 480))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.selectDay(today.plusDays(1), today)

        assertFalse(vm.uiState.value.muokattavissa)
        assertEquals(AnnoksenTila.TULOSSA, onlyRow(vm).tila)
        vm.toggle(onlyRow(vm))
        assertEquals(emptyList<Write>(), repository.writes)
    }

    @Test
    fun switchingDays_neverShowsADayWithAnotherDaysTicks() = runTest {
        val (repository, vm) = viewModelWith(muistutus(1, 1, 480))
        repository.otetut.value = mapOf(today to setOf(1L))
        val states = mutableListOf<EtusivuUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.toList(states)
        }

        vm.selectDay(yesterday, today)
        vm.selectDay(today, today)

        val tilat = states.filter { !it.loading }
            .map { it.paiva to it.ryhmat.single().rivit.single().tila }
        assertEquals(
            listOf(
                today to AnnoksenTila.OTETTU,
                yesterday to AnnoksenTila.OTTAMATTA,
                today to AnnoksenTila.OTETTU
            ),
            tilat
        )
    }

    @Test
    fun aDayPickedYesterday_givesWayToTheNewDay_andTodayFollowsMidnight() = runTest {
        val clock = MutableClock(today.atTime(23, 59).atZone(zone).toInstant(), zone)
        val (_, vm) = viewModelWith(clock, muistutus(1, 1, 480))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.selectDay(yesterday, today)

        clock.instant = today.plusDays(1).atStartOfDay(zone).toInstant()
        advanceTimeBy(60_001)
        assertEquals(today.plusDays(1), vm.uiState.value.paiva)

        // Today, picked again, also follows the next midnight.
        vm.selectDay(today.plusDays(1), today.plusDays(1))
        clock.instant = today.plusDays(2).atStartOfDay(zone).toInstant()
        advanceTimeBy(60_001)
        assertEquals(today.plusDays(2), vm.uiState.value.paiva)
    }

    @Test
    fun aPickThatGaveWay_staysGone_whenTheClockMovesBackAndReachesItsDayAgain() = runTest {
        // Tomorrow picked today at 10.00; the clock then moves back to yesterday
        // 23.59 (a westward zone change) and passes midnight into today again.
        val clock = MutableClock(now.toInstant(), zone)
        val (_, vm) = viewModelWith(clock, muistutus(1, 1, 480))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.selectDay(today.plusDays(1), today)
        assertEquals(today.plusDays(1), vm.uiState.value.paiva)

        clock.instant = yesterday.atTime(23, 59).atZone(zone).toInstant()
        advanceTimeBy(60_001)
        assertEquals(yesterday, vm.uiState.value.paiva)

        clock.instant = today.atStartOfDay(zone).toInstant()
        advanceTimeBy(60_001)
        assertEquals(today, vm.uiState.value.tanaan)
        assertEquals(today, vm.uiState.value.paiva)
    }

    @Test
    fun aTapJustAfterMidnight_beforeTheTick_picksFromTheOldWeek_andTheNewDayWins() = runTest {
        // Sunday 21.12. at 23.59; the user taps Sunday (today on the strip), and
        // the tap runs at 00.00.00,5 Monday, before the ticker has noticed midnight.
        val sunday = LocalDate.of(2025, 12, 21)
        val monday = sunday.plusDays(1)
        val clock = MutableClock(sunday.atTime(23, 59).atZone(zone).toInstant(), zone)
        val (_, vm) = viewModelWith(clock, muistutus(1, 1, 480))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val strip = vm.uiState.value
        assertEquals(sunday, strip.tanaan)

        clock.instant = monday.atStartOfDay(zone).toInstant().plusMillis(500)
        vm.selectDay(sunday, strip.tanaan!!)
        advanceTimeBy(60_001)

        assertEquals(monday, vm.uiState.value.paiva)
        assertEquals(viikko(monday), vm.uiState.value.viikko)
    }

    // Ticking

    @Test
    fun tappingARow_ticksItOnItsDay_andTappingAgainUnticksIt() = runTest {
        val (repository, vm) = viewModelWith(muistutus(1, 1, 1200))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.toggle(onlyRow(vm))
        assertEquals(listOf(Write(1, today, true)), repository.writes)
        assertEquals(AnnoksenTila.OTETTU, onlyRow(vm).tila)

        vm.toggle(onlyRow(vm))
        assertEquals(Write(1, today, false), repository.writes.last())
        assertEquals(AnnoksenTila.TULOSSA, onlyRow(vm).tila)
    }

    @Test
    fun aMissedDose_canBeTickedLate() = runTest {
        val (repository, vm) = viewModelWith(muistutus(1, 1, 480))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(AnnoksenTila.OTTAMATTA, onlyRow(vm).tila)

        vm.toggle(onlyRow(vm))

        assertEquals(listOf(Write(1, today, true)), repository.writes)
        assertEquals(AnnoksenTila.OTETTU, onlyRow(vm).tila)
    }

    @Test
    fun pastDays_canBeTicked_futureDaysAreReadOnly() = runTest {
        val (repository, vm) = viewModelWith(muistutus(1, 1, 480))
        val row = AnnosRivi(1, today, "A", "5", "mg", AnnoksenTila.TULOSSA)

        vm.toggle(row.copy(paiva = today.plusDays(1)))
        assertEquals(emptyList<Write>(), repository.writes)

        vm.toggle(row.copy(paiva = yesterday, tila = AnnoksenTila.OTTAMATTA))
        assertEquals(listOf(Write(1, yesterday, true)), repository.writes)
    }

    @Test
    fun eachFailedTick_isANewMessage_andEachNewTapStartsWithoutIt() = runTest {
        val (repository, vm) = viewModelWith(muistutus(1, 1, 1200))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        repository.failWrites = true

        vm.toggle(onlyRow(vm))
        val first = vm.uiState.value.toggleErrorId
        assertTrue(first != 0L)
        assertEquals(AnnoksenTila.TULOSSA, onlyRow(vm).tila)
        vm.toggleErrorShown(first)
        assertEquals(0L, vm.uiState.value.toggleErrorId)

        // Two failures in a row are two messages, even within one frame,
        // and a late "shown" for the older one doesn't hide the newer one.
        vm.toggle(onlyRow(vm))
        val second = vm.uiState.value.toggleErrorId
        vm.toggle(onlyRow(vm))
        val third = vm.uiState.value.toggleErrorId
        assertTrue(second != 0L && third != 0L && third != second)
        vm.toggleErrorShown(second)
        assertEquals(third, vm.uiState.value.toggleErrorId)

        repository.failWrites = false
        vm.toggle(onlyRow(vm))
        assertEquals(0L, vm.uiState.value.toggleErrorId)
        assertEquals(AnnoksenTila.OTETTU, onlyRow(vm).tila)
    }

    @Test
    fun refresh_restartsTheClock_whenTheTickerSleptThroughTheTime() = runTest {
        // The ticker's delay counts uptime, which stops while the phone sleeps.
        val clock = MutableClock(now.plusSeconds(30).toInstant(), zone)
        val (_, vm) = viewModelWith(clock, muistutus(1, 1, 630))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(AnnoksenTila.TULOSSA, onlyRow(vm).tila)

        clock.instant = now.plusHours(1).toInstant() // No virtual time has passed.
        assertEquals(LocalTime.of(10, 0), vm.uiState.value.nyt)
        vm.refresh()

        assertEquals(LocalTime.of(11, 0), vm.uiState.value.nyt)
        assertEquals(AnnoksenTila.OTTAMATTA, onlyRow(vm).tila)
    }

    /** A ViewModel at 10.00 today over one daily medication with [reminders]. */
    private fun viewModelWith(vararg reminders: Muistutus) =
        viewModelWith(Clock.fixed(now.toInstant(), zone), *reminders)

    /** A ViewModel at [clock]'s time over one daily medication with [reminders]. */
    private fun viewModelWith(
        clock: Clock,
        vararg reminders: Muistutus
    ): Pair<FakeAnnosRepository, EtusivuViewModel> {
        val repository = FakeAnnosRepository()
        repository.laakkeet.value = listOf(laake(1, "A"))
        repository.muistutukset.value = reminders.toList()
        return repository to EtusivuViewModel(repository, clock)
    }

    private fun onlyRow(vm: EtusivuViewModel) = vm.uiState.value.ryhmat.single().rivit.single()

    private fun laake(
        id: Long,
        nimi: String,
        kuinkaUsein: KuinkaUsein = KuinkaUsein.PAIVITTAIN,
        end: LocalDate? = null
    ) = Laake(
        laakeId = id,
        nimi = nimi,
        tyyppiId = 1,
        annosmaara = "5",
        yksikko = "mg",
        kuinkaUsein = kuinkaUsein,
        aloituspv = today.minusDays(10),
        lopetuspv = end
    )

    private fun muistutus(
        id: Long,
        laakeId: Long,
        aika: Int,
        alkaen: LocalDate = today.minusDays(10),
        asti: LocalDate? = null
    ) = Muistutus(
        muistutusId = id,
        laakeId = laakeId,
        aika = aika,
        voimassaAlkaen = alkaen,
        voimassaAsti = asti
    )
}

/** A clock the test can move. */
private class MutableClock(var instant: Instant, private val zone: ZoneId) : Clock() {
    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)

    override fun instant(): Instant = instant
}

/** One [AnnosRepository.setOtettu] call. */
private data class Write(val muistutusId: Long, val paiva: LocalDate, val otettu: Boolean)

/**
 * In-memory [AnnosRepository]; [failReads] makes the medication query fail when it starts and
 * [failWrites] the ticks. Saved ticks are recorded in [writes].
 */
private class FakeAnnosRepository : AnnosRepository {
    val laakkeet = MutableStateFlow<List<Laake>>(emptyList())
    val muistutukset = MutableStateFlow<List<Muistutus>>(emptyList())
    val otetut = MutableStateFlow<Map<LocalDate, Set<Long>>>(emptyMap())
    val writes = mutableListOf<Write>()
    var failReads = false
    var failWrites = false

    override suspend fun setOtettu(muistutusId: Long, paiva: LocalDate, otettu: Boolean) {
        if (failWrites) throw IllegalStateException("database unavailable")
        writes += Write(muistutusId, paiva, otettu)
        otetut.update { all ->
            val day = all[paiva].orEmpty()
            all + (paiva to if (otettu) day + muistutusId else day - muistutusId)
        }
    }

    override fun observeLaakkeet(): Flow<List<Laake>> = flow {
        if (failReads) throw IllegalStateException("database unavailable")
        emitAll(laakkeet)
    }

    override fun observeMuistutukset(): Flow<List<Muistutus>> = muistutukset

    override fun observeOtetut(paiva: LocalDate): Flow<Set<Long>> =
        otetut.map { it[paiva].orEmpty() }
}
