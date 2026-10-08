package fi.hh.laakekamu.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * One reminder time of a daily medication, valid from [voimassaAlkaen] to [voimassaAsti]
 * (inclusive). Changing or removing a time ends the old row instead of editing history.
 * "Current" reminders are the ones with [voimassaAsti] = null.
 */
@Entity(
    tableName = "Muistutukset",
    foreignKeys = [
        ForeignKey(
            entity = Laake::class,
            parentColumns = ["laake_id"],
            childColumns = ["laake_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["laake_id"], name = "idx_muistutukset_laake_id")]
)
data class Muistutus(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "muistutus_id")
    val muistutusId: Long = 0,
    @ColumnInfo(name = "laake_id")
    val laakeId: Long,
    /** Minutes after midnight, 0–1439 (480 = 8.00). */
    @ColumnInfo(name = "aika")
    val aika: Int,
    @ColumnInfo(name = "voimassa_alkaen")
    val voimassaAlkaen: LocalDate,
    /** Null = valid until further notice. */
    @ColumnInfo(name = "voimassa_asti")
    val voimassaAsti: LocalDate?
)
