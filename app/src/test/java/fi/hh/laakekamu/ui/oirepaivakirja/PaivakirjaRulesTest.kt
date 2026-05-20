package fi.hh.laakekamu.ui.oirepaivakirja

import fi.hh.laakekamu.data.entity.Laake
import fi.hh.laakekamu.data.entity.Laakkeenotto
import fi.hh.laakekamu.data.entity.Muistutus
import fi.hh.laakekamu.domain.KuinkaUsein
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaivakirjaRulesTest {
    private val helsinki = ZoneId.of("Europe/Helsinki")
    private val today = LocalDate.of(2025, 12, 18)
    private val yesterday = today.minusDays(1)

    /** 10.00 today. */
    private val now = ZonedDateTime.of(today.atTime(10, 0), helsinki)

    // The taken list

    @Test
    fun otetutRivit_byTimeThenFinnishName_withTheReminderTime() {
        val laakkeet = listOf(laake(1, "Öljy"), laake(2, "Burana"), laake(3, "Ärtyvä"))
        val reminders = listOf(muistutus(1, 1, 480), muistutus(2, 2, 480), muistutus(3, 3, 1200))
        // Ticked late in the evening: the row still shows its dose time.
        val otot = listOf(otto(3, today), otto(1, today), otto(2, today))

        val rivit = otetutRivit(otot, laakkeet, reminders)

        assertEquals(listOf("Burana", "Öljy", "Ärtyvä"), rivit.map { it.nimi })
        assertEquals(listOf(480, 480, 1200), rivit.map { it.aika })
        assertEquals(OtettuRivi("Burana", "5", "mg", 480), rivit[0])
    }

    @Test
    fun otetutRivit_keepDeletedAndAsNeededMedications() {
        val deleted = laake(1, "Poistettu").copy(aktiivinen = false)
        val switched = laake(2, "Vaihdettu", KuinkaUsein.TARVITTAESSA)
        val reminders = listOf(
            muistutus(1, 1, 480, asti = yesterday),
            muistutus(2, 2, 540, asti = today)
        )

        val rivit = otetutRivit(
            listOf(otto(1, yesterday), otto(2, today)),
            listOf(deleted, switched),
            reminders
        )

        assertEquals(listOf("Poistettu", "Vaihdettu"), rivit.map { it.nimi })
    }

    // The ✓ days (Home's rule)

    @Test
    fun valmiitPaivat_allDueTaken_isComplete_partlyTakenOrNoDoses_isNot() {
        val laakkeet = listOf(laake(1, "A"), laake(2, "B", start = today.minusDays(2)))
        val reminders = listOf(
            muistutus(1, 1, 480),
            muistutus(2, 2, 1200, alkaen = today.minusDays(2))
        )
        val otot = listOf(
            // 15.12.: only A was due, and it was taken.
            otto(1, today.minusDays(3)),
            // 16.12.: A and B due, both taken.
            otto(1, today.minusDays(2)),
            otto(2, today.minusDays(2)),
            // 17.12.: only A taken.
            otto(1, yesterday)
        )

        val valmiit = valmiitPaivat(YearMonth.from(today), now, laakkeet, reminders, otot)

        assertEquals(setOf(today.minusDays(3), today.minusDays(2)), valmiit)
    }

    @Test
    fun valmiitPaivat_deletedMedication_keepsItsPastCheck() {
        val deleted = laake(1, "A").copy(aktiivinen = false)
        val reminders = listOf(muistutus(1, 1, 480, asti = yesterday))

        val valmiit = valmiitPaivat(
            YearMonth.from(today),
            now,
            listOf(deleted),
            reminders,
            listOf(otto(1, yesterday))
        )

        assertEquals(setOf(yesterday), valmiit)
    }

    @Test
    fun valmiitPaivat_today_followsHome_switchedToAsNeededIsNotDue() {
        // A taken today, then B switched to Tarvittaessa (its reminder ends today, untaken):
        // Home hides B today, so today is complete, as on Home.
        val laakkeet = listOf(laake(1, "A"), laake(2, "B", KuinkaUsein.TARVITTAESSA))
        val reminders = listOf(muistutus(1, 1, 480), muistutus(2, 2, 1200, asti = today))

        val valmiit = valmiitPaivat(
            YearMonth.from(today),
            now,
            laakkeet,
            reminders,
            listOf(otto(1, today))
        )

        assertTrue(today in valmiit)
    }

    private fun laake(
        id: Long,
        nimi: String,
        kuinkaUsein: KuinkaUsein = KuinkaUsein.PAIVITTAIN,
        start: LocalDate = today.minusDays(10)
    ) = Laake(
        laakeId = id,
        nimi = nimi,
        tyyppiId = 1,
        annosmaara = "5",
        yksikko = "mg",
        kuinkaUsein = kuinkaUsein,
        aloituspv = start,
        lopetuspv = null
    )

    private fun muistutus(
        id: Long,
        laakeId: Long,
        aika: Int,
        alkaen: LocalDate = today.minusDays(10),
        asti: LocalDate? = null
    ) = Muistutus(
        muistutusId = id,
        laakeId = laakeId,
        aika = aika,
        voimassaAlkaen = alkaen,
        voimassaAsti = asti
    )

    private fun otto(muistutusId: Long, paiva: LocalDate) = Laakkeenotto(
        muistutusId = muistutusId,
        paivamaara = paiva,
        ottoaika = paiva.atTime(21, 0).atZone(helsinki).toInstant()
    )
}
