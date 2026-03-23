package fi.hh.laakekamu.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import fi.hh.laakekamu.data.entity.Muistutus
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Reminder times with validity periods. Multi-row changes run in a transaction. */
@Dao
interface MuistutusDao {
    /** Inserts a reminder and returns its new id. */
    @Insert
    suspend fun insert(muistutus: Muistutus): Long

    @Update
    suspend fun update(muistutus: Muistutus)

    @Delete
    suspend fun delete(muistutus: Muistutus)

    /** Every reminder of a medication, including ended ones, oldest validity first. */
    @Query("SELECT * FROM Muistutukset WHERE laake_id = :laakeId ORDER BY voimassa_alkaen, aika")
    suspend fun getByLaake(laakeId: Long): List<Muistutus>

    /** The current reminders of a medication (voimassa_asti IS NULL), by time. */
    @Query(
        "SELECT * FROM Muistutukset WHERE laake_id = :laakeId AND voimassa_asti IS NULL " +
            "ORDER BY aika"
    )
    suspend fun getCurrentByLaake(laakeId: Long): List<Muistutus>

    // Single-column updates used when the repository applies an change set.

    @Query("DELETE FROM Muistutukset WHERE muistutus_id = :muistutusId")
    suspend fun deleteById(muistutusId: Long)

    @Query("UPDATE Muistutukset SET voimassa_asti = :asti WHERE muistutus_id = :muistutusId")
    suspend fun setVoimassaAsti(muistutusId: Long, asti: LocalDate)

    @Query("UPDATE Muistutukset SET voimassa_alkaen = :alkaen WHERE muistutus_id = :muistutusId")
    suspend fun setVoimassaAlkaen(muistutusId: Long, alkaen: LocalDate)

    @Query("UPDATE Muistutukset SET aika = :aika WHERE muistutus_id = :muistutusId")
    suspend fun setAika(muistutusId: Long, aika: Int)

    /** All reminders, including ended ones (Home and the scheduler apply rules). */
    @Query("SELECT * FROM Muistutukset ORDER BY aika")
    fun observeAll(): Flow<List<Muistutus>>
}
