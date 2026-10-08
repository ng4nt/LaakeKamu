package fi.hh.laakekamu.data

import fi.hh.laakekamu.data.entity.Laake
import fi.hh.laakekamu.data.entity.Muistutus
import fi.hh.laakekamu.data.entity.Tyyppi
import fi.hh.laakekamu.data.entity.Yksikko
import fi.hh.laakekamu.domain.EditedTime
import fi.hh.laakekamu.domain.NameError
import kotlinx.coroutines.flow.Flow

/**
 * Medications and their reminder times. The app uses [RoomLaakeRepository]; ViewModel unit tests
 * use a fake. Writes apply rules in one transaction and enforce the CHECK rules.
 */
interface LaakeRepository {
    /** Medication types for the wizard's Tyyppi dropdown. */
    fun observeTyypit(): Flow<List<Tyyppi>>

    /** Dose units for the wizard's Yksikkö dropdown. */
    fun observeYksikot(): Flow<List<Yksikko>>

    /** Medications that aren't deleted, in Finnish alphabetical order. */
    fun observeActive(): Flow<List<Laake>>

    /**
     * The current reminder times (voimassa_asti IS NULL) of every medication, by medication id,
     * earliest first. Lääkelista groups the daily medications by these.
     */
    fun observeCurrentTimes(): Flow<Map<Long, List<Int>>>

    /** The medication with [laakeId], also a deleted one, or null (for the edit wizard). */
    suspend fun get(laakeId: Long): Laake?

    /** The current reminder times of a medication (for pre-filling the edit wizard). */
    suspend fun currentTimes(laakeId: Long): List<Muistutus>

    /** Why [name] can't be used, or null. [editedId] may keep its own name. */
    suspend fun nameError(name: String, editedId: Long? = null): NameError?

    /**
     * Adds a medication with one reminder per time in [times] (none for TARVITTAESSA) and
     * returns its id. @throws IllegalArgumentException if the data breaks or a CHECK rule.
     */
    suspend fun add(laake: Laake, times: List<Int>): Long

    /**
     * Saves an edited medication and applies rules to its [times]. Returns the ids of the
     * reminders it ended, deleted or changed, so their stale notifications and snoozes can be
     * removed.
     */
    suspend fun update(laake: Laake, times: List<EditedTime>): List<Long>

    /**
     * Soft-deletes a medication and ends its reminders. Returns the ids of all its
     * reminders, so their notifications and snoozes can be removed.
     */
    suspend fun delete(laakeId: Long): List<Long>
}
