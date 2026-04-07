package fi.hh.laakekamu.data

import androidx.room.withTransaction
import fi.hh.laakekamu.data.entity.Laake
import fi.hh.laakekamu.data.entity.Muistutus
import fi.hh.laakekamu.data.entity.Tyyppi
import fi.hh.laakekamu.data.entity.Yksikko
import fi.hh.laakekamu.domain.EditedTime
import fi.hh.laakekamu.domain.KuinkaUsein
import fi.hh.laakekamu.domain.Muistutusjakso
import fi.hh.laakekamu.domain.Muutos
import fi.hh.laakekamu.domain.NameError
import fi.hh.laakekamu.domain.capToMedicationEnd
import fi.hh.laakekamu.domain.endAll
import fi.hh.laakekamu.domain.isValidDateRange
import fi.hh.laakekamu.domain.isValidEndDateEdit
import fi.hh.laakekamu.domain.isValidReminderTime
import fi.hh.laakekamu.domain.moveStart
import fi.hh.laakekamu.domain.newReminderStart
import fi.hh.laakekamu.domain.planEdit
import fi.hh.laakekamu.domain.reopenAfterEnd
import fi.hh.laakekamu.domain.validateName
import java.text.Collator
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Room implementation of [LaakeRepository].
 *
 * Every change that touches several rows runs in one transaction and goes through rules
 * (domain/MuistutusRules), so past days never change. Room can't declare the SQL design's CHECK
 * constraints, so this class enforces them before writing. [database] opens the
 * database lazily; [clock] gives "today" (system zone in the app, fixed in tests).
 */
class RoomLaakeRepository(
    private val database: suspend () -> AppDatabase,
    private val clock: Clock
) : LaakeRepository {
    private fun today(): LocalDate = LocalDate.now(clock)

    /** Medication types for the wizard's Tyyppi dropdown. */
    override fun observeTyypit(): Flow<List<Tyyppi>> = flow {
        emitAll(database().valintaDao().observeTyypit())
    }

    /** Dose units for the wizard's Yksikkö dropdown. */
    override fun observeYksikot(): Flow<List<Yksikko>> = flow {
        emitAll(database().valintaDao().observeYksikot())
    }

    /** Medications that aren't deleted, in Finnish alphabetical order (Å, Ä, Ö after Z). */
    override fun observeActive(): Flow<List<Laake>> = flow {
        emitAll(database().laakeDao().observeActive())
    }.map { laakkeet ->
        // A new Collator per emission: Collator isn't thread-safe.
        val finnish = Collator.getInstance(FINNISH)
        laakkeet.sortedWith(compareBy(finnish) { it.nimi })
    }

    /** Current reminder times by medication, earliest first (filtered here: a few rows). */
    override fun observeCurrentTimes(): Flow<Map<Long, List<Int>>> = flow {
        emitAll(database().muistutusDao().observeAll())
    }.map { reminders ->
        reminders.filter { it.voimassaAsti == null }
            .groupBy({ it.laakeId }, { it.aika })
            .mapValues { (_, times) -> times.sorted() }
    }

    /** The current reminder times of a medication (for pre-filling the edit wizard). */
    override suspend fun currentTimes(laakeId: Long): List<Muistutus> =
        database().muistutusDao().getCurrentByLaake(laakeId)

    /** Why [name] can't be used, or null. [editedId] may keep its own name. */
    override suspend fun nameError(name: String, editedId: Long?): NameError? {
        val active = database().laakeDao().getActive().associate { it.laakeId to it.nimi }
        return validateName(name, active, editedId)
    }

    /**
     * Adds a medication. A daily one gets one reminder per time in [times], valid from
     * max(today, aloituspv); an as-needed one gets none. Returns the new id.
     *
     * @throws IllegalArgumentException if the data breaks or a CHECK rule.
     */
    override suspend fun add(laake: Laake, times: List<Int>): Long {
        val db = database()
        return db.withTransaction {
            requireValid(laake, times, editedId = null)
            val laakeId = db.laakeDao().insert(
                laake.copy(laakeId = 0, nimi = laake.nimi.trim(), aktiivinen = true)
            )
            if (laake.kuinkaUsein == KuinkaUsein.PAIVITTAIN) {
                val start = newReminderStart(today(), laake.aloituspv)
                times.forEach { aika ->
                    db.muistutusDao().insert(
                        Muistutus(
                            laakeId = laakeId,
                            aika = aika,
                            voimassaAlkaen = start,
                            voimassaAsti = null
                        )
                    )
                }
            }
            laakeId
        }
    }

    /**
     * Saves an edited medication: its own row, then its reminder times through the reminder rules. [times] is the
     * wizard's time list (existing reminders by id, new ones without). Switching to
     * TARVITTAESSA ends every current reminder; a changed start date moves reminders that
     * haven't started yet; a changed end date may not be in the past, and reopening a medication
     * that ended before yesterday restarts its times from today without touching the gap.
     */
    override suspend fun update(laake: Laake, times: List<EditedTime>) {
        val db = database()
        db.withTransaction {
            val old =
                requireNotNull(db.laakeDao().getById(laake.laakeId)) {
                    "No medication ${laake.laakeId}"
                }
            require(old.aktiivinen) { "A deleted medication can't be edited" }
            requireValid(laake, times.map { it.aika }, editedId = laake.laakeId)
            val today = today()
            require(isValidEndDateEdit(old.lopetuspv, laake.lopetuspv, today)) {
                "A changed lopetuspv can't be in the past"
            }
            db.laakeDao().update(laake.copy(nimi = laake.nimi.trim(), aktiivinen = true))

            // Every end stays inside the medication's period as it was.
            fun capped(changes: List<Muutos>, against: List<Muistutusjakso>) =
                capToMedicationEnd(changes, against, old.lopetuspv, today)

            // Times first, against the reminders as they were...
            val current = currentJaksot(db, laake.laakeId)
            val taken = takenToday(db, current, today)
            val timeChanges = if (laake.kuinkaUsein == KuinkaUsein.TARVITTAESSA) {
                endAll(current, today, taken)
            } else {
                val endsToday = endsToday(db, laake.laakeId, today)
                planEdit(current, times, today, laake.aloituspv, taken, endsToday)
            }
            apply(db, laake.laakeId, current, capped(timeChanges, current))
            // ...then a start-date change, against the reminders as they are now.
            if (laake.aloituspv != old.aloituspv) {
                val afterTimes = currentJaksot(db, laake.laakeId)
                val moves =
                    moveStart(afterTimes, laake.aloituspv, today, takenToday(db, afterTimes, today))
                apply(db, laake.laakeId, afterTimes, capped(moves, afterTimes))
            }
            // ...and last, reopening after an old end date, so the gap days stay not due.
            val afterStart = currentJaksot(db, laake.laakeId)
            val reopen =
                reopenAfterEnd(afterStart, old.lopetuspv, laake.lopetuspv, today, laake.aloituspv)
            apply(db, laake.laakeId, afterStart, reopen)
        }
    }

    /**
     * Deletes a medication: a soft delete (aktiivinen = false) that ends its reminders by
     * So it disappears from the list and Home while its history stays in the diary.
     */
    override suspend fun delete(laakeId: Long) {
        val db = database()
        db.withTransaction {
            val laake = requireNotNull(db.laakeDao().getById(laakeId)) { "No medication $laakeId" }
            db.laakeDao().update(laake.copy(aktiivinen = false))
            val today = today()
            val current = currentJaksot(db, laakeId)
            val ends = endAll(current, today, takenToday(db, current, today))
            apply(db, laakeId, current, capToMedicationEnd(ends, current, laake.lopetuspv, today))
        }
    }

    private suspend fun requireValid(laake: Laake, times: List<Int>, editedId: Long?) {
        nameError(laake.nimi, editedId)?.let { throw IllegalArgumentException("Invalid name: $it") }
        require(laake.annosmaara.isNotBlank()) { "annosmaara is required" }
        require(isValidDateRange(laake.aloituspv, laake.lopetuspv)) { "lopetuspv before aloituspv" }
        if (laake.kuinkaUsein == KuinkaUsein.PAIVITTAIN) {
            require(times.isNotEmpty()) { "A daily medication needs at least one time" }
            require(times.all(::isValidReminderTime)) { "Reminder time outside 0..1439: $times" }
            require(times.toSet().size == times.size) { "Duplicate reminder times: $times" }
        }
    }

    private suspend fun currentJaksot(db: AppDatabase, laakeId: Long): List<Muistutusjakso> =
        db.muistutusDao().getCurrentByLaake(laakeId).map {
            Muistutusjakso(it.muistutusId, it.aika, it.voimassaAlkaen, it.voimassaAsti)
        }

    private suspend fun takenToday(
        db: AppDatabase,
        jaksot: List<Muistutusjakso>,
        today: LocalDate
    ): Set<Long> = if (jaksot.isEmpty()) {
        emptySet()
    } else {
        db.laakkeenottoDao().takenOn(
            today,
            jaksot.map {
                it.id
            }
        ).toSet()
    }

    /** Times of the medication's reminders that end today, so are still valid today. */
    private suspend fun endsToday(db: AppDatabase, laakeId: Long, today: LocalDate): Set<Int> =
        db.muistutusDao().getByLaake(laakeId).filter { it.voimassaAsti == today }.map { it.aika }
            .toSet()

    /** Writes an change set. [current] are the rows the changes were planned against. */
    private suspend fun apply(
        db: AppDatabase,
        laakeId: Long,
        current: List<Muistutusjakso>,
        changes: List<Muutos>
    ) {
        val byId = current.associateBy { it.id }
        val dao = db.muistutusDao()
        changes.forEach { muutos ->
            when (muutos) {
                is Muutos.Lisaa -> {
                    require(isValidReminderTime(muutos.aika)) { "Reminder time outside 0..1439" }
                    dao.insert(
                        Muistutus(
                            laakeId = laakeId,
                            aika = muutos.aika,
                            voimassaAlkaen = muutos.voimassaAlkaen,
                            voimassaAsti = null
                        )
                    )
                }
                is Muutos.Poista -> dao.deleteById(muutos.id)
                is Muutos.Paata -> {
                    val alkaen = byId.getValue(muutos.id).voimassaAlkaen
                    require(isValidDateRange(alkaen, muutos.voimassaAsti)) {
                        "voimassa_asti before voimassa_alkaen"
                    }
                    dao.setVoimassaAsti(muutos.id, muutos.voimassaAsti)
                }
                is Muutos.VaihdaAika -> {
                    require(isValidReminderTime(muutos.aika)) { "Reminder time outside 0..1439" }
                    dao.setAika(muutos.id, muutos.aika)
                }
                is Muutos.SiirraAlku -> dao.setVoimassaAlkaen(muutos.id, muutos.voimassaAlkaen)
            }
        }
    }
}

/** Finnish collation for medication names (SQLite NOCASE only folds ASCII). */
private val FINNISH: Locale = Locale.forLanguageTag("fi-FI")
