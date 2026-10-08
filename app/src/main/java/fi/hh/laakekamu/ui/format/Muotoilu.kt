package fi.hh.laakekamu.ui.format

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Finnish locale for every date and time shown in the UI.
 *
 * The UI is Finnish only, so formatting never follows the device language.
 */
val SUOMI: Locale = Locale.forLanguageTag("fi-FI")

// Stand-alone patterns (c, L): the format patterns (E, M) give "torstaina" and "joulukuuta".
private val DATE = DateTimeFormatter.ofPattern("d.M.yyyy", SUOMI)
private val DAY_MONTH = DateTimeFormatter.ofPattern("d.M.", SUOMI)
private val WEEKDAY = DateTimeFormatter.ofPattern("cccc", SUOMI)
private val WEEKDAY_SHORT = DateTimeFormatter.ofPattern("ccc", SUOMI)
private val MONTH = DateTimeFormatter.ofPattern("LLLL", SUOMI)
private val TIME = DateTimeFormatter.ofPattern("H.mm", SUOMI)

private const val MINUTES_PER_DAY = 24 * 60

/** "18.12.2025" */
fun formatDate(date: LocalDate): String = DATE.format(date)

/** "18.12." */
fun formatDayMonth(date: LocalDate): String = DAY_MONTH.format(date)

/** "torstai" */
fun formatWeekday(date: LocalDate): String = WEEKDAY.format(date)

/** "Torstai", for the start of a heading such as "Torstai 18.12.". */
fun formatWeekdayTitle(date: LocalDate): String = formatWeekday(date).capitalised()

/** "to" */
fun formatWeekdayShort(date: LocalDate): String = WEEKDAY_SHORT.format(date)

/** "joulukuu" */
fun formatMonth(date: LocalDate): String = MONTH.format(date)

/** "Joulukuu", for the diary calendar heading. */
fun formatMonthTitle(date: LocalDate): String = formatMonth(date).capitalised()

/** "8.00" */
fun formatTime(time: LocalTime): String = TIME.format(time)

/**
 * "8.00" for [minuteOfDay] 480. Reminder times are stored as minutes after midnight (0–1439).
 */
fun formatTime(minuteOfDay: Int): String {
    require(minuteOfDay in 0 until MINUTES_PER_DAY) { "minuteOfDay out of range: $minuteOfDay" }
    return formatTime(LocalTime.of(minuteOfDay / 60, minuteOfDay % 60))
}

private fun String.capitalised(): String = replaceFirstChar { it.titlecase(SUOMI) }
