package fi.hh.laakekamu.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import fi.hh.laakekamu.data.entity.Laakkeenotto
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Taken doses: a row means taken. */
@Dao
interface LaakkeenottoDao {
    /**
     * Marks a dose taken. IGNORE keeps an existing row for the same reminder and day, so a
     * double tap or a race with the notification action doesn't fail on the unique key.
     * Returns the new row id, or -1 if the dose was already taken.
     *
     * IGNORE doesn't cover foreign keys: if the reminder was just deleted, this throws
     * SQLiteConstraintException. Callers acting on a notification must handle that.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(otto: Laakkeenotto): Long

    /** Unticks a dose. Returns the number of rows deleted (0 or 1). */
    @Query("DELETE FROM Laakkeenotto WHERE muistutus_id = :muistutusId AND paivamaara = :paiva")
    suspend fun delete(muistutusId: Long, paiva: LocalDate): Int

    /** Doses taken for days [alku]..[loppu] (inclusive). */
    @Query("SELECT * FROM Laakkeenotto WHERE paivamaara BETWEEN :alku AND :loppu")
    fun observeBetween(alku: LocalDate, loppu: LocalDate): Flow<List<Laakkeenotto>>

    /** Which of [muistutusIds] have their dose taken on [paiva] (the "taken today" check). */
    @Query(
        "SELECT muistutus_id FROM Laakkeenotto " +
            "WHERE paivamaara = :paiva AND muistutus_id IN (:muistutusIds)"
    )
    suspend fun takenOn(paiva: LocalDate, muistutusIds: List<Long>): List<Long>
}
