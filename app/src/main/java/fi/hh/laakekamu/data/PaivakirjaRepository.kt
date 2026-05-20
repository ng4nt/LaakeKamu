package fi.hh.laakekamu.data

import fi.hh.laakekamu.data.entity.Laake
import fi.hh.laakekamu.data.entity.Laakkeenotto
import fi.hh.laakekamu.data.entity.Muistutus
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * What Oirepäiväkirja reads to show taken doses and complete days: medications and
 * reminders including deleted and ended ones (a deleted medication's taken doses stay in the
 * diary), and the doses taken over a range of days. Read-only.
 */
interface PaivakirjaRepository {
    /** Every medication, deleted ones too. */
    fun observeLaakkeet(): Flow<List<Laake>>

    /** Every reminder, ended ones too. */
    fun observeMuistutukset(): Flow<List<Muistutus>>

    /** The doses taken on days [alku]..[loppu] (inclusive). */
    fun observeOtot(alku: LocalDate, loppu: LocalDate): Flow<List<Laakkeenotto>>
}

/** Room implementation of [PaivakirjaRepository]; [database] opens the database lazily. */
class RoomPaivakirjaRepository(private val database: suspend () -> AppDatabase) :
    PaivakirjaRepository {
    override fun observeLaakkeet(): Flow<List<Laake>> = flow {
        emitAll(database().laakeDao().observeAll())
    }

    override fun observeMuistutukset(): Flow<List<Muistutus>> = flow {
        emitAll(database().muistutusDao().observeAll())
    }

    override fun observeOtot(alku: LocalDate, loppu: LocalDate): Flow<List<Laakkeenotto>> = flow {
        emitAll(database().laakkeenottoDao().observeBetween(alku, loppu))
    }
}
