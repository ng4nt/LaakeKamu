package fi.hh.laakekamu.ui.oirepaivakirja

import fi.hh.laakekamu.data.entity.Laake
import fi.hh.laakekamu.data.entity.Laakkeenotto
import fi.hh.laakekamu.data.entity.Muistutus
import fi.hh.laakekamu.data.entity.Oiremerkinta
import fi.hh.laakekamu.domain.isDayComplete
import fi.hh.laakekamu.ui.etusivu.etusivunRyhmat
import fi.hh.laakekamu.ui.format.SUOMI
import java.text.Collator
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime

/** A dose taken on the selected day: "A aamulääke **5 mg** (klo 8.00)"; [aika] is its time. */
data class OtettuRivi(val nimi: String, val annosmaara: String, val yksikko: String, val aika: Int)

/**
 * The doses taken in [otot], as "name **dose** (klo H.mm)" rows in time order and then in
 * Finnish name order. Every taken row is listed, also when its medication has been deleted or
 * switched to Tarvittaessa since (the diary keeps the taken dose). The time is the
 * reminder's, not the moment of ticking: a past day ticked later still shows its dose time.
 */
fun otetutRivit(
    otot: List<Laakkeenotto>,
    laakkeet: List<Laake>,
    muistutukset: List<Muistutus>
): List<OtettuRivi> {
    val laakeById = laakkeet.associateBy { it.laakeId }
    val muistutusById = muistutukset.associateBy { it.muistutusId }
    val finnish = Collator.getInstance(SUOMI)
    return otot
        .mapNotNull { otto ->
            val muistutus = muistutusById[otto.muistutusId] ?: return@mapNotNull null
            val laake = laakeById[muistutus.laakeId] ?: return@mapNotNull null
            OtettuRivi(laake.nimi, laake.annosmaara, laake.yksikko, muistutus.aika)
        }
        .sortedWith(compareBy<OtettuRivi> { it.aika }.thenBy(finnish) { it.nimi })
}

/**
 * The days of [kuukausi] that are complete at [now]. A day's due doses come from
 * Etusivu's own rule ([etusivunRyhmat]), so the ✓ and Home never disagree.
 * [otot] are the doses taken in [kuukausi].
 */
fun valmiitPaivat(
    kuukausi: YearMonth,
    now: ZonedDateTime,
    laakkeet: List<Laake>,
    muistutukset: List<Muistutus>,
    otot: List<Laakkeenotto>
): Set<LocalDate> {
    val otetutByDay = otot.groupBy({ it.paivamaara }, { it.muistutusId })
    return (1..kuukausi.lengthOfMonth())
        .map { kuukausi.atDay(it) }
        .filter { day ->
            val taken = otetutByDay[day].orEmpty().toSet()
            val ryhmat = etusivunRyhmat(day, now, laakkeet, muistutukset, taken)
            isDayComplete(ryhmat.flatMap { ryhma -> ryhma.rivit.map { it.tila } })
        }
        .toSet()
}

/** A symptom on the selected day; [sivuoire] is the linked medication's name, if any. */
data class OireRivi(val merkinta: Oiremerkinta, val sivuoire: String?) {
    val merkintaId: Long get() = merkinta.merkintaId
    val kuvaus: String get() = merkinta.kuvaus
}

/**
 * The symptoms active on [paiva] (see [isActiveOn]), newest start first, each with its side
 * effect's medication name. The name is found among every medication, deleted ones too, so a
 * link to a deleted medication still shows.
 */
fun oireetPaivalle(
    paiva: LocalDate,
    tanaan: LocalDate,
    oireet: List<Oiremerkinta>,
    laakkeet: List<Laake>
): List<OireRivi> {
    val nimet = laakkeet.associate { it.laakeId to it.nimi }
    val newestFirst = compareByDescending<Oiremerkinta> { it.aloituspv }
        .thenByDescending { it.merkintaId }
    return oireet
        .filter { it.isActiveOn(paiva, tanaan) }
        .sortedWith(newestFirst)
        .map { OireRivi(it, it.laakeId?.let(nimet::get)) }
}

/** The days of [kuukausi] with at least one active symptom (the • marker). */
fun oirePaivat(kuukausi: YearMonth, tanaan: LocalDate, oireet: List<Oiremerkinta>): Set<LocalDate> =
    (1..kuukausi.lengthOfMonth())
        .map { kuukausi.atDay(it) }
        .filter { day -> oireet.any { it.isActiveOn(day, tanaan) } }
        .toSet()

/**
 * A symptom is active from its start to its end, both included. An open end means it still
 * continues, so it reaches [tanaan] but not the days after it, which haven't come yet; an end
 * date the user gave counts as it is.
 */
private fun Oiremerkinta.isActiveOn(day: LocalDate, tanaan: LocalDate): Boolean =
    !day.isBefore(aloituspv) && !day.isAfter(lopetuspv ?: maxOf(tanaan, aloituspv))
