package fi.hh.laakekamu.ui.components

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.hh.laakekamu.ui.theme.LaakeKamuTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The pickers are Finnish whatever the device language. Meaningful on a device
 * in another language; the test emulator runs in English.
 */
@RunWith(AndroidJUnit4::class)
class FinnishPickerTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun datePicker_textsAndMonthsAreFinnish_andTheWeekStartsOnMonday() {
        compose.setContent { LaakeKamuTheme { LkDateField("Aloituspäivä", null, {}) } }
        compose.onNode(hasContentDescription("Aloituspäivä", substring = true)).performClick()

        compose.onNodeWithText("Valittu päivämäärä").assertExists()
        compose.onNodeWithContentDescription("Vaihda tekstinsyöttötilaan").assertExists()
        compose.onNodeWithContentDescription("maanantai").assertExists()
        compose.onNode(
            hasContentDescription("Selected date", substring = true)
        ).assertDoesNotExist()
    }

    @Test
    fun timePicker_textsAreFinnish() {
        compose.setContent { LaakeKamuTheme { LkTimeField(480, {}, "Aika", "Valitse aika") } }
        compose.onNode(hasContentDescription("Aika", substring = true)).performClick()

        compose.onNodeWithContentDescription("tuntien osalta").assertExists()
        compose.onNodeWithContentDescription("minuuttien osalta").assertExists()
        compose.onNode(hasContentDescription("Vaihda ajan syöttämiseen", substring = true))
            .assertExists()
    }
}
