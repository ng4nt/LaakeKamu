package fi.hh.laakekamu.data

import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** The app's clock must follow a time zone change while the app is running. */
class SystemZoneClockTest {
    private lateinit var original: TimeZone

    @Before
    fun saveZone() {
        original = TimeZone.getDefault()
    }

    @After
    fun restoreZone() {
        TimeZone.setDefault(original)
    }

    @Test
    fun zone_followsTheCurrentDefaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Helsinki"))
        assertEquals("Europe/Helsinki", SystemZoneClock.zone.id)

        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        assertEquals("America/New_York", SystemZoneClock.zone.id)
    }

    @Test
    fun instant_isTheSystemTime() {
        val before = System.currentTimeMillis()
        val now = SystemZoneClock.millis()
        val after = System.currentTimeMillis()
        assert(now in before..after)
    }
}
