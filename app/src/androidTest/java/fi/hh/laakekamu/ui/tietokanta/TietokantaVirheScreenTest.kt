package fi.hh.laakekamu.ui.tietokanta

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.hh.laakekamu.ui.theme.LaakeKamuTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** "Aloita alusta" deletes nothing until the dialog is confirmed. */
@RunWith(AndroidJUnit4::class)
class TietokantaVirheScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun startOver_runsOnlyAfterTheConfirmation_andCancelKeepsEverything() {
        var retries = 0
        var startOvers = 0
        compose.setContent {
            LaakeKamuTheme {
                TietokantaVirheScreen(
                    voiAloittaaAlusta = true,
                    enabled = true,
                    onRetry = { retries++ },
                    onStartOver = { startOvers++ }
                )
            }
        }
        compose.onNodeWithText("Tietoja ei voitu avata").assertExists()
        compose.onNodeWithText("Yritä uudelleen").performClick()
        assertEquals(1, retries)

        compose.onNodeWithText("Aloita alusta").performClick()
        compose.onNodeWithText("Aloitetaanko alusta?").assertExists()
        compose.onNodeWithText("Peruuta").performClick()
        compose.onNodeWithText("Aloitetaanko alusta?").assertDoesNotExist()
        assertEquals(0, startOvers)

        compose.onNodeWithText("Aloita alusta").performClick()
        compose.onNode(hasText("Aloita alusta") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("Aloitetaanko alusta?").assertDoesNotExist()
        assertEquals(1, startOvers)
        assertEquals(1, retries)
    }

    /** When the data is probably fine (e.g. full storage) only the retry is shown. */
    @Test
    fun withoutStartOver_onlyTheRetryIsOffered() {
        compose.setContent {
            LaakeKamuTheme {
                TietokantaVirheScreen(
                    voiAloittaaAlusta = false,
                    enabled = true,
                    onRetry = {},
                    onStartOver = {}
                )
            }
        }
        compose.onNodeWithText("vapaata tallennustilaa", substring = true).assertExists()
        compose.onNodeWithText("Yritä uudelleen").assertExists()
        compose.onNodeWithText("Aloita alusta").assertDoesNotExist()
    }
}
