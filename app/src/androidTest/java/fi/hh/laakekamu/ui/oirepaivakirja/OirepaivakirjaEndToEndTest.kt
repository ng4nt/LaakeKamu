package fi.hh.laakekamu.ui.oirepaivakirja

import android.Manifest
import android.os.Build
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fi.hh.laakekamu.data.AppDatabase
import fi.hh.laakekamu.data.OireRepository
import fi.hh.laakekamu.data.RoomAnnosRepository
import fi.hh.laakekamu.data.RoomLaakeRepository
import fi.hh.laakekamu.data.RoomPaivakirjaRepository
import fi.hh.laakekamu.data.SeedCallback
import fi.hh.laakekamu.ui.etusivu.EtusivuScreen
import fi.hh.laakekamu.ui.etusivu.EtusivuViewModel
import fi.hh.laakekamu.ui.lisaalaake.LisaaLaakeScreen
import fi.hh.laakekamu.ui.lisaalaake.LisaaLaakeViewModel
import fi.hh.laakekamu.ui.theme.LaakeKamuTheme
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A medication added through the wizard appears on Etusivu; ticking it there
 * makes the day complete, and the diary shows the taken dose and the ✓ on that day.
 *
 * The three real screens share one in-memory database and a fixed clock (18.12.2025 10.00).
 * They are switched by the test, like the bottom bar does, because the app's own navigation
 * builds its ViewModels on the app database.
 */
@RunWith(AndroidJUnit4::class)
class OirepaivakirjaEndToEndTest {
    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("Europe/Helsinki")
    private val clock =
        Clock.fixed(LocalDate.of(2025, 12, 18).atTime(10, 0).atZone(zone).toInstant(), zone)
    private lateinit var db: AppDatabase

    private enum class Naytto { LISAA, ETUSIVU, PAIVAKIRJA }

    private var naytto by mutableStateOf(Naytto.LISAA)

    @Before
    fun setUp() {
        // Saving a daily medication asks for notifications on Android 13+. The system
        // dialog would cover the app and hide its Compose hierarchy, so grant it first.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.uiAutomation.grantRuntimePermission(
                instrumentation.targetContext.packageName,
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).addCallback(SeedCallback).build()
        val lisaa =
            LisaaLaakeViewModel(SavedStateHandle(), RoomLaakeRepository({ db }, clock), clock)
        val etusivu = EtusivuViewModel(RoomAnnosRepository({ db }, clock), clock)
        val paivakirja = OirepaivakirjaViewModel(
            SavedStateHandle(),
            RoomPaivakirjaRepository { db },
            OireRepository { db },
            clock
        )
        compose.setContent {
            LaakeKamuTheme {
                when (naytto) {
                    Naytto.LISAA -> LisaaLaakeScreen(
                        onClose = {},
                        onSaved = { naytto = Naytto.ETUSIVU },
                        viewModel = lisaa
                    )
                    Naytto.ETUSIVU -> EtusivuScreen(
                        onAdd = {},
                        snackbarHostState = remember { SnackbarHostState() },
                        viewModel = etusivu
                    )
                    Naytto.PAIVAKIRJA -> OirepaivakirjaScreen(
                        onAdd = {},
                        snackbarHostState = remember { SnackbarHostState() },
                        viewModel = paivakirja
                    )
                }
            }
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun addTickAndSeeTheDayCompleteInTheDiary() {
        // The wizard, step 1.
        compose.onNodeWithContentDescription("Lääke").performTextInput("A aamulääke")
        compose.onNodeWithContentDescription("Tyyppi: Valitse lääkkeen tyyppi").performClick()
        compose.onNodeWithText("tabletti").performClick()
        compose.onNodeWithContentDescription("Annosmäärä").performTextInput("5")
        compose.onNodeWithContentDescription("Yksikkö: Valitse").performScrollTo().performClick()
        compose.onNodeWithText("mg").performClick()
        compose.onNodeWithText("Seuraava vaihe").performScrollTo().performClick()

        // Step 2: the picker's first time (0.00), daily, starting today (the default).
        waitFor(hasContentDescription("Aika 1: Valitse aika"))
        compose.onNodeWithContentDescription("Aika 1: Valitse aika").performClick()
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithContentDescription("Kuinka usein otat lääkkeen?: Valitse")
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("Päivittäin").performClick()
        compose.onNodeWithText("Valmis!").performScrollTo().performClick()

        // Etusivu: the dose is there; tick it.
        val doseRow = hasText("A aamulääke", substring = true) and isToggleable()
        waitFor(doseRow)
        compose.onNode(doseRow).performClick()
        waitFor(isOn())

        // The diary: today is complete and the taken dose is listed.
        compose.runOnUiThread { naytto = Naytto.PAIVAKIRJA }
        waitFor(hasText("A aamulääke 5 mg (klo 0.00)"))
        compose.onNodeWithText("Tänään otetut lääkkeet").assertIsDisplayed()
        compose.onNodeWithContentDescription(
            "torstai 18.12.2025, tänään, kaikki lääkkeet otettu"
        ).assertIsDisplayed()
    }

    /** Room answers on its own thread, which Compose's idling doesn't wait for. */
    private fun waitFor(matcher: SemanticsMatcher) =
        compose.waitUntil(5_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().size == 1 }
}
