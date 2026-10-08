package fi.hh.laakekamu.ui.oirepaivakirja

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.hh.laakekamu.data.AppDatabase
import fi.hh.laakekamu.data.OireRepository
import fi.hh.laakekamu.data.RoomPaivakirjaRepository
import fi.hh.laakekamu.data.SeedCallback
import fi.hh.laakekamu.data.entity.Oiremerkinta
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

/**
 * Rule 7: a symptom is deleted from the diary only after the confirmation. "Peruuta"
 * keeps it; "Poista" in the dialog deletes the row from the database for good.
 */
@RunWith(AndroidJUnit4::class)
class OirepaivakirjaScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("Europe/Helsinki")
    private val today = LocalDate.of(2025, 12, 18)
    private val clock = Clock.fixed(today.atTime(10, 0).atZone(zone).toInstant(), zone)
    private lateinit var db: AppDatabase
    private lateinit var oireet: OireRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).addCallback(SeedCallback).build()
        oireet = OireRepository { db }
        runBlocking {
            oireet.add(
                Oiremerkinta(
                    kuvaus = "Huimaus",
                    laakeId = null,
                    aloituspv = today,
                    lopetuspv = null
                )
            )
        }
        val viewModel = OirepaivakirjaViewModel(
            SavedStateHandle(),
            RoomPaivakirjaRepository { db },
            oireet,
            clock
        )
        compose.setContent {
            LaakeKamuTheme {
                OirepaivakirjaScreen(
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

    @Test
    fun deleting_asksFirst_cancelKeepsIt_confirmDeletesIt() {
        waitFor(hasText("Huimaus"))
        compose.onNodeWithContentDescription("torstai 18.12.2025, tänään, oire kirjattu")
            .assertIsDisplayed()

        compose.onNodeWithContentDescription("Poista oire Huimaus").performScrollTo().performClick()
        compose.onNodeWithText("Poistetaanko oire?").assertIsDisplayed()
        compose.onNodeWithText("Peruuta").performClick()
        compose.waitUntil(5_000) { nodes(isInDialog()).isEmpty() }
        assertEquals(1, stored())

        compose.onNodeWithContentDescription("Poista oire Huimaus").performScrollTo().performClick()
        compose.onNode(hasText("Poista") and isInDialog()).performClick()

        waitFor(hasText("Ei kirjattuja oireita"))
        assertEquals(0, stored())
        compose.onNodeWithContentDescription("torstai 18.12.2025, tänään").assertIsDisplayed()
    }

    private fun isInDialog() = hasAnyAncestor(isDialog())

    private fun nodes(matcher: SemanticsMatcher) = compose.onAllNodes(matcher).fetchSemanticsNodes()

    /** Room answers on its own thread, which Compose's idling doesn't wait for. */
    private fun waitFor(matcher: SemanticsMatcher) =
        compose.waitUntil(5_000) { nodes(matcher).size == 1 }

    private fun stored(): Int = runBlocking { oireet.observeAll().first().size }
}
