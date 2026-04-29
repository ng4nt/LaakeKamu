package fi.hh.laakekamu.ui.etusivu

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * Tapping a dose row ticks it (✓ and a Laakkeenotto row for today) and tapping
 * it again unticks it. The progress percentage part of comes with the progress.
 */
@RunWith(AndroidJUnit4::class)
class EtusivuScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2025, 12, 18)
    private lateinit var db: AppDatabase
    private var reminder = 0L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).addCallback(SeedCallback).build()
        val zone = ZoneId.of("Europe/Helsinki")
        // 10.00, so the 20.00 dose is upcoming.
        val clock = Clock.fixed(today.atTime(10, 0).atZone(zone).toInstant(), zone)
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
        val viewModel = EtusivuViewModel(RoomAnnosRepository({ db }, clock), clock)
        compose.setContent {
            LaakeKamuTheme {
                EtusivuScreen(
                    onAdd = {},
                    snackbarHostState = remember { SnackbarHostState() },
                    viewModel = viewModel
                )
            }
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private val doseRow = hasText("A iltalääke", substring = true) and isToggleable()

    /** The list comes from Room's own thread, which Compose's idling doesn't wait for. */
    private fun waitFor(matcher: SemanticsMatcher) =
        compose.waitUntil(5_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().size == 1 }

    @Test
    fun tappingADoseRow_ticksIt_andTappingAgainUnticksIt() {
        waitFor(doseRow)
        val row = compose.onNode(doseRow)
        row.assertIsOff().assert(hasState("tulossa"))

        row.performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(isOn()).fetchSemanticsNodes().size == 1 }
        row.assertIsOn().assert(hasState("otettu"))
        assertEquals(setOf(reminder), takenToday())

        row.performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(isOn()).fetchSemanticsNodes().isEmpty() }
        row.assertIsOff().assert(hasState("tulossa"))
        assertEquals(emptySet<Long>(), takenToday())
    }

    /** the week strip shows another day; a future day's doses can't be ticked. */
    @Test
    fun theWeekStrip_showsAnotherDay_andAFutureDayIsReadOnly() {
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

    private fun takenToday(): Set<Long> = takenOn(today)

    private fun takenOn(day: LocalDate): Set<Long> = runBlocking {
        db.laakkeenottoDao().observeBetween(day, day).first().map { it.muistutusId }.toSet()
    }
}
