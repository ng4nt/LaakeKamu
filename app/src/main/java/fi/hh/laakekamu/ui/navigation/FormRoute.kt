package fi.hh.laakekamu.ui.navigation

import fi.hh.laakekamu.ui.lisaaoire.LisaaOireViewModel
import java.time.LocalDate

/** Routes of the full-screen forms (no bottom bar). */
object FormRoute {
    const val LISAA_LAAKE = "lisaa_laake"

    /** The symptom form; its argument is the start date's default as an epoch day. */
    const val LISAA_OIRE = "lisaa_oire/{${LisaaOireViewModel.ARG_ALKAA}}"

    /** [LISAA_OIRE] starting on [alkaa]. */
    fun lisaaOire(alkaa: LocalDate): String = "lisaa_oire/${alkaa.toEpochDay()}"
}
