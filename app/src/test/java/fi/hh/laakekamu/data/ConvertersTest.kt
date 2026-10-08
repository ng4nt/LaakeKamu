package fi.hh.laakekamu.data

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConvertersTest {
    private val converters = Converters()

    @Test
    fun localDate_isStoredAsEpochDay() {
        assertEquals(0L, converters.localDateToEpochDay(LocalDate.of(1970, 1, 1)))
        assertEquals(20440L, converters.localDateToEpochDay(LocalDate.of(2025, 12, 18)))
    }

    @Test
    fun localDate_roundTrips() {
        val date = LocalDate.of(2026, 10, 9)
        assertEquals(date, converters.epochDayToLocalDate(converters.localDateToEpochDay(date)))
    }

    @Test
    fun instant_isStoredAsEpochMillis_andRoundTrips() {
        val instant = Instant.ofEpochMilli(1_765_000_000_123L)
        assertEquals(1_765_000_000_123L, converters.instantToEpochMilli(instant))
        assertEquals(instant, converters.epochMilliToInstant(1_765_000_000_123L))
    }

    @Test
    fun nulls_stayNull() {
        assertNull(converters.localDateToEpochDay(null))
        assertNull(converters.epochDayToLocalDate(null))
        assertNull(converters.instantToEpochMilli(null))
        assertNull(converters.epochMilliToInstant(null))
    }
}
