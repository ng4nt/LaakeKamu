package fi.hh.laakekamu.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** The state of one dose (a reminder on one day). */
enum class AnnoksenTila {
    /** A Laakkeenotto row exists. */
    OTETTU,

    /** Not taken and its time has passed (shown red with "!" and "ottamatta"). */
    OTTAMATTA,

    /** Not taken and its time hasn't come yet. */
    TULOSSA
}

/**
 * "due on day d": a reminder's dose is due on [day] when the day is inside the reminder's
 * validity period and inside the medication's own period (all ends inclusive, null = open).
 * Past days ignore `aktiivinen`: deleting a medication ends its reminders instead, so its
 * history stays.
 */
fun isDueOn(
    day: LocalDate,
    voimassaAlkaen: LocalDate,
    voimassaAsti: LocalDate?,
    aloituspv: LocalDate,
    lopetuspv: LocalDate?
): Boolean = day.inRange(voimassaAlkaen, voimassaAsti) && day.inRange(aloituspv, lopetuspv)

/**
 * The state of a due dose on [day] at minute-of-day [aika]. A taken dose is [AnnoksenTila.OTETTU];
 * otherwise it is missed from its local wall-clock time [now] on, and upcoming before that.
 */
fun tilaOf(day: LocalDate, aika: Int, taken: Boolean, now: LocalDateTime): AnnoksenTila = when {
    taken -> AnnoksenTila.OTETTU
    !now.isBefore(LocalDateTime.of(day, LocalTime.of(aika / 60, aika % 60))) ->
        AnnoksenTila.OTTAMATTA
    else -> AnnoksenTila.TULOSSA
}

/**
 * Today and past days can be ticked and unticked; future days are read-only. A tick on a
 * past day is stored for that day (paivamaara = [day]), with the current time as ottoaika.
 */
fun canToggle(day: LocalDate, today: LocalDate): Boolean = !day.isAfter(today)

/**
 * A day is complete when at least one dose was due and every due dose is taken.
 * Days without doses get no marker.
 */
fun isDayComplete(states: Collection<AnnoksenTila>): Boolean =
    states.isNotEmpty() && states.all { it == AnnoksenTila.OTETTU }

private fun LocalDate.inRange(start: LocalDate, end: LocalDate?): Boolean =
    !isBefore(start) && (end == null || !isAfter(end))
