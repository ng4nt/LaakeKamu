package fi.hh.laakekamu.ui.etusivu

import fi.hh.laakekamu.domain.AnnoksenTila.OTETTU
import fi.hh.laakekamu.domain.AnnoksenTila.OTTAMATTA
import fi.hh.laakekamu.domain.AnnoksenTila.TULOSSA
import org.junit.Assert.assertEquals
import org.junit.Test

/** The progress percentage and how the line between two dots is drawn. */
class EdistysTest {
    @Test
    fun thePercentage_isTheTakenShare_asInFigma() {
        // Figma.
        assertEquals(0, edistysProsentti(List(5) { TULOSSA }))
        assertEquals(60, edistysProsentti(listOf(OTETTU, OTETTU, OTETTU, TULOSSA, TULOSSA)))
        assertEquals(40, edistysProsentti(listOf(OTETTU, OTTAMATTA, OTETTU, TULOSSA, TULOSSA)))
        assertEquals(100, edistysProsentti(listOf(OTETTU)))
    }

    @Test
    fun thePercentage_roundsDown_soOnly_everyDoseTaken_is100() {
        assertEquals(66, edistysProsentti(listOf(OTETTU, OTETTU, TULOSSA)))
        assertEquals(99, edistysProsentti(List(199) { OTETTU } + TULOSSA))
    }

    @Test
    fun noDoses_isZero() {
        assertEquals(0, edistysProsentti(emptyList()))
    }

    @Test
    fun theLine_isPassedBetweenTwoPassedDoses_andRedNextToAMissedOne() {
        // Figma: taken, missed, taken → red; then light to the upcoming doses.
        assertEquals(OTTAMATTA, viivanTila(OTETTU, OTTAMATTA))
        assertEquals(OTTAMATTA, viivanTila(OTTAMATTA, OTETTU))
        assertEquals(OTTAMATTA, viivanTila(OTTAMATTA, OTTAMATTA))
        assertEquals(TULOSSA, viivanTila(OTETTU, TULOSSA))
        // Figma: all taken → primary.
        assertEquals(OTETTU, viivanTila(OTETTU, OTETTU))
    }

    @Test
    fun theLine_toOrFromAnUpcomingDose_isUpcoming_evenWhenADoseWasTickedAhead() {
        assertEquals(TULOSSA, viivanTila(TULOSSA, OTETTU))
        assertEquals(TULOSSA, viivanTila(OTTAMATTA, TULOSSA))
        assertEquals(TULOSSA, viivanTila(TULOSSA, TULOSSA))
    }
}
