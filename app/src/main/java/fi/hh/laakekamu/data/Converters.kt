package fi.hh.laakekamu.data

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate

/**
 * Room type converters. SQLite has no date type, so dates are stored as INTEGER epoch days and
 * instants as INTEGER epoch milliseconds.
 */
class Converters {
    @TypeConverter
    fun localDateToEpochDay(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(epochDay: Long?): LocalDate? = epochDay?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun instantToEpochMilli(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun epochMilliToInstant(epochMilli: Long?): Instant? = epochMilli?.let(Instant::ofEpochMilli)
}
