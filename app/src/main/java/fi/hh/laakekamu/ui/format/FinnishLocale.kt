package fi.hh.laakekamu.ui.format

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * Gives [content] a Finnish [LocalConfiguration] and [LocalContext] whatever the device language
 *. The M3 pickers read their locale (month names, weekdays, Monday first) from the
 * configuration and their own texts ("Valittu päivämäärä", "Tunti") from the context's
 * resources.
 *
 * A dialog is its own window that provides both locals again from the activity, so call this
 * inside the dialog's content too.
 */
@Composable
fun ProvideFinnishLocale(content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val finnish = remember(configuration) {
        Configuration(configuration).apply { setLocale(SUOMI) }
    }
    val finnishContext = remember(context, finnish) { FinnishContext(context, finnish) }
    CompositionLocalProvider(
        LocalConfiguration provides finnish,
        LocalContext provides finnishContext,
        content = content
    )
}

/** [base] with Finnish resources; the activity stays in the context chain. */
private class FinnishContext(base: Context, configuration: Configuration) : ContextWrapper(base) {
    private val finnishResources = base.createConfigurationContext(configuration).resources

    override fun getResources(): Resources = finnishResources
}
