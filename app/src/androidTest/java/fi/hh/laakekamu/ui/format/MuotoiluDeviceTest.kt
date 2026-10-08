package fi.hh.laakekamu.ui.format

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The JVM unit tests check the formatters against the JDK's CLDR data; Android formats with its
 * own ICU data, so the stand-alone Finnish forms are checked on the device too.
 */
@RunWith(AndroidJUnit4::class)
class MuotoiluDeviceTest {
    private val thursday = LocalDate.of(2025, 12, 18)

    @Test
    fun finnishNames_onTheDevice() {
        assertEquals("torstai", formatWeekday(thursday))
        assertEquals("Torstai", formatWeekdayTitle(thursday))
        assertEquals("to", formatWeekdayShort(thursday))
        assertEquals("joulukuu", formatMonth(thursday))
        assertEquals("Joulukuu", formatMonthTitle(thursday))
    }

    @Test
    fun numericFormats_onTheDevice() {
        assertEquals("18.12.2025", formatDate(thursday))
        assertEquals("18.12.", formatDayMonth(thursday))
        assertEquals("8.00", formatTime(LocalTime.of(8, 0)))
    }
}
