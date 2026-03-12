package fi.hh.laakekamu.domain

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnosRulesTest {
    private val today = LocalDate.of(2025, 12, 18)

    // "due on day d": inside both the reminder validity and the medication period

    @Test
    fun due_insideBothPeriods_boundariesIncluded() {
        val start = today.minusDays(5)
        val end = today.plusDays(5)
        assertTrue(isDueOn(start, voimassaAlkaen = start, voimassaAsti = end, start, end))
        assertTrue(isDueOn(end, voimassaAlkaen = start, voimassaAsti = end, start, end))
        assertTrue(isDueOn(today, voimassaAlkaen = start, voimassaAsti = null, start, null))
    }

    @Test
    fun due_notBeforeValidityStartOrAfterValidityEnd() {
        val medStart = today.minusDays(30)
        assertFalse(isDueOn(today.minusDays(1), today, null, medStart, null))
        assertFalse(isDueOn(today.plusDays(1), today.minusDays(10), today, medStart, null))
    }

    @Test
    fun due_notOutsideTheMedicationPeriod() {
        val validFrom = today.minusDays(30)
        assertFalse(
            isDueOn(today, validFrom, null, aloituspv = today.plusDays(1), lopetuspv = null)
        )
        assertFalse(isDueOn(today, validFrom, null, aloituspv = validFrom, today.minusDays(1)))
    }

    // Dose state

    @Test
    fun state_takenRow_isOtettu_whateverTheTime() {
        assertEquals(AnnoksenTila.OTETTU, tilaOf(today, 480, taken = true, at(today, 7, 0)))
        assertEquals(AnnoksenTila.OTETTU, tilaOf(today, 480, taken = true, at(today, 9, 0)))
    }

    @Test
    fun state_notTaken_beforeTheTime_isTulossa_fromTheTimeOn_isOttamatta() {
        assertEquals(AnnoksenTila.TULOSSA, tilaOf(today, 480, taken = false, at(today, 7, 59)))
        assertEquals(AnnoksenTila.OTTAMATTA, tilaOf(today, 480, taken = false, at(today, 8, 0)))
        assertEquals(AnnoksenTila.OTTAMATTA, tilaOf(today, 480, taken = false, at(today, 23, 0)))
    }

    @Test
    fun state_pastDayNotTaken_isOttamatta_futureDay_isTulossa() {
        val now = at(today, 7, 0)
        assertEquals(AnnoksenTila.OTTAMATTA, tilaOf(today.minusDays(1), 1200, false, now))
        assertEquals(AnnoksenTila.TULOSSA, tilaOf(today.plusDays(1), 0, false, now))
    }

    @Test
    fun state_midnightDose_isMissedFromMidnight() {
        assertEquals(AnnoksenTila.OTTAMATTA, tilaOf(today, 0, taken = false, at(today, 0, 0)))
    }

    // Editability

    @Test
    fun toggle_todayAndPastDays_futureDaysAreReadOnly() {
        assertTrue(canToggle(today, today))
        assertTrue(canToggle(today.minusDays(6), today))
        assertFalse(canToggle(today.plusDays(1), today))
    }

    // Day complete

    @Test
    fun dayComplete_needsAtLeastOneDose_andAllTaken() {
        assertFalse(isDayComplete(emptyList()))
        assertTrue(isDayComplete(listOf(AnnoksenTila.OTETTU, AnnoksenTila.OTETTU)))
        assertFalse(isDayComplete(listOf(AnnoksenTila.OTETTU, AnnoksenTila.OTTAMATTA)))
        assertFalse(isDayComplete(listOf(AnnoksenTila.OTETTU, AnnoksenTila.TULOSSA)))
    }

    private fun at(day: LocalDate, hour: Int, minute: Int) =
        LocalDateTime.of(day.year, day.month, day.dayOfMonth, hour, minute)
}
