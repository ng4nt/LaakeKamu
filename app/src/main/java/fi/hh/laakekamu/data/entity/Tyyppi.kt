package fi.hh.laakekamu.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A medication type such as "tabletti" (seeded list for the wizard's Tyyppi dropdown). */
@Entity(
    tableName = "Tyypit",
    indices = [Index(value = ["tyyppi"], unique = true)]
)
data class Tyyppi(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "tyyppi_id")
    val tyyppiId: Long = 0,
    @ColumnInfo(name = "tyyppi")
    val tyyppi: String
)
