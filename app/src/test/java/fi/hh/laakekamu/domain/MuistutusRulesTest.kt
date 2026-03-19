package fi.hh.laakekamu.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MuistutusRulesTest {
    private val today = LocalDate.of(2025, 12, 18)
    private val yesterday = today.minusDays(1)
    private val tomorrow = today.plusDays(1)

    private fun jakso(id: Long, aika: Int, alkaen: LocalDate, asti: LocalDate? = null) =
        Muistutusjakso(id, aika, alkaen, asti)

    // New reminder

    @Test
    fun newReminder_startsToday_orOnALaterMedicationStart() {
        assertEquals(today, newReminderStart(today, aloituspv = today.minusDays(30)))
        assertEquals(today.plusDays(5), newReminderStart(today, aloituspv = today.plusDays(5)))
    }

    // Ending a reminder (time removed, medication deleted, switched to Tarvittaessa)

    @Test
    fun end_startedTodayAndNotTaken_isDeleted() {
        assertEquals(Muutos.Poista(1), endReminder(jakso(1, 480, today), today, takenToday = false))
    }

    @Test
    fun end_futureStartNotTaken_isDeleted() {
        assertEquals(Muutos.Poista(1), endReminder(jakso(1, 480, tomorrow), today, false))
    }

    @Test
    fun end_takenToday_endsToday_soTodaysDoseStays() {
        assertEquals(Muutos.Paata(1, today), endReminder(jakso(1, 480, yesterday), today, true))
        assertEquals(Muutos.Paata(1, today), endReminder(jakso(1, 480, today), today, true))
    }

    @Test
    fun end_withOldUntakenHistory_endsYesterday_soPastDaysStayDue() {
        val old = jakso(1, 480, today.minusDays(10))
        assertEquals(Muutos.Paata(1, yesterday), endReminder(old, today, takenToday = false))
    }

    // Time changed

    @Test
    fun timeChange_beforeAnyHistory_updatesInPlace() {
        assertEquals(
            listOf(Muutos.VaihdaAika(1, 540)),
            changeTime(jakso(1, 480, today), 540, today, takenToday = false, aloituspv = today)
        )
    }

    @Test
    fun timeChange_withHistory_notTakenToday_endsYesterday_andNewTimeStartsToday() {
        val old = jakso(1, 480, today.minusDays(10))
        assertEquals(
            listOf(Muutos.Paata(1, yesterday), Muutos.Lisaa(540, today)),
            changeTime(old, 540, today, takenToday = false, aloituspv = today.minusDays(10))
        )
    }

    @Test
    fun timeChange_afterTakingToday_keepsTodaysDose_andNewTimeStartsTomorrow() {
        val old = jakso(1, 480, today.minusDays(10))
        assertEquals(
            listOf(Muutos.Paata(1, today), Muutos.Lisaa(540, tomorrow)),
            changeTime(old, 540, today, takenToday = true, aloituspv = today.minusDays(10))
        )
    }

    @Test
    fun timeChange_toTheSameTime_changesNothing() {
        assertEquals(
            emptyList<Muutos>(),
            changeTime(jakso(1, 480, yesterday), 480, today, false, yesterday)
        )
    }

    // Medication start date (aloituspv) edited

    @Test
    fun startEdit_movesOnlyCurrentRemindersThatHaveNotStarted() {
        val started = jakso(1, 480, today.minusDays(10))
        val notStarted = jakso(2, 720, tomorrow)
        val ended = jakso(3, 1200, today.minusDays(10), asti = yesterday)
        assertEquals(
            listOf(Muutos.SiirraAlku(2, today.plusDays(7))),
            moveStart(listOf(started, notStarted, ended), today.plusDays(7), today, emptySet())
        )
    }

    @Test
    fun startEdit_toThePast_startsNotYetStartedRemindersToday() {
        val notStarted = jakso(2, 720, today.plusDays(3))
        assertEquals(
            listOf(Muutos.SiirraAlku(2, today)),
            moveStart(listOf(notStarted), today.minusDays(3), today, emptySet())
        )
    }

    @Test
    fun startEdit_intoTheFuture_afterTakingToday_endsTodayAndStartsAnew() {
        val startedToday = jakso(2, 720, today)
        assertEquals(
            listOf(Muutos.Paata(2, today), Muutos.Lisaa(720, today.plusDays(7))),
            moveStart(listOf(startedToday), today.plusDays(7), today, takenToday = setOf(2L))
        )
    }

    // Saving the edit wizard: the whole time list at once

    @Test
    fun planEdit_keepsUnchanged_endsRemoved_changesEdited_addsNew() {
        val current = listOf(
            jakso(1, 480, today.minusDays(10)),
            jakso(2, 720, today.minusDays(10)),
            jakso(3, 1200, today.minusDays(10))
        )
        val edited = listOf(
            EditedTime(id = 1, aika = 480),
            EditedTime(id = 2, aika = 750),
            EditedTime(id = null, aika = 1320)
        )
        assertEquals(
            listOf(
                Muutos.Paata(2, yesterday),
                Muutos.Lisaa(750, today),
                Muutos.Paata(3, yesterday),
                Muutos.Lisaa(1320, today)
            ),
            planEdit(current, edited, today, today.minusDays(10), takenToday = emptySet())
        )
    }

    @Test
    fun planEdit_removingATimeAndAddingTheSameTimeAgain_keepsTheReminder() {
        val current = listOf(
            jakso(1, 480, today.minusDays(10)),
            jakso(2, 1200, today.minusDays(10))
        )
        val edited = listOf(EditedTime(id = 1, aika = 480), EditedTime(id = null, aika = 1200))
        assertEquals(
            emptyList<Muutos>(),
            planEdit(current, edited, today, today.minusDays(10), takenToday = emptySet())
        )
    }

    @Test
    fun endAll_deleteOrTarvittaessa_endsEveryCurrentReminder() {
        val current = listOf(jakso(1, 480, today.minusDays(10)), jakso(2, 720, today))
        assertEquals(
            listOf(Muutos.Paata(1, today), Muutos.Poista(2)),
            endAll(current, today, takenToday = setOf(1L))
        )
    }
}
