package fi.hh.laakekamu.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/** A symptom diary entry. A non-null [laakeId] marks it as a side effect of that medication. */
@Entity(
    tableName = "Oiremerkinnat",
    foreignKeys = [
        ForeignKey(
            entity = Laake::class,
            parentColumns = ["laake_id"],
            childColumns = ["laake_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["laake_id"], name = "idx_oiremerkinnat_laake_id")]
)
data class Oiremerkinta(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "merkinta_id")
    val merkintaId: Long = 0,
    @ColumnInfo(name = "kuvaus")
    val kuvaus: String,
    /** Null = not a side effect. */
    @ColumnInfo(name = "laake_id")
    val laakeId: Long?,
    @ColumnInfo(name = "aloituspv")
    val aloituspv: LocalDate,
    /** Null = the symptom continues. */
    @ColumnInfo(name = "lopetuspv")
    val lopetuspv: LocalDate?
)
