package fi.hh.laakekamu.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Maximum medication name length (VARCHAR(150) in the data dictionary). */
const val NAME_MAX_LENGTH = 150

/** Last minute of the day; reminder times are minutes after midnight. */
private const val LAST_MINUTE_OF_DAY = 24 * 60 - 1

/** Why a medication name was rejected. The UI turns these into Finnish texts. */
sealed interface NameError {
    data object Blank : NameError

    data object TooLong : NameError

    /** Another active medication already has this name: `Lääke "X" on jo lisätty.` */
    data class Duplicate(val name: String) : NameError
}

/**
 * Checks a medication name.
 *
 * The name is trimmed, and must not be blank or longer than [NAME_MAX_LENGTH]. It must not match
 * another **active** medication when both are lower-cased with Kotlin's locale-invariant
 * [lowercase] (handles ä/ö; SQLite's NOCASE is ASCII-only). [activeNames] maps the ids of the
 * active medications to their names; [editedId] is the medication being edited, which may keep
 * its own name.
 *
 * @return null when the name is valid.
 */
fun validateName(name: String, activeNames: Map<Long, String>, editedId: Long?): NameError? {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return NameError.Blank
    if (trimmed.length > NAME_MAX_LENGTH) return NameError.TooLong
    val key = trimmed.lowercase()
    val taken = activeNames.any { (id, existing) ->
        id != editedId && existing.trim().lowercase() == key
    }
    return if (taken) NameError.Duplicate(trimmed) else null
}

/** How long a medication lasts, as shown in the list and the wizard. */
sealed interface Kesto {
    /** The start date is in the future: "alkaa d.M.". */
    data class Alkaa(val date: LocalDate) : Kesto

    /** An end date is set: "vielä N pv". Counts today. */
    data class Jaljella(val days: Long) : Kesto

    /** The end date has passed: "päättynyt". */
    data object Paattynyt : Kesto

    /** No end date: "Käytössä N pv". Counts the start day. */
    data class Kaytossa(val days: Long) : Kesto
}

/** The [Kesto] of a medication running [start]..[end] (end inclusive) on [today]. */
fun kestoOf(start: LocalDate, end: LocalDate?, today: LocalDate): Kesto = when {
    start.isAfter(today) -> Kesto.Alkaa(start)
    end == null -> Kesto.Kaytossa(ChronoUnit.DAYS.between(start, today) + 1)
    end.isBefore(today) -> Kesto.Paattynyt
    else -> Kesto.Jaljella(ChronoUnit.DAYS.between(today, end) + 1)
}

/** CHECK (aika BETWEEN 0 AND 1439): a reminder time is a minute of the day. */
fun isValidReminderTime(minuteOfDay: Int): Boolean = minuteOfDay in 0..LAST_MINUTE_OF_DAY

/**
 * CHECK (lopetuspv IS NULL OR lopetuspv >= aloituspv), and the same for the reminder validity
 * period: an open end, or an end on or after the start.
 */
fun isValidDateRange(start: LocalDate, end: LocalDate?): Boolean =
    end == null || !end.isBefore(start)
