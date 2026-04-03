package fi.hh.laakekamu.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun end_futureStartWithAStrayTakenRowForToday_isStillDeleted() {
        // A tick on a stale notification can leave a row for a day that isn't due.
        assertEquals(
            Muutos.Poista(1),
            endReminder(jakso(1, 480, tomorrow), today, takenToday = true)
        )
        assertEquals(
            listOf(Muutos.VaihdaAika(1, 540)),
            changeTime(jakso(1, 480, tomorrow), 540, today, takenToday = true, aloituspv = tomorrow)
        )
    }

    @Test
    fun planEdit_editedTimeAddedBackAsANewRow_keepsTheReminder_andAddsTheEditedTime() {
        // 8.00 taken today, then edited to 9.00 and a new 8.00 row added.
        val current = listOf(jakso(1, 480, today.minusDays(10)))
        val edited = listOf(EditedTime(id = 1, aika = 540), EditedTime(id = null, aika = 480))
        assertEquals(
            listOf(Muutos.Lisaa(540, today)),
            planEdit(current, edited, today, today.minusDays(10), takenToday = setOf(1L))
        )
    }

    @Test
    fun planEdit_unknownId_isTreatedAsANewTime() {
        // A stale id restored from saved state must not drop the time.
        val current = listOf(jakso(1, 480, today.minusDays(10)))
        assertEquals(
            listOf(Muutos.Paata(1, yesterday), Muutos.Lisaa(600, today)),
            planEdit(current, listOf(EditedTime(99, 600)), today, today.minusDays(10), emptySet())
        )
    }

    // Medication end date (lopetuspv) edited

    @Test
    fun endDateEdit_mayNotMoveIntoThePast_butAnUnchangedOldEndIsFine() {
        val oldEnd = today.minusDays(5)
        assertTrue(isValidEndDateEdit(oldEnd, oldEnd, today))
        assertTrue(isValidEndDateEdit(oldEnd, null, today))
        assertTrue(isValidEndDateEdit(null, today, today))
        assertTrue(isValidEndDateEdit(null, today.plusDays(9), today))
        assertFalse(isValidEndDateEdit(null, yesterday, today))
        assertFalse(isValidEndDateEdit(today.plusDays(9), today.minusDays(3), today))
        assertFalse(isValidEndDateEdit(oldEnd, oldEnd.plusDays(2), today))
    }

    @Test
    fun reopen_afterAGap_endsAtTheOldEnd_andRestartsTheSameTimesToday() {
        val oldEnd = today.minusDays(5)
        val current = listOf(
            jakso(1, 480, today.minusDays(30)),
            jakso(2, 720, today.minusDays(2)),
            jakso(3, 1200, today)
        )
        assertEquals(
            listOf(
                Muutos.Paata(1, oldEnd),
                Muutos.Lisaa(480, today),
                Muutos.Poista(2),
                Muutos.Lisaa(720, today)
            ),
            reopenAfterEnd(current, oldEnd, newEnd = null, today, aloituspv = today.minusDays(30))
        )
    }

    @Test
    fun reopen_notNeededWithoutAGapOrWithoutExtending() {
        val current = listOf(jakso(1, 480, today.minusDays(30)))
        val start = today.minusDays(30)
        assertEquals(emptyList<Muutos>(), reopenAfterEnd(current, yesterday, null, today, start))
        assertEquals(emptyList<Muutos>(), reopenAfterEnd(current, null, null, today, start))
        val oldEnd = today.minusDays(5)
        assertEquals(emptyList<Muutos>(), reopenAfterEnd(current, oldEnd, oldEnd, today, start))
    }

    @Test
    fun reopen_withAFutureStart_restartsOnTheStart() {
        val oldEnd = today.minusDays(5)
        assertEquals(
            listOf(Muutos.Paata(1, oldEnd), Muutos.Lisaa(480, today.plusDays(3))),
            reopenAfterEnd(
                listOf(jakso(1, 480, today.minusDays(30))),
                oldEnd,
                null,
                today,
                aloituspv = today.plusDays(3)
            )
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

    @Test
    fun moveStart_futureStartWithAStrayTakenRow_movesIt() {
        // Only a reminder that started today can end today.
        assertEquals(
            listOf(Muutos.SiirraAlku(1, today.plusDays(3))),
            moveStart(listOf(jakso(1, 480, tomorrow)), today.plusDays(3), today, setOf(1L))
        )
    }

    @Test
    fun planEdit_editedTimeOfARemovedTakenReminder_keepsThatReminder() {
        // 8.00 not taken, 9.00 taken; 8.00 edited to 9.00 and 9.00 removed (review a).
        val current = listOf(jakso(1, 480, today.minusDays(10)), jakso(2, 540, today.minusDays(10)))
        assertEquals(
            listOf(Muutos.Paata(1, yesterday)),
            planEdit(current, listOf(EditedTime(1, 540)), today, today.minusDays(10), setOf(2L))
        )
    }

    @Test
    fun planEdit_twoEditedRowsMovingOntoEachOthersTimes_keepTheTakenDose() {
        // 8.00 taken, 9.00; 8.00 edited to 10.00 and 9.00 to 8.00 (review b).
        val current = listOf(jakso(1, 480, today.minusDays(10)), jakso(2, 540, today.minusDays(10)))
        assertEquals(
            listOf(Muutos.Paata(2, yesterday), Muutos.Lisaa(600, today)),
            planEdit(
                current,
                listOf(EditedTime(1, 600), EditedTime(2, 480)),
                today,
                today.minusDays(10),
                takenToday = setOf(1L)
            )
        )
    }

    @Test
    fun planEdit_shiftingEveryTimeAfterTakingOne_keepsTodaysDoseCount() {
        // 8.00 taken, 9.00; both shifted an hour: 8.00 to 9.00 and 9.00 to 10.00.
        // Today: 8.00 taken + 9.00. From tomorrow: 9.00 + 10.00.
        val current = listOf(jakso(1, 480, today.minusDays(10)), jakso(2, 540, today.minusDays(10)))
        assertEquals(
            listOf(Muutos.Paata(1, today), Muutos.Lisaa(600, tomorrow)),
            planEdit(
                current,
                listOf(EditedTime(1, 540), EditedTime(2, 600)),
                today,
                today.minusDays(10),
                takenToday = setOf(1L)
            )
        )
    }

    @Test
    fun planEdit_removingATakenTimeAndAddingAnother_startsTheNewTimeToday() {
        // A removed row isn't a move: the new time is a dose of its own today.
        val current = listOf(jakso(1, 480, today.minusDays(10)))
        assertEquals(
            listOf(Muutos.Paata(1, today), Muutos.Lisaa(600, today)),
            planEdit(current, listOf(EditedTime(null, 600)), today, today.minusDays(10), setOf(1L))
        )
    }

    @Test
    fun planEdit_swappedTimes_changeNothing() {
        val current = listOf(jakso(1, 480, today.minusDays(10)), jakso(2, 540, today.minusDays(10)))
        assertEquals(
            emptyList<Muutos>(),
            planEdit(
                current,
                listOf(EditedTime(1, 540), EditedTime(2, 480)),
                today,
                today.minusDays(10),
                emptySet()
            )
        )
    }

    @Test
    fun planEdit_timeOfAReminderThatEndsToday_startsTomorrow() {
        // Earlier today 8.00 was taken and then changed to 10.00 (8.00 ends today); now 8.00 is
        // added back. Today already has its 8.00 dose.
        val current = listOf(jakso(2, 600, tomorrow))
        assertEquals(
            listOf(Muutos.Lisaa(480, tomorrow)),
            planEdit(
                current,
                listOf(EditedTime(2, 600), EditedTime(null, 480)),
                today,
                today.minusDays(10),
                takenToday = emptySet(),
                endsToday = setOf(480)
            )
        )
    }

    // An end planned after the medication had already ended

    @Test
    fun cap_endAfterAnOldMedicationEnd_movesBackToIt_orDeletesAReminderThatStartedAfterIt() {
        val oldEnd = today.minusDays(6)
        val current = listOf(jakso(1, 480, today.minusDays(30)), jakso(2, 720, today.minusDays(3)))
        val planned = listOf(
            Muutos.Paata(1, yesterday),
            Muutos.Lisaa(540, today),
            Muutos.Paata(2, today)
        )
        assertEquals(
            listOf(Muutos.Paata(1, oldEnd), Muutos.Lisaa(540, today), Muutos.Poista(2)),
            capToMedicationEnd(planned, current, oldEnd, today)
        )
    }

    @Test
    fun cap_changesNothingWhileTheMedicationRuns() {
        val current = listOf(jakso(1, 480, today.minusDays(30)))
        val planned = listOf(Muutos.Paata(1, yesterday))
        assertEquals(planned, capToMedicationEnd(planned, current, null, today))
        assertEquals(planned, capToMedicationEnd(planned, current, today, today))
        assertEquals(planned, capToMedicationEnd(planned, current, yesterday, today))
    }
}
