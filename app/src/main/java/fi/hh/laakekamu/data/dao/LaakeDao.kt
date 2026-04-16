package fi.hh.laakekamu.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import fi.hh.laakekamu.data.entity.Laake
import kotlinx.coroutines.flow.Flow

/** Medications. Deletion is a soft delete through [update] (aktiivinen = false). */
@Dao
interface LaakeDao {
    /** Inserts a medication and returns its new id. */
    @Insert
    suspend fun insert(laake: Laake): Long

    @Update
    suspend fun update(laake: Laake)

    /** The medication with [laakeId], also when it's deleted (for history), or null. */
    @Query("SELECT * FROM Laakkeet WHERE laake_id = :laakeId")
    suspend fun getById(laakeId: Long): Laake?

    /** Medications that aren't deleted, by name. */
    @Query("SELECT * FROM Laakkeet WHERE aktiivinen = 1 ORDER BY nimi COLLATE NOCASE")
    fun observeActive(): Flow<List<Laake>>

    /** The same as [observeActive], once (for the name check). */
    @Query("SELECT * FROM Laakkeet WHERE aktiivinen = 1")
    suspend fun getActive(): List<Laake>

    /** Every medication, deleted ones too: past days on Etusivu still show their doses. */
    @Query("SELECT * FROM Laakkeet")
    fun observeAll(): Flow<List<Laake>>
}
