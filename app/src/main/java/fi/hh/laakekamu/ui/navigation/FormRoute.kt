package fi.hh.laakekamu.ui.navigation

import fi.hh.laakekamu.ui.lisaalaake.LisaaLaakeViewModel
import fi.hh.laakekamu.ui.lisaaoire.LisaaOireViewModel
import java.time.LocalDate

/** Routes of the full-screen forms (no bottom bar). */
object FormRoute {
    const val LISAA_LAAKE = "lisaa_laake"

    /** Muokkaa lääkettä: choose a medication to edit or delete. */
    const val MUOKKAA_LAAKETTA = "muokkaa_laaketta"

    /** The wizard editing a medication; its argument is the medication's id. */
    const val MUOKKAA_LAAKE = "muokkaa_laake/{${LisaaLaakeViewModel.ARG_LAAKE_ID}}"

    /** [MUOKKAA_LAAKE] for the medication [laakeId]. */
    fun muokkaaLaake(laakeId: Long): String = "muokkaa_laake/$laakeId"

    /** The symptom form; its argument is the start date's default as an epoch day. */
    const val LISAA_OIRE = "lisaa_oire/{${LisaaOireViewModel.ARG_ALKAA}}"

    /** [LISAA_OIRE] starting on [alkaa]. */
    fun lisaaOire(alkaa: LocalDate): String = "lisaa_oire/${alkaa.toEpochDay()}"
}
