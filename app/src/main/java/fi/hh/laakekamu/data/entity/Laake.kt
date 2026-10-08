package fi.hh.laakekamu.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import fi.hh.laakekamu.domain.KuinkaUsein
import java.time.LocalDate

/**
 * A medication. Deleting one is a soft delete ([aktiivinen] = false) so its history stays in
 * the diary. The CHECK rules of the SQL design are enforced in domain/LaakeValidators.
 */
@Entity(
    tableName = "Laakkeet",
    foreignKeys = [
        ForeignKey(
            entity = Tyyppi::class,
            parentColumns = ["tyyppi_id"],
            childColumns = ["tyyppi_id"]
        ),
        ForeignKey(
            entity = Yksikko::class,
            parentColumns = ["yksikko"],
            childColumns = ["yksikko"]
        )
    ],
    indices = [
        Index(value = ["tyyppi_id"], name = "idx_laakkeet_tyyppi_id"),
        Index(value = ["yksikko"], name = "idx_laakkeet_yksikko")
    ]
)
data class Laake(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "laake_id")
    val laakeId: Long = 0,
    @ColumnInfo(name = "nimi")
    val nimi: String,
    @ColumnInfo(name = "tyyppi_id")
    val tyyppiId: Long,
    /** Free text so that doses such as "2 x 2,5" fit. */
    @ColumnInfo(name = "annosmaara")
    val annosmaara: String,
    @ColumnInfo(name = "yksikko")
    val yksikko: String,
    @ColumnInfo(name = "kuinka_usein")
    val kuinkaUsein: KuinkaUsein,
    @ColumnInfo(name = "aloituspv")
    val aloituspv: LocalDate,
    /** Null = continuous medication. */
    @ColumnInfo(name = "lopetuspv")
    val lopetuspv: LocalDate?,
    @ColumnInfo(name = "aktiivinen", defaultValue = "1")
    val aktiivinen: Boolean = true
)
