package fi.hh.laakekamu.ui.format

import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The formatters must give Finnish output even when the device language is something else,
 * so every test runs with an English default locale.
 */
class MuotoiluTest {
    private lateinit var originalLocale: Locale

    // Thursday, as in the Figma sample "Tänään on torstai 18.12."
    private val thursday = LocalDate.of(2025, 12, 18)

    @Before
    fun setEnglishDefaultLocale() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreDefaultLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun formatDate_usesDayMonthYearWithDots() {
        assertEquals("18.12.2025", formatDate(thursday))
        assertEquals("1.3.2026", formatDate(LocalDate.of(2026, 3, 1)))
    }

    @Test
    fun formatDayMonth_hasTrailingDotAndNoYear() {
        assertEquals("18.12.", formatDayMonth(thursday))
    }

    @Test
    fun formatWeekday_isNominativeNotEssive() {
        // EEEE would give "torstaina"; the stand-alone form is needed.
        assertEquals("torstai", formatWeekday(thursday))
        assertEquals("maanantai", formatWeekday(LocalDate.of(2025, 12, 15)))
    }

    @Test
    fun formatWeekdayTitle_isCapitalised() {
        assertEquals("Torstai", formatWeekdayTitle(thursday))
    }

    @Test
    fun formatWeekdayShort_isTwoLetters() {
        assertEquals("to", formatWeekdayShort(thursday))
        assertEquals("su", formatWeekdayShort(LocalDate.of(2025, 12, 21)))
    }

    @Test
    fun formatMonth_isNominativeNotPartitive() {
        // MMMM would give "joulukuuta"; the stand-alone form is needed.
        assertEquals("joulukuu", formatMonth(thursday))
    }

    @Test
    fun formatMonthTitle_isCapitalised() {
        assertEquals("Joulukuu", formatMonthTitle(thursday))
    }

    @Test
    fun formatTime_usesDotAndNoLeadingZeroForHours() {
        assertEquals("8.00", formatTime(LocalTime.of(8, 0)))
        assertEquals("20.05", formatTime(LocalTime.of(20, 5)))
    }

    @Test
    fun formatTime_fromMinuteOfDay_coversWholeDay() {
        assertEquals("0.00", formatTime(minuteOfDay = 0))
        assertEquals("8.00", formatTime(minuteOfDay = 480))
        assertEquals("23.59", formatTime(minuteOfDay = 1439))
    }

    @Test(expected = IllegalArgumentException::class)
    fun formatTime_rejectsMinuteOutsideDay() {
        formatTime(minuteOfDay = 1440)
    }
}
