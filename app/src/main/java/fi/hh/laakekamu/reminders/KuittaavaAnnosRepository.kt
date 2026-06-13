package fi.hh.laakekamu.reminders

import android.content.Context
import fi.hh.laakekamu.data.AnnosRepository
import java.time.LocalDate

/**
 * An [AnnosRepository] whose tick in the app also removes that dose's notification. A snooze still pending finds the dose taken and stays silent.
 */
class KuittaavaAnnosRepository(private val annokset: AnnosRepository, context: Context) :
    AnnosRepository by annokset {
    private val appContext = context.applicationContext

    override suspend fun setOtettu(muistutusId: Long, paiva: LocalDate, otettu: Boolean) {
        annokset.setOtettu(muistutusId, paiva, otettu)
        if (otettu) Ilmoitukset.poista(appContext, muistutusId)
    }
}
