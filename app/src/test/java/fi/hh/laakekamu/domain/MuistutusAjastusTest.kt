package fi.hh.laakekamu.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Next trigger, slots and late doses, in Finland's time zone. */
class MuistutusAjastusTest {
    private val zone = ZoneId.of("Europe/Helsinki")
    private val today = LocalDate.of(2025, 12, 18)
    private val longAgo = today.minusDays(30)

    private fun at(day: LocalDate, hour: Int, minute: Int): ZonedDateTime =
        ZonedDateTime.of(day.atTime(hour, minute), zone)

    private fun aikataulu(
        id: Long,
        aika: Int,
        alkaen: LocalDate = longAgo,
        asti: LocalDate? = null,
        aloituspv: LocalDate = longAgo,
        lopetuspv: LocalDate? = null
    ) = MuistutusAikataulu(id, aika, alkaen, asti, aloituspv, lopetuspv)

    @Test
    fun next_laterToday_orTomorrowIfPassed_orNowIfExactlyNow() {
        assertEquals(at(today, 8, 0), nextTrigger(at(today, 7, 0), aikataulu(1, 480)))
        assertEquals(at(today.plusDays(1), 8, 0), nextTrigger(at(today, 8, 1), aikataulu(1, 480)))
        assertEquals(at(today, 8, 0), nextTrigger(at(today, 8, 0), aikataulu(1, 480)))
    }

    @Test
    fun next_futureValidityOrMedicationStart_waitsForIt() {
        val inThreeDays = today.plusDays(3)
        assertEquals(
            at(inThreeDays, 8, 0),
            nextTrigger(at(today, 7, 0), aikataulu(1, 480, alkaen = inThreeDays))
        )
        assertEquals(
            at(inThreeDays, 8, 0),
            nextTrigger(at(today, 7, 0), aikataulu(1, 480, aloituspv = inThreeDays))
        )
    }

    @Test
    fun next_afterValidityOrMedicationEnd_isNull() {
        val later = at(today, 9, 0)
        assertNull(nextTrigger(later, aikataulu(1, 480, asti = today.minusDays(1))))
        assertNull(nextTrigger(later, aikataulu(1, 480, asti = today)))
        assertNull(nextTrigger(later, aikataulu(1, 480, lopetuspv = today)))
        assertEquals(at(today, 20, 0), nextTrigger(later, aikataulu(1, 1200, asti = today)))
    }

    @Test
    fun next_springForwardGap_firesAfterTheGap() {
        // 29.3.2026 clocks go from 3.00 to 4.00, so 3.30 doesn't exist that day.
        val day = LocalDate.of(2026, 3, 29)
        val trigger = nextTrigger(at(day, 0, 0), aikataulu(1, 210))!!
        assertEquals(day.atTime(4, 30), trigger.toLocalDateTime())
        assertEquals(ZoneOffset.ofHours(3), trigger.offset)
    }

    @Test
    fun next_fallBackOverlap_firesAtTheFirstOccurrence() {
        // 25.10.2026 clocks go from 4.00 back to 3.00, so 3.30 happens twice.
        val day = LocalDate.of(2026, 10, 25)
        val trigger = nextTrigger(at(day, 0, 0), aikataulu(1, 210))!!
        assertEquals(day.atTime(3, 30), trigger.toLocalDateTime())
        assertEquals(ZoneOffset.ofHours(3), trigger.offset)
    }

    @Test
    fun next_dayAfterSpringForward_keepsTheLocalTime() {
        val trigger = nextTrigger(at(LocalDate.of(2026, 3, 29), 9, 0), aikataulu(1, 480))!!
        assertEquals(LocalDate.of(2026, 3, 30).atTime(8, 0), trigger.toLocalDateTime())
    }

    // Slots: one alarm per distinct time

    @Test
    fun slots_oneEntryPerDistinctTime_withTheEarliestTrigger_endedOnesLeftOut() {
        val reminders = listOf(
            aikataulu(1, 480, alkaen = today.plusDays(2)),
            aikataulu(2, 480),
            aikataulu(3, 1200),
            aikataulu(4, 720, asti = today.minusDays(1))
        )
        assertEquals(
            mapOf(480 to at(today.plusDays(1), 8, 0), 1200 to at(today, 20, 0)),
            nextSlotTriggers(reminders, at(today, 9, 0))
        )
    }

    // Late doses after boot: due, not taken, time in [now - 2 h, now)

    @Test
    fun late_windowStartIsIncluded_nowIsExcluded() {
        val reminders = listOf(
            aikataulu(1, 7 * 60),
            aikataulu(2, 8 * 60),
            aikataulu(3, 6 * 60 + 59),
            aikataulu(4, 9 * 60)
        )
        assertEquals(
            listOf(Annos(1, today, at(today, 7, 0)), Annos(2, today, at(today, 8, 0))),
            lateDoses(at(today, 9, 0), reminders, taken = emptySet())
        )
    }

    @Test
    fun late_takenDosesAreLeftOut() {
        val reminders = listOf(aikataulu(1, 480), aikataulu(2, 500))
        assertEquals(
            listOf(Annos(2, today, at(today, 8, 20))),
            lateDoses(at(today, 9, 0), reminders, taken = setOf(TakenDose(1, today)))
        )
    }

    @Test
    fun late_windowAcrossMidnight_includesYesterdaysDoseForYesterday() {
        val reminders = listOf(aikataulu(1, 23 * 60 + 30))
        assertEquals(
            listOf(Annos(1, today.minusDays(1), at(today.minusDays(1), 23, 30))),
            lateDoses(at(today, 1, 0), reminders, taken = emptySet())
        )
    }

    @Test
    fun late_dosesNotDueThatDayAreLeftOut() {
        val reminders = listOf(aikataulu(1, 480, alkaen = today.plusDays(1)))
        assertEquals(emptyList<Annos>(), lateDoses(at(today, 9, 0), reminders, emptySet()))
    }
}
