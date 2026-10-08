package fi.hh.laakekamu.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import fi.hh.laakekamu.data.entity.Muistutus
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * A reminder with what its notification and its alarm need: the medication's name, dose and
 * period.
 */
data class MuistutettavaAnnos(
    val muistutusId: Long,
    /** Minutes after midnight, local wall-clock time. */
    val aika: Int,
    val voimassaAlkaen: LocalDate,
    val voimassaAsti: LocalDate?,
    val nimi: String,
    val annosmaara: String,
    val yksikko: String,
    val aloituspv: LocalDate,
    val lopetuspv: LocalDate?
)

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

    /**
     * The reminders that can still be due on [paiva] or later: valid until then or open-ended,
     * of an active daily medication (as-needed ones are never reminded), by time and name.
     */
    @Query(
        "SELECT m.muistutus_id AS muistutusId, m.aika AS aika, " +
            "m.voimassa_alkaen AS voimassaAlkaen, m.voimassa_asti AS voimassaAsti, " +
            "l.nimi AS nimi, l.annosmaara AS annosmaara, l.yksikko AS yksikko, " +
            "l.aloituspv AS aloituspv, l.lopetuspv AS lopetuspv " +
            "FROM Muistutukset m INNER JOIN Laakkeet l ON l.laake_id = m.laake_id " +
            "WHERE l.aktiivinen = 1 AND l.kuinka_usein = 'PAIVITTAIN' " +
            "AND (m.voimassa_asti IS NULL OR m.voimassa_asti >= :paiva) " +
            "ORDER BY m.aika, l.nimi"
    )
    suspend fun getMuistutettavat(paiva: LocalDate): List<MuistutettavaAnnos>

    /** All reminders, including ended ones (Home and the scheduler apply rules). */
    @Query("SELECT * FROM Muistutukset ORDER BY aika")
    fun observeAll(): Flow<List<Muistutus>>
}
