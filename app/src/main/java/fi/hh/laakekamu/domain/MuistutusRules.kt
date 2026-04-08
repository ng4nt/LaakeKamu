package fi.hh.laakekamu.domain

import java.time.LocalDate
import java.time.ZonedDateTime

/** A reminder time with its validity period, as rules see it (mirrors Muistutukset). */
data class Muistutusjakso(
    val id: Long,
    /** Minutes after midnight. */
    val aika: Int,
    val voimassaAlkaen: LocalDate,
    /** Null = current (valid until further notice). */
    val voimassaAsti: LocalDate?
)

/** One change to the Muistutukset table. The repository applies a list of them in a transaction. */
sealed interface Muutos {
    /** Insert a new reminder at [aika], valid from [voimassaAlkaen]. */
    data class Lisaa(val aika: Int, val voimassaAlkaen: LocalDate) : Muutos

    /** Delete reminder [id]. Only used when it has no history (nothing due before today). */
    data class Poista(val id: Long) : Muutos

    /** End reminder [id] on [voimassaAsti] (its last valid day). */
    data class Paata(val id: Long, val voimassaAsti: LocalDate) : Muutos

    /** Change reminder [id]'s time in place. Only used when it has no history. */
    data class VaihdaAika(val id: Long, val aika: Int) : Muutos

    /** Move reminder [id]'s first day to [voimassaAlkaen]. Only for reminders not yet started. */
    data class SiirraAlku(val id: Long, val voimassaAlkaen: LocalDate) : Muutos
}

/** One row of the edit wizard's time list: an existing reminder ([id]) or a new one (null). */
data class EditedTime(val id: Long?, val aika: Int)

/** A new reminder starts today, or on the medication's start date if that is later. */
fun newReminderStart(today: LocalDate, aloituspv: LocalDate): LocalDate = maxOf(today, aloituspv)

/**
 * Ends a reminder (time removed, medication deleted, switched to Tarvittaessa) without
 * changing past days:
 * - not started before today and not taken today → delete it (no history)
 * - taken today → it ends today, so today's taken dose stays
 * - otherwise → it ends yesterday, so past days keep their due doses
 */
fun endReminder(jakso: Muistutusjakso, today: LocalDate, takenToday: Boolean): Muutos = when {
    hasNoHistory(jakso, today, takenToday) -> Muutos.Poista(jakso.id)
    takenToday -> Muutos.Paata(jakso.id, today)
    else -> Muutos.Paata(jakso.id, today.minusDays(1))
}

/**
 * Changes a reminder's time. Without history it is updated in place; otherwise the old
 * reminder ends as in [endReminder] and the new time starts tomorrow (if today's dose was
 * already taken) or today.
 */
fun changeTime(
    jakso: Muistutusjakso,
    newAika: Int,
    today: LocalDate,
    takenToday: Boolean,
    aloituspv: LocalDate
): List<Muutos> {
    if (newAika == jakso.aika) return emptyList()
    if (hasNoHistory(jakso, today, takenToday)) return listOf(Muutos.VaihdaAika(jakso.id, newAika))
    val newStart = if (takenToday) today.plusDays(1) else today
    return listOf(
        endReminder(jakso, today, takenToday),
        Muutos.Lisaa(newAika, maxOf(newStart, aloituspv))
    )
}

/**
 * The medication's start date was edited. Current reminders that haven't started before
 * today move to max(today, [newAloituspv]). If a reminder started today, its dose was already
 * taken and the start moves into the future, the reminder ends today and a new one starts on the
 * new date, so the taken row stays inside its reminder's validity. (The dose is then no longer due today, because
 * the medication now starts later, so Home hides it; the diary still lists the taken row.)
 * Reminders that started earlier are left alone (documented limitation: existing history isn't
 * rewritten).
 */
fun moveStart(
    current: List<Muistutusjakso>,
    newAloituspv: LocalDate,
    today: LocalDate,
    takenToday: Set<Long>
): List<Muutos> {
    val newStart = maxOf(today, newAloituspv)
    return current
        .filter { it.voimassaAsti == null && !it.voimassaAlkaen.isBefore(today) }
        .filter { it.voimassaAlkaen != newStart }
        .flatMap { jakso ->
            // A reminder that starts later can't end today; a row for it is a stray.
            if (jakso.id in takenToday && jakso.voimassaAlkaen == today) {
                listOf(Muutos.Paata(jakso.id, today), Muutos.Lisaa(jakso.aika, newStart))
            } else {
                listOf(Muutos.SiirraAlku(jakso.id, newStart))
            }
        }
}

/**
 * Plans saving the edit wizard's time list against the [current] reminders
 * (voimassa_asti IS NULL). [takenToday] holds the ids of reminders whose dose is taken today.
 *
 * Times are matched first: a current reminder whose time is still in the list stays as it is,
 * whichever row the time is on, so moving, swapping or re-adding times never gives two doses at
 * the same time today. A reminder that lost its time changes to its own row's
 * new time by [changeTime] when no other reminder has that time, otherwise it ends by
 * [endReminder]. Remaining times start at [newReminderStart], except a time in [endsToday] (a
 * reminder at that time is still valid today, its dose already taken), which starts tomorrow.
 * An id that isn't a current reminder (e.g. a stale one restored from saved state) only brings
 * its time.
 *
 * Doses taken today are assigned to the new times by [takenTimes]. A time that already has a
 * taken dose isn't due again today: a reminder there that isn't taken today gives the time up
 * (it ends, or changes to its own row's time), and a new reminder at that time starts tomorrow.
 * So moving times after taking doses never adds a dose today.
 */
fun planEdit(
    current: List<Muistutusjakso>,
    edited: List<EditedTime>,
    today: LocalDate,
    aloituspv: LocalDate,
    takenToday: Set<Long>,
    endsToday: Set<Int> = emptySet()
): List<Muutos> {
    val kept = mutableSetOf<Long>()
    val missing = mutableListOf<Int>()
    edited.map { it.aika }.distinct().forEach { aika ->
        val match = current.firstOrNull { it.aika == aika && it.id !in kept }
        if (match != null) kept += match.id else missing += aika
    }
    val rowTimes = edited.filter { it.id != null }.associate { it.id to it.aika }
    val doneToday = takenTimes(current, kept, rowTimes, takenToday)
    doneToday.forEach { aika ->
        val holder = current.firstOrNull { it.id in kept && it.aika == aika }
        if (holder != null && holder.id !in takenToday) {
            kept -= holder.id
            missing += aika
        }
    }
    val changes = current.filter { it.id !in kept }.flatMap { jakso ->
        val newAika = rowTimes[jakso.id]
        val taken = jakso.id in takenToday
        if (newAika != null && missing.remove(newAika)) {
            changeTime(jakso, newAika, today, taken, aloituspv)
        } else {
            listOf(endReminder(jakso, today, taken))
        }
    }
    val additions = missing.map { Muutos.Lisaa(it, newReminderStart(today, aloituspv)) }
    // A time whose dose is already taken today (or whose reminder ends today after its dose was
    // taken) isn't due again today: a new reminder there starts tomorrow, and so does one changed
    // in place onto it (it has no history).
    val alreadyDone = doneToday + endsToday
    val startsToday = current.filter { it.voimassaAlkaen == today }.map { it.id }.toSet()
    val tomorrow = today.plusDays(1)
    return (changes + additions).flatMap { muutos ->
        when {
            muutos is Muutos.Lisaa &&
                muutos.voimassaAlkaen == today &&
                muutos.aika in alreadyDone -> listOf(muutos.copy(voimassaAlkaen = tomorrow))
            muutos is Muutos.VaihdaAika &&
                muutos.id in startsToday &&
                muutos.aika in alreadyDone -> listOf(muutos, Muutos.SiirraAlku(muutos.id, tomorrow))
            else -> listOf(muutos)
        }
    }
}

/**
 * The new times whose dose is already taken today, for [planEdit]. A reminder taken today whose
 * time is still in the list ([kept]) keeps its dose there. A moved one counts for its row's new
 * time; when that time already has a taken dose, it counts for the time that dose's reminder
 * moved to, and so on (each time visited once). A taken reminder whose row was removed counts
 * for nothing: the user dropped that time.
 */
private fun takenTimes(
    current: List<Muistutusjakso>,
    kept: Set<Long>,
    rowTimes: Map<Long?, Int>,
    takenToday: Set<Long>
): Set<Int> {
    val done = current.filter {
        it.id in kept && it.id in takenToday
    }.map { it.aika }.toMutableSet()
    current.filter { it.id !in kept && it.id in takenToday }.forEach { moved ->
        var aika = rowTimes[moved.id]
        val seen = mutableSetOf<Int>()
        while (aika != null && aika in done && seen.add(aika)) {
            val holder = current.firstOrNull {
                it.id in kept && it.aika == aika && it.id in takenToday
            }
            aika = holder?.let { rowTimes[it.id] }
        }
        if (aika != null && aika !in done) done += aika
    }
    return done
}

/**
 * May a medication's end date change from [oldEnd] to [newEnd] on [today]? An end before
 * today would hide doses that were due on past days, so a *changed* end date must be open or
 * today or later. Keeping an old end date that is already in the past is fine.
 */
fun isValidEndDateEdit(oldEnd: LocalDate?, newEnd: LocalDate?, today: LocalDate): Boolean =
    newEnd == oldEnd || newEnd == null || !newEnd.isBefore(today)

/**
 * A medication that ended before yesterday ([oldEnd]) is extended or made continuous
 * ([newEnd] later or null). The days between the old end and today were not due and must stay
 * so: current reminders end on [oldEnd] (or are deleted if they started after it) and the same
 * times start again at max(today, [aloituspv]). Reminders that already start then are kept.
 * Returns no changes when there is no gap (old end yesterday or later) or no extension.
 */
fun reopenAfterEnd(
    current: List<Muistutusjakso>,
    oldEnd: LocalDate?,
    newEnd: LocalDate?,
    today: LocalDate,
    aloituspv: LocalDate
): List<Muutos> {
    if (oldEnd == null || !oldEnd.isBefore(today.minusDays(1))) return emptyList()
    if (newEnd != null && !newEnd.isAfter(oldEnd)) return emptyList()
    val restart = maxOf(today, aloituspv)
    return current
        .filter { it.voimassaAsti == null && it.voimassaAlkaen.isBefore(restart) }
        .flatMap { jakso ->
            val end = if (jakso.voimassaAlkaen.isAfter(oldEnd)) {
                Muutos.Poista(jakso.id)
            } else {
                Muutos.Paata(jakso.id, oldEnd)
            }
            listOf(end, Muutos.Lisaa(jakso.aika, restart))
        }
}

/**
 * A medication that ended before today ([lopetuspv], as stored before this save) has no due
 * days after its end, so an end that [changes] put later is moved back to [lopetuspv], and a
 * reminder that starts after it is deleted (it never had a due day). Otherwise extending the end
 * date later would make the days between the old end and this save due. [current]
 * are the reminders the changes were planned against.
 */
fun capToMedicationEnd(
    changes: List<Muutos>,
    current: List<Muistutusjakso>,
    lopetuspv: LocalDate?,
    today: LocalDate
): List<Muutos> {
    if (lopetuspv == null || !lopetuspv.isBefore(today)) return changes
    val byId = current.associateBy { it.id }
    return changes.map { muutos ->
        when {
            muutos !is Muutos.Paata || !muutos.voimassaAsti.isAfter(lopetuspv) -> muutos
            byId.getValue(muutos.id).voimassaAlkaen.isAfter(lopetuspv) -> Muutos.Poista(muutos.id)
            else -> Muutos.Paata(muutos.id, lopetuspv)
        }
    }
}

/** Ends every current reminder (medication deleted, or switched to Tarvittaessa). */
fun endAll(current: List<Muistutusjakso>, today: LocalDate, takenToday: Set<Long>): List<Muutos> =
    current.map { endReminder(it, today, it.id in takenToday) }

/**
 * A reminder has no due days in the past when it starts later than today, or starts today and
 * isn't taken today. (A row for a reminder that hasn't started can only come from a stale
 * notification; it isn't history, and the delete cascades it away.)
 */
private fun hasNoHistory(jakso: Muistutusjakso, today: LocalDate, takenToday: Boolean): Boolean =
    jakso.voimassaAlkaen.isAfter(today) ||
        (!jakso.voimassaAlkaen.isBefore(today) && !takenToday)

// ---- Scheduling: next trigger, slots, late doses ----

/** A reminder together with its medication's period: what scheduling needs. */
data class MuistutusAikataulu(
    val muistutusId: Long,
    /** Minutes after midnight, local wall-clock time. */
    val aika: Int,
    val voimassaAlkaen: LocalDate,
    val voimassaAsti: LocalDate?,
    val aloituspv: LocalDate,
    val lopetuspv: LocalDate?
)

/** One dose: reminder [muistutusId] on day [paivamaara], due at [aika]. */
data class Annos(val muistutusId: Long, val paivamaara: LocalDate, val aika: ZonedDateTime)

/** A Laakkeenotto row's key: the dose of reminder [muistutusId] on [paivamaara] is taken. */
data class TakenDose(val muistutusId: Long, val paivamaara: LocalDate)

/** Hours before boot within which a missed dose is still posted as "Myöhässä". */
private const val LATE_WINDOW_HOURS = 2L

/**
 * The earliest due occurrence of [aikataulu] at or after [now], in [now]'s zone, or null
 * when the reminder or the medication has ended. Every date is resolved on its own, so daylight
 * saving is handled: a 3.30 dose on the spring-forward day fires at 4.30, and on the fall-back
 * day at the first 3.30.
 */
fun nextTrigger(now: ZonedDateTime, aikataulu: MuistutusAikataulu): ZonedDateTime? {
    val firstDay = maxOf(now.toLocalDate(), aikataulu.voimassaAlkaen, aikataulu.aloituspv)
    val lastDay = listOfNotNull(aikataulu.voimassaAsti, aikataulu.lopetuspv).minOrNull()
    // The first candidate can only be before now on today's date, so two days are enough.
    for (day in listOf(firstDay, firstDay.plusDays(1))) {
        if (lastDay != null && day.isAfter(lastDay)) return null
        val at = ZonedDateTime.of(day, aikaToLocalTime(aikataulu.aika), now.zone)
        if (!at.isBefore(now)) return at
    }
    return null
}

/**
 * One alarm per distinct time (slot), because allow-while-idle alarms are limited to one
 * per nine minutes per app. Maps each slot's `aika` to the earliest [nextTrigger] of its
 * reminders; slots whose reminders have all ended are left out.
 */
fun nextSlotTriggers(
    aikataulut: List<MuistutusAikataulu>,
    now: ZonedDateTime
): Map<Int, ZonedDateTime> = aikataulut
    .mapNotNull { a -> nextTrigger(now, a)?.let { a.aika to it } }
    .groupBy({ it.first }, { it.second })
    .mapValues { (_, triggers) -> triggers.min() }
    .toSortedMap()

/**
 * Doses that were due in the window [now - 2 h, now) and aren't taken, oldest first. The
 * window may start yesterday, so yesterday's late doses keep yesterday's date.
 *
 * Used only once after BOOT_COMPLETED, for the "Myöhässä" notifications of doses missed while
 * the phone was off. Home doesn't use it: missed doses stay red there through [tilaOf].
 */
fun lateDoses(
    now: ZonedDateTime,
    aikataulut: List<MuistutusAikataulu>,
    taken: Set<TakenDose>
): List<Annos> {
    val windowStart = now.minusHours(LATE_WINDOW_HOURS)
    val days = listOf(now.toLocalDate().minusDays(1), now.toLocalDate())
    return days
        .flatMap { day ->
            aikataulut
                .filter {
                    isDueOn(day, it.voimassaAlkaen, it.voimassaAsti, it.aloituspv, it.lopetuspv)
                }
                .map {
                    Annos(
                        it.muistutusId,
                        day,
                        ZonedDateTime.of(day, aikaToLocalTime(it.aika), now.zone)
                    )
                }
        }
        .filter { !it.aika.isBefore(windowStart) && it.aika.isBefore(now) }
        .filter { TakenDose(it.muistutusId, it.paivamaara) !in taken }
        .sortedWith(compareBy({ it.aika }, { it.muistutusId }))
}
