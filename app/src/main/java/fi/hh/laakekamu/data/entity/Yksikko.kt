package fi.hh.laakekamu.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** A dose unit such as "mg" (seeded list for the wizard's Yksikkö dropdown). */
@Entity(tableName = "Yksikot")
data class Yksikko(
    @PrimaryKey
    @ColumnInfo(name = "yksikko")
    val yksikko: String
)
