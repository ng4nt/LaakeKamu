package fi.hh.laakekamu.data

import fi.hh.laakekamu.data.entity.Oiremerkinta
import fi.hh.laakekamu.domain.isValidDateRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Symptom diary entries. Deleting an entry is a hard delete; the UI asks for confirmation
 * first. The SQL design's CHECK (end not before start) is enforced here.
 */
class OireRepository(private val database: suspend () -> AppDatabase) {
    /** All entries, newest start date first. */
    fun observeAll(): Flow<List<Oiremerkinta>> = flow {
        emitAll(database().oiremerkintaDao().observeAll())
    }

    /** Adds an entry and returns its id. @throws IllegalArgumentException on invalid data. */
    suspend fun add(merkinta: Oiremerkinta): Long {
        requireValid(merkinta)
        return database().oiremerkintaDao().insert(merkinta.copy(merkintaId = 0))
    }

    /** Saves an edited entry. @throws IllegalArgumentException on invalid data. */
    suspend fun update(merkinta: Oiremerkinta) {
        requireValid(merkinta)
        database().oiremerkintaDao().update(merkinta)
    }

    /** Deletes an entry for good (after the user confirmed). */
    suspend fun delete(merkinta: Oiremerkinta) {
        database().oiremerkintaDao().delete(merkinta)
    }

    private fun requireValid(merkinta: Oiremerkinta) {
        require(merkinta.kuvaus.isNotBlank()) { "kuvaus is required" }
        require(isValidDateRange(merkinta.aloituspv, merkinta.lopetuspv)) {
            "lopetuspv before aloituspv"
        }
    }
}
