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
 * today move to max(today, [newAloituspv]). If today's dose was already taken and the start
 * moves into the future, the reminder ends today and a new one starts on the new date, so the
 * taken dose stays visible. Reminders that started earlier are left alone (documented
 * limitation: existing history isn't rewritten).
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
            if (jakso.id in takenToday) {
                listOf(Muutos.Paata(jakso.id, today), Muutos.Lisaa(jakso.aika, newStart))
            } else {
                listOf(Muutos.SiirraAlku(jakso.id, newStart))
            }
        }
}

/**
 * Plans saving the edit wizard's time list against the [current] reminders
 * (voimassa_asti IS NULL). Unchanged times stay, edited times use [changeTime], removed times
 * use [endReminder], and new times start at [newReminderStart]. [takenToday] holds the ids of
 * reminders whose dose is taken today.
 */
fun planEdit(
    current: List<Muistutusjakso>,
    edited: List<EditedTime>,
    today: LocalDate,
    aloituspv: LocalDate,
    takenToday: Set<Long>
): List<Muutos> {
    val editedById = edited.filter { it.id != null }.associateBy { it.id }
    val changes = current.flatMap { jakso ->
        val row = editedById[jakso.id]
        if (row == null) {
            listOf(endReminder(jakso, today, jakso.id in takenToday))
        } else {
            changeTime(jakso, row.aika, today, jakso.id in takenToday, aloituspv)
        }
    }
    val additions = edited
        .filter { it.id == null }
        .map { Muutos.Lisaa(it.aika, newReminderStart(today, aloituspv)) }
    return changes + additions
}

/** Ends every current reminder (medication deleted, or switched to Tarvittaessa). */
fun endAll(current: List<Muistutusjakso>, today: LocalDate, takenToday: Set<Long>): List<Muutos> =
    current.map { endReminder(it, today, it.id in takenToday) }

/** A reminder that starts today or later and isn't taken today has no due days in the past. */
private fun hasNoHistory(jakso: Muistutusjakso, today: LocalDate, takenToday: Boolean): Boolean =
    !jakso.voimassaAlkaen.isBefore(today) && !takenToday

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
