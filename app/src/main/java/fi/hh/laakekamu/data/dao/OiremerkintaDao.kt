package fi.hh.laakekamu.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import fi.hh.laakekamu.data.entity.Oiremerkinta
import kotlinx.coroutines.flow.Flow

/** Symptom diary entries. Deleting an entry is a hard delete after confirmation. */
@Dao
interface OiremerkintaDao {
    /** Inserts an entry and returns its new id. */
    @Insert
    suspend fun insert(merkinta: Oiremerkinta): Long

    @Update
    suspend fun update(merkinta: Oiremerkinta)

    @Delete
    suspend fun delete(merkinta: Oiremerkinta)

    /** All entries, newest start date first. */
    @Query("SELECT * FROM Oiremerkinnat ORDER BY aloituspv DESC, merkinta_id DESC")
    fun observeAll(): Flow<List<Oiremerkinta>>
}
