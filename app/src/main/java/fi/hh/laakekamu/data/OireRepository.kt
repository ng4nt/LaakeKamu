package fi.hh.laakekamu.data

import fi.hh.laakekamu.data.entity.Laake
import fi.hh.laakekamu.data.entity.Oiremerkinta
import fi.hh.laakekamu.domain.isValidDateRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Symptom diary entries. Deleting an entry is a hard delete; the UI asks for confirmation
 * first. The SQL design's CHECK (end not before start) is enforced here.
 */
interface OireRepository {
    /** All entries, newest start date first. */
    fun observeAll(): Flow<List<Oiremerkinta>>

    /** The medications a symptom can be linked to: those with `aktiivinen = 1`. */
    fun observeAktiivisetLaakkeet(): Flow<List<Laake>>

    /** Adds an entry and returns its id. @throws IllegalArgumentException on invalid data. */
    suspend fun add(merkinta: Oiremerkinta): Long

    /** Saves an edited entry. @throws IllegalArgumentException on invalid data. */
    suspend fun update(merkinta: Oiremerkinta)

    /** Deletes an entry for good (after the user confirmed). */
    suspend fun delete(merkinta: Oiremerkinta)
}

/** The Room [OireRepository]; [database] opens the database lazily. */
fun OireRepository(database: suspend () -> AppDatabase): OireRepository =
    RoomOireRepository(database)

/** Room implementation of [OireRepository]. */
class RoomOireRepository(private val database: suspend () -> AppDatabase) : OireRepository {
    override fun observeAll(): Flow<List<Oiremerkinta>> = flow {
        emitAll(database().oiremerkintaDao().observeAll())
    }

    override fun observeAktiivisetLaakkeet(): Flow<List<Laake>> = flow {
        emitAll(database().laakeDao().observeActive())
    }

    override suspend fun add(merkinta: Oiremerkinta): Long {
        requireValid(merkinta)
        return database().oiremerkintaDao().insert(merkinta.copy(merkintaId = 0))
    }

    override suspend fun update(merkinta: Oiremerkinta) {
        requireValid(merkinta)
        database().oiremerkintaDao().update(merkinta)
    }

    override suspend fun delete(merkinta: Oiremerkinta) {
        database().oiremerkintaDao().delete(merkinta)
    }

    private fun requireValid(merkinta: Oiremerkinta) {
        require(merkinta.kuvaus.isNotBlank()) { "kuvaus is required" }
        require(isValidDateRange(merkinta.aloituspv, merkinta.lopetuspv)) {
            "lopetuspv before aloituspv"
        }
    }
}
