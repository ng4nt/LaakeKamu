package fi.hh.laakekamu.ui.etusivu

import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.hh.laakekamu.data.AppDatabase
import fi.hh.laakekamu.data.RoomAnnosRepository
import fi.hh.laakekamu.data.RoomLaakeRepository
import fi.hh.laakekamu.data.SeedCallback
import fi.hh.laakekamu.data.entity.Laake
import fi.hh.laakekamu.domain.KuinkaUsein
import fi.hh.laakekamu.ui.theme.LaakeKamuTheme
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A row whose TalkBack state is [tila]. */
private fun hasState(tila: String) =
    SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, tila)

/**
 * Tapping a dose row ticks it (✓, a Laakkeenotto row for today and the
 * progress at 100 %) and tapping it again unticks it (back to 0 %).
 */
@RunWith(AndroidJUnit4::class)
class EtusivuScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2025, 12, 18)
    private val zone = ZoneId.of("Europe/Helsinki")

    // 10.00, so the 20.00 dose is upcoming.
    private val clock = MovableClock(today.atTime(10, 0).atZone(zone).toInstant(), zone)
    private lateinit var db: AppDatabase
    private var reminder = 0L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).addCallback(SeedCallback).build()
        val laakkeet = RoomLaakeRepository({ db }, clock)
        runBlocking {
            val tyyppi = laakkeet.observeTyypit().first().first()
            val id = laakkeet.add(
                Laake(
                    nimi = "A iltalääke",
                    tyyppiId = tyyppi.tyyppiId,
                    annosmaara = "5",
                    yksikko = "mg",
                    kuinkaUsein = KuinkaUsein.PAIVITTAIN,
                    aloituspv = today,
                    lopetuspv = null
                ),
                listOf(20 * 60)
            )
            reminder = db.muistutusDao().getByLaake(id).single().muistutusId
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** Shows Etusivu in [modifier], under [lifecycleOwner] when given (else the activity's). */
    private fun show(modifier: Modifier = Modifier, lifecycleOwner: LifecycleOwner? = null) {
        val viewModel = EtusivuViewModel(RoomAnnosRepository({ db }, clock), clock)
        compose.setContent {
            LaakeKamuTheme {
                CompositionLocalProvider(
                    LocalLifecycleOwner provides (lifecycleOwner ?: LocalLifecycleOwner.current)
                ) {
                    EtusivuScreen(
                        onAdd = {},
                        snackbarHostState = remember { SnackbarHostState() },
                        modifier = modifier,
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    private val doseRow = hasText("A iltalääke", substring = true) and isToggleable()

    /** The list comes from Room's own thread, which Compose's idling doesn't wait for. */
    private fun waitFor(matcher: SemanticsMatcher) =
        compose.waitUntil(5_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().size == 1 }

    @Test
    fun tappingADoseRow_ticksIt_andTappingAgainUnticksIt() {
        show()
        waitFor(doseRow)
        val row = compose.onNode(doseRow)
        row.assertIsOff().assert(hasState("tulossa"))
        progress("Edistys 0 %: tulossa 1").assertIsDisplayed()

        row.performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(isOn()).fetchSemanticsNodes().size == 1 }
        row.assertIsOn().assert(hasState("otettu"))
        progress("Edistys 100 %: otettu 1").assertIsDisplayed()
        assertEquals(setOf(reminder), takenToday())

        row.performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(isOn()).fetchSemanticsNodes().isEmpty() }
        row.assertIsOff().assert(hasState("tulossa"))
        progress("Edistys 0 %: tulossa 1").assertIsDisplayed()
        assertEquals(emptySet<Long>(), takenToday())
    }

    /** The progress (dots and %) is one TalkBack node with this description. */
    private fun progress(kuvaus: String) = compose.onNodeWithContentDescription(kuvaus)

    /** the week strip shows another day; a future day's doses can't be ticked. */
    @Test
    fun theWeekStrip_showsAnotherDay_andAFutureDayIsReadOnly() {
        show()
        waitFor(doseRow)
        compose.onNodeWithText("Tänään on torstai 18.12.", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("torstai 18.12., tänään").assertIsSelected()

        compose.onNodeWithContentDescription("perjantai 19.12.").performClick()
        waitFor(hasText("Perjantai 19.12."))
        compose.onNodeWithContentDescription("perjantai 19.12.").assertIsSelected()
        compose.onNode(doseRow).assertIsNotEnabled().performClick()
        assertEquals(emptySet<Long>(), takenOn(today.plusDays(1)))

        compose.onNodeWithContentDescription("torstai 18.12., tänään").performClick()
        waitFor(hasText("Tänään on torstai 18.12.", substring = true))
        compose.onNode(doseRow).assertIsEnabled()
    }

    /** On a narrow screen the cells share the width instead of squeezing Sunday. */
    @Test
    fun theWeekStrip_onANarrowScreen_keepsSundayAsWideAsTheOthers() {
        show(Modifier.width(320.dp))
        waitFor(doseRow)

        // 318dp of strip (1dp edges) / 7 cells = 45.4dp each; before Sunday got 18dp.
        compose.onNodeWithContentDescription("sunnuntai 21.12.").assertWidthIsAtLeast(45.dp)
        compose.onNodeWithContentDescription("maanantai 15.12.").assertWidthIsAtLeast(45.dp)
    }

    /**
     * Coming back to the screen restarts the clock at once. Without
     * that, the ticker would still be waiting for the next whole minute after the pause.
     */
    @Test
    fun comingBack_showsTheCurrentTimeAtOnce() {
        val owner = TestLifecycleOwner()
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        show(lifecycleOwner = owner)
        waitFor(hasText("klo 10.00", substring = true))

        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.CREATED }
        clock.instant = clock.instant.plus(Duration.ofHours(1))
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }

        // Well under the ~60 s the old ticker would still wait.
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("klo 11.00", substring = true))
                .fetchSemanticsNodes().size == 1
        }
    }

    private fun takenToday(): Set<Long> = takenOn(today)

    private fun takenOn(day: LocalDate): Set<Long> = runBlocking {
        db.laakkeenottoDao().observeBetween(day, day).first().map { it.muistutusId }.toSet()
    }
}

/** A clock the test can move; the ViewModel reads it on every tick. */
private class MovableClock(@Volatile var instant: Instant, private val zone: ZoneId) : Clock() {
    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MovableClock(instant, zone)

    override fun instant(): Instant = instant
}

/** A lifecycle the test moves by hand, standing in for the screen going away and back. */
private class TestLifecycleOwner : LifecycleOwner {
    val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
}
