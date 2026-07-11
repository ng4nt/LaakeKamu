package fi.hh.laakekamu.ui.format

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.hh.laakekamu.R
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `%d` in a string resource follows the resources' locale.
 * The app's activity uses [withFinnishResources], so its numbers stay Latin on any phone.
 */
@RunWith(AndroidJUnit4::class)
class FinnishResourcesTest {
    private val app: Context = ApplicationProvider.getApplicationContext()

    private fun Context.inLanguage(tag: String): Context = createConfigurationContext(
        Configuration(resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
    )

    @Test
    fun persianResources_formatPersianDigits() {
        // The problem itself, so the next test proves the fix and not a quiet device.
        assertEquals("۵ %", app.inLanguage("fa").getString(R.string.prosentti, 5))
    }

    @Test
    fun withFinnishResources_formatsLatinDigits_onAPersianDevice() {
        val context = app.inLanguage("fa").withFinnishResources()

        assertEquals("5 %", context.getString(R.string.prosentti, 5))
        assertEquals(Locale.forLanguageTag("fi-FI"), context.resources.configuration.locales[0])
    }
}
