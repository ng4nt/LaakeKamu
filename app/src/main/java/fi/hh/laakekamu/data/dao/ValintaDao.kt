package fi.hh.laakekamu.data.dao

import androidx.room.Dao
import androidx.room.Query
import fi.hh.laakekamu.data.entity.Tyyppi
import fi.hh.laakekamu.data.entity.Yksikko
import kotlinx.coroutines.flow.Flow

/** The seeded choice lists of the add/edit wizard (Tyyppi and Yksikkö dropdowns). */
@Dao
interface ValintaDao {
    /** All medication types in alphabetical order. */
    @Query("SELECT * FROM Tyypit ORDER BY tyyppi")
    fun observeTyypit(): Flow<List<Tyyppi>>

    /** All dose units in alphabetical order. */
    @Query("SELECT * FROM Yksikot ORDER BY yksikko")
    fun observeYksikot(): Flow<List<Yksikko>>
}
