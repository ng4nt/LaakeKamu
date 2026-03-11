package fi.hh.laakekamu.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * A taken dose: a row means taken, a missing row means not taken. The unique index on
 * (muistutus_id, paivamaara) stops the same dose from being recorded twice for one day;
 * inserts use IGNORE so a double tap or a notification race can't fail.
 */
@Entity(
    tableName = "Laakkeenotto",
    foreignKeys = [
        ForeignKey(
            entity = Muistutus::class,
            parentColumns = ["muistutus_id"],
            childColumns = ["muistutus_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["muistutus_id", "paivamaara"], unique = true)]
)
data class Laakkeenotto(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "otto_id")
    val ottoId: Long = 0,
    @ColumnInfo(name = "muistutus_id")
    val muistutusId: Long,
    /** The day the dose belongs to, which isn't always the day it was ticked. */
    @ColumnInfo(name = "paivamaara")
    val paivamaara: LocalDate,
    /** When the user ticked it. */
    @ColumnInfo(name = "ottoaika")
    val ottoaika: Instant
)
