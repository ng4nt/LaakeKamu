package fi.hh.laakekamu.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import fi.hh.laakekamu.data.entity.Muistutus
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

    /** All reminders, including ended ones (Home and the scheduler apply rules). */
    @Query("SELECT * FROM Muistutukset ORDER BY aika")
    fun observeAll(): Flow<List<Muistutus>>
}
