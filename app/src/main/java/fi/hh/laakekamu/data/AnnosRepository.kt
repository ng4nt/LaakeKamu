package fi.hh.laakekamu.data

import fi.hh.laakekamu.data.entity.Laake
import fi.hh.laakekamu.data.entity.Muistutus
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * What Etusivu needs to work out a day's doses: medications and reminders including deleted and
 * ended ones (past days keep their doses), and the doses taken on a day.
 */
interface AnnosRepository {
    /** Every medication, deleted ones too. */
    fun observeLaakkeet(): Flow<List<Laake>>

    /** Every reminder, ended ones too. */
    fun observeMuistutukset(): Flow<List<Muistutus>>

    /** The ids of the reminders whose dose is taken on [paiva]. */
    fun observeOtetut(paiva: LocalDate): Flow<Set<Long>>
}

/** Room implementation of [AnnosRepository]; [database] opens the database lazily. */
class RoomAnnosRepository(private val database: suspend () -> AppDatabase) : AnnosRepository {
    override fun observeLaakkeet(): Flow<List<Laake>> = flow {
        emitAll(database().laakeDao().observeAll())
    }

    override fun observeMuistutukset(): Flow<List<Muistutus>> = flow {
        emitAll(database().muistutusDao().observeAll())
    }

    override fun observeOtetut(paiva: LocalDate): Flow<Set<Long>> = flow {
        emitAll(database().laakkeenottoDao().observeBetween(paiva, paiva))
    }.map { rows -> rows.map { it.muistutusId }.toSet() }
}
