package fi.hh.laakekamu.ui.format

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Gives [content] a Finnish [LocalConfiguration] whatever the device language. The M3 date
 * picker reads its locale from `LocalConfiguration.current.locales[0]`, so month names, weekday
 * initials and the first day of the week (Monday) become Finnish.
 */
@Composable
fun ProvideFinnishLocale(content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    val finnish = remember(configuration) {
        Configuration(configuration).apply { setLocale(SUOMI) }
    }
    CompositionLocalProvider(LocalConfiguration provides finnish, content = content)
}
