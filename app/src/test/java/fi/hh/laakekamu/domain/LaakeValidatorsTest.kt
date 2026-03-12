package fi.hh.laakekamu.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LaakeValidatorsTest {
    private val today = LocalDate.of(2025, 12, 18)

    // Active medications by id (the caller passes only aktiivinen = 1 ones).
    private val active = mapOf(1L to "A aamulääke", 2L to "Äkäslääke")

    // Unique name

    @Test
    fun name_newUniqueName_isValid() {
        assertNull(validateName("B aamulääke", active, editedId = null))
    }

    @Test
    fun name_sameNameDifferentCase_isDuplicate_includingScandinavianLetters() {
        assertEquals(NameError.Duplicate("äkäslääke"), validateName("äkäslääke", active, null))
        assertEquals(NameError.Duplicate("ÄKÄSLÄÄKE"), validateName("ÄKÄSLÄÄKE", active, null))
        assertEquals(NameError.Duplicate("a aamulääke"), validateName("a aamulääke", active, null))
    }

    @Test
    fun name_surroundingSpaces_areIgnored_andTheTrimmedNameIsReported() {
        assertEquals(
            NameError.Duplicate("A aamulääke"),
            validateName("  A aamulääke ", active, null)
        )
    }

    @Test
    fun name_editingTheSameMedication_isNotADuplicateOfItself() {
        assertNull(validateName("A aamulääke", active, editedId = 1L))
        assertEquals(NameError.Duplicate("Äkäslääke"), validateName("Äkäslääke", active, 1L))
    }

    @Test
    fun name_blank_isRejected() {
        assertEquals(NameError.Blank, validateName("", active, null))
        assertEquals(NameError.Blank, validateName("   ", active, null))
    }

    @Test
    fun name_maxLength_is150CharactersAfterTrim() {
        assertNull(validateName("x".repeat(150), active, null))
        assertEquals(NameError.TooLong, validateName("x".repeat(151), active, null))
        assertNull(validateName(" " + "x".repeat(150) + " ", active, null))
    }

    // Duration labels

    @Test
    fun kesto_futureStart_showsStartDate_evenWithEndDate() {
        val start = today.plusDays(3)
        assertEquals(Kesto.Alkaa(start), kestoOf(start, start.plusDays(10), today))
        assertEquals(Kesto.Alkaa(start), kestoOf(start, null, today))
    }

    @Test
    fun kesto_withEndDate_countsRemainingDaysIncludingToday() {
        assertEquals(
            Kesto.Jaljella(20),
            kestoOf(today.minusDays(5), today.plusDays(19), today)
        )
        assertEquals(Kesto.Jaljella(1), kestoOf(today.minusDays(5), today, today))
    }

    @Test
    fun kesto_endDateInThePast_isEnded() {
        assertEquals(Kesto.Paattynyt, kestoOf(today.minusDays(5), today.minusDays(1), today))
    }

    @Test
    fun kesto_withoutEndDate_countsDaysInUseIncludingStartDay() {
        assertEquals(Kesto.Kaytossa(1), kestoOf(today, null, today))
        assertEquals(Kesto.Kaytossa(10), kestoOf(today.minusDays(9), null, today))
    }

    // CHECK rules of the SQL design

    @Test
    fun reminderTime_mustBeAMinuteOfTheDay() {
        assertFalse(isValidReminderTime(-1))
        assertTrue(isValidReminderTime(0))
        assertTrue(isValidReminderTime(1439))
        assertFalse(isValidReminderTime(1440))
    }

    @Test
    fun dateRange_endMayEqualStart_orBeOpen_butNotBeforeStart() {
        assertTrue(isValidDateRange(today, today))
        assertTrue(isValidDateRange(today, null))
        assertTrue(isValidDateRange(today, today.plusDays(1)))
        assertFalse(isValidDateRange(today, today.minusDays(1)))
    }
}
