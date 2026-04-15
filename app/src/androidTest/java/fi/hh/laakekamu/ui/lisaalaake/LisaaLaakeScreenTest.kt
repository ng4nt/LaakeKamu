package fi.hh.laakekamu.ui.lisaalaake

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
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
import fi.hh.laakekamu.data.AppDatabase
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Wizard step 1 shows its validation errors and the duplicate message. */
/** A field whose semantics error is [message]. */
private fun hasError(message: String) =
    SemanticsMatcher.expectValue(SemanticsProperties.Error, message)

@RunWith(AndroidJUnit4::class)
class LisaaLaakeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repository: RoomLaakeRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).addCallback(SeedCallback).build()
        val zone = ZoneId.of("Europe/Helsinki")
        val clock = Clock.fixed(LocalDate.of(2025, 12, 18).atStartOfDay(zone).toInstant(), zone)
        repository = RoomLaakeRepository({ db }, clock)
        runBlocking {
            val tyyppi = repository.observeTyypit().first().first()
            repository.add(
                Laake(
                    nimi = "Burana",
                    tyyppiId = tyyppi.tyyppiId,
                    annosmaara = "400",
                    yksikko = "mg",
                    kuinkaUsein = KuinkaUsein.TARVITTAESSA,
                    aloituspv = LocalDate.of(2025, 12, 1),
                    lopetuspv = null
                ),
                emptyList()
            )
        }
        val viewModel = LisaaLaakeViewModel(SavedStateHandle(), repository, clock)
        compose.setContent {
            LaakeKamuTheme {
                LisaaLaakeScreen(onClose = {}, onSaved = {}, viewModel = viewModel)
            }
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun emptyFields_showAnErrorUnderEachField() {
        compose.onNodeWithText("Seuraava vaihe").performScrollTo().performClick()

        // Each field carries its error for TalkBack (the visible text is hidden from it).
        compose.onNode(hasError("Lääkkeen nimi puuttuu.")).performScrollTo().assertIsDisplayed()
        compose.onNode(hasError("Valitse lääkkeen tyyppi.")).performScrollTo().assertIsDisplayed()
        compose.onNode(hasError("Annosmäärä puuttuu.")).performScrollTo().assertIsDisplayed()
        compose.onNode(hasError("Valitse yksikkö.")).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun duplicateName_showsTheL5Message() {
        compose.onNodeWithContentDescription("Lääke").performTextInput("burana")
        compose.onNodeWithText("Seuraava vaihe").performScrollTo().performClick()

        compose.onNode(hasError("Lääke \"burana\" on jo lisätty.")).performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun validStep1_movesToStep2() {
        compose.onNodeWithContentDescription("Lääke").performTextInput("A aamulääke")
        compose.onNodeWithContentDescription("Tyyppi: Valitse lääkkeen tyyppi").performClick()
        compose.onNodeWithText("tabletti").performClick()
        compose.onNodeWithContentDescription("Annosmäärä").performTextInput("5")
        compose.onNodeWithContentDescription("Yksikkö: Valitse").performScrollTo().performClick()
        compose.onNodeWithText("mg").performClick()
        compose.onNodeWithText("Seuraava vaihe").performScrollTo().performClick()

        // Step 1's main button is gone; step 2 shows its back button.
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTextCount("Seuraava vaihe") == 0
        }
    }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(
    text: String
): Int = onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
