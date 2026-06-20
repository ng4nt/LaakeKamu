package fi.hh.laakekamu.reminders

import fi.hh.laakekamu.data.LaakeRepository
import fi.hh.laakekamu.data.entity.Laake
import fi.hh.laakekamu.domain.EditedTime

/**
 * A [LaakeRepository] that resets the reminder alarms after every medication change. A failed reschedule is only
 * logged, so it never turns a successful save into an error. A delete also gives the deleted
 * medication's reminder ids to [forget], which removes their notifications and snoozes.
 */
class AjastavaLaakeRepository(
    private val laakkeet: LaakeRepository,
    private val forget: suspend (List<Long>) -> Unit,
    private val reschedule: suspend () -> Unit
) : LaakeRepository by laakkeet {
    override suspend fun add(laake: Laake, times: List<Int>): Long =
        laakkeet.add(laake, times).also { reschedule() }

    override suspend fun update(laake: Laake, times: List<EditedTime>) {
        laakkeet.update(laake, times)
        reschedule()
    }

    override suspend fun delete(laakeId: Long): List<Long> = laakkeet.delete(laakeId).also {
        forget(it)
        reschedule()
    }
}
