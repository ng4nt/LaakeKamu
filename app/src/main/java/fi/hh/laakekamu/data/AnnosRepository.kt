package fi.hh.laakekamu.data

import fi.hh.laakekamu.data.entity.Laake
import fi.hh.laakekamu.data.entity.Laakkeenotto
import fi.hh.laakekamu.data.entity.Muistutus
import fi.hh.laakekamu.domain.canToggle
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * What Etusivu needs to work out a day's doses: medications and reminders including deleted and
 * ended ones (past days keep their doses), and the doses taken on a day. Ticking a dose is
 * the only write.
 */
interface AnnosRepository {
    /** Every medication, deleted ones too. */
    fun observeLaakkeet(): Flow<List<Laake>>

    /** Every reminder, ended ones too. */
    fun observeMuistutukset(): Flow<List<Muistutus>>

    /** The ids of the reminders whose dose is taken on [paiva]. */
    fun observeOtetut(paiva: LocalDate): Flow<Set<Long>>

    /**
     * Marks the dose of reminder [muistutusId] on [paiva] taken ([otettu], with the current
     * time as ottoaika) or not taken. Both directions are idempotent: a dose already taken keeps
     * its first ottoaika (IGNORE), and unticking an untaken dose changes nothing.
     *
     * A future [paiva] is rejected with IllegalArgumentException. Ticking a reminder that
     * no longer exists throws SQLiteConstraintException (the foreign key).
     */
    suspend fun setOtettu(muistutusId: Long, paiva: LocalDate, otettu: Boolean)
}

/**
 * Room implementation of [AnnosRepository]; [database] opens the database lazily and [clock]
 * gives "today" and the tick time.
 */
class RoomAnnosRepository(
    private val database: suspend () -> AppDatabase,
    private val clock: Clock
) : AnnosRepository {
    override fun observeLaakkeet(): Flow<List<Laake>> = flow {
        emitAll(database().laakeDao().observeAll())
    }

    override fun observeMuistutukset(): Flow<List<Muistutus>> = flow {
        emitAll(database().muistutusDao().observeAll())
    }

    override fun observeOtetut(paiva: LocalDate): Flow<Set<Long>> = flow {
        emitAll(database().laakkeenottoDao().observeBetween(paiva, paiva))
    }.map { rows -> rows.map { it.muistutusId }.toSet() }

    override suspend fun setOtettu(muistutusId: Long, paiva: LocalDate, otettu: Boolean) {
        require(canToggle(paiva, LocalDate.now(clock))) { "$paiva is in the future" }
        val dao = database().laakkeenottoDao()
        if (otettu) {
            dao.insert(
                Laakkeenotto(
                    muistutusId = muistutusId,
                    paivamaara = paiva,
                    ottoaika = Instant.now(clock)
                )
            )
        } else {
            dao.delete(muistutusId, paiva)
        }
    }
}
