package fi.hh.laakekamu.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import fi.hh.laakekamu.R

/**
 * The three screens reachable from the bottom bar, in the bar's order (Figma: medicine | home |
 * calendar). Routes are plain strings; type-safe routes would need the kotlinx-serialization
 * plugin, which isn't in the dependency path.
 */
enum class MainDestination(
    val route: String,
    @param:DrawableRes val icon: Int,
    @param:StringRes val label: Int
) {
    LAAKELISTA("laakelista", R.drawable.ic_medicine, R.string.nav_laakelista),
    ETUSIVU("etusivu", R.drawable.ic_home, R.string.nav_etusivu),
    OIREPAIVAKIRJA("oirepaivakirja", R.drawable.ic_calendar, R.string.nav_oirepaivakirja);

    companion object {
        fun isMain(route: String?): Boolean = entries.any { it.route == route }
    }
}
