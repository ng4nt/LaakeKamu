package fi.hh.laakekamu.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.hh.laakekamu.data.entity.Laake
import fi.hh.laakekamu.data.entity.Laakkeenotto
import fi.hh.laakekamu.data.entity.Muistutus
import fi.hh.laakekamu.data.entity.Oiremerkinta
import fi.hh.laakekamu.domain.EditedTime
import fi.hh.laakekamu.domain.KuinkaUsein
import fi.hh.laakekamu.domain.NameError
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests of the repositories on an in-memory database: the duplicate-name
 * change sets are applied in transactions, and deleting keeps history.
 */
@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private val zone = ZoneId.of("Europe/Helsinki")
    private val today = LocalDate.of(2025, 12, 18)
    private val yesterday = today.minusDays(1)
    private val clock = Clock.fixed(today.atTime(10, 0).atZone(zone).toInstant(), zone)

    private lateinit var db: AppDatabase
    private lateinit var laakkeet: RoomLaakeRepository
    private lateinit var oireet: OireRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).addCallback(SeedCallback).build()
        laakkeet = RoomLaakeRepository({ db }, clock)
        oireet = OireRepository { db }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun laake(
        nimi: String = "A aamulääke",
        kuinkaUsein: KuinkaUsein = KuinkaUsein.PAIVITTAIN,
        aloituspv: LocalDate = today.minusDays(10)
    ) = Laake(
        nimi = nimi,
        tyyppiId = laakkeet.observeTyypit().first().first().tyyppiId,
        annosmaara = "5",
        yksikko = "mg",
        kuinkaUsein = kuinkaUsein,
        aloituspv = aloituspv,
        lopetuspv = null
    )

    /** Inserts a reminder directly, to set up history that started before today. */
    private suspend fun oldReminder(laakeId: Long, aika: Int): Long = db.muistutusDao().insert(
        Muistutus(
            laakeId = laakeId,
            aika = aika,
            voimassaAlkaen = today.minusDays(10),
            voimassaAsti = null
        )
    )

    private suspend fun take(muistutusId: Long, day: LocalDate) {
        db.laakkeenottoDao().insert(
            Laakkeenotto(muistutusId = muistutusId, paivamaara = day, ottoaika = Instant.EPOCH)
        )
    }

    private suspend fun reminders(laakeId: Long) = db.muistutusDao().getByLaake(laakeId)

    @Test
    fun add_daily_createsOneReminderPerTime_validFromToday_andTheFlowEmitsIt() = runTest {
        val id = laakkeet.add(laake(), listOf(480, 1200))

        assertEquals(listOf("A aamulääke"), laakkeet.observeActive().first().map { it.nimi })
        val rows = reminders(id)
        assertEquals(listOf(480, 1200), rows.map { it.aika })
        assertTrue(rows.all { it.voimassaAlkaen == today && it.voimassaAsti == null })
    }

    @Test
    fun add_futureStart_remindersStartOnTheStartDate() = runTest {
        val start = today.plusDays(5)
        val id = laakkeet.add(laake(aloituspv = start), listOf(480))
        assertEquals(start, reminders(id).single().voimassaAlkaen)
    }

    @Test
    fun add_asNeeded_hasNoReminders() = runTest {
        val id = laakkeet.add(laake(kuinkaUsein = KuinkaUsein.TARVITTAESSA), emptyList())
        assertEquals(emptyList<Muistutus>(), reminders(id))
    }

    @Test
    fun add_duplicateName_isReported_andRejected_withoutWritingAnything() = runTest {
        laakkeet.add(laake("Äkäslääke"), listOf(480))
        assertEquals(NameError.Duplicate("äkäslääke"), laakkeet.nameError("äkäslääke"))

        val error = runCatching { laakkeet.add(laake("äkäslääke"), listOf(600)) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals(1, laakkeet.observeActive().first().size)
    }

    @Test
    fun add_invalidTime_rollsBackTheWholeTransaction() = runTest {
        val error = runCatching { laakkeet.add(laake(), listOf(480, 1440)) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals(emptyList<Laake>(), laakkeet.observeActive().first())
    }

    @Test
    fun update_removeAndChangeTimes_keepsPastDaysAndTodaysTakenDose() = runTest {
        val id = laakkeet.add(laake(), listOf(480))
        db.muistutusDao().deleteById(reminders(id).single().muistutusId)
        val morning = oldReminder(id, 480)
        val evening = oldReminder(id, 1200)
        take(morning, today)

        // Morning moves to 9.00 after today's dose was taken; evening is removed; noon is added.
        val changed = laakkeet.update(
            laake().copy(laakeId = id),
            listOf(EditedTime(morning, 540), EditedTime(null, 720))
        )

        val rows = reminders(id).associateBy { it.muistutusId }
        assertEquals(today, rows.getValue(morning).voimassaAsti)
        assertEquals(yesterday, rows.getValue(evening).voimassaAsti)
        val added = rows.values.filter { it.voimassaAsti == null }.associateBy { it.aika }
        assertEquals(today.plusDays(1), added.getValue(540).voimassaAlkaen)
        assertEquals(today, added.getValue(720).voimassaAlkaen)
        // Today's taken dose is still there.
        assertEquals(listOf(morning), db.laakkeenottoDao().takenOn(today, listOf(morning)))
        // Both old reminders changed, so their notifications can be removed.
        assertEquals(setOf(morning, evening), changed.toSet())
    }

    @Test
    fun update_nameOnly_reportsNoChangedReminder() = runTest {
        val id = laakkeet.add(laake(), listOf(480))
        val reminder = reminders(id).single().muistutusId

        val changed = laakkeet.update(
            laake(nimi = "B aamulääke").copy(laakeId = id),
            listOf(EditedTime(reminder, 480))
        )

        assertEquals(emptyList<Long>(), changed)
    }

    @Test
    fun update_switchToAsNeeded_endsEveryReminder() = runTest {
        val id = laakkeet.add(laake(), listOf(480))
        db.muistutusDao().deleteById(reminders(id).single().muistutusId)
        val old = oldReminder(id, 480)

        laakkeet.update(
            laake(kuinkaUsein = KuinkaUsein.TARVITTAESSA).copy(laakeId = id),
            emptyList()
        )

        assertEquals(yesterday, reminders(id).single { it.muistutusId == old }.voimassaAsti)
        assertEquals(emptyList<Muistutus>(), laakkeet.currentTimes(id))
    }

    @Test
    fun update_startMovedLater_movesRemindersThatHaveNotStarted() = runTest {
        val start = today.plusDays(3)
        val id = laakkeet.add(laake(aloituspv = start), listOf(480))

        val reminder = reminders(id).single().muistutusId

        val changed = laakkeet.update(
            laake(aloituspv = today.plusDays(7)).copy(laakeId = id),
            listOf(EditedTime(reminder, 480))
        )

        assertEquals(today.plusDays(7), reminders(id).single().voimassaAlkaen)
        assertEquals(listOf(reminder), changed)
    }

    @Test
    fun update_reopeningAMedicationThatEnded_keepsTheGapDaysOutOfItsReminders() = runTest {
        val oldEnd = today.minusDays(5)
        val id = laakkeet.add(laake().copy(lopetuspv = oldEnd), listOf(480))
        db.muistutusDao().deleteById(reminders(id).single().muistutusId)
        val old = oldReminder(id, 480)

        laakkeet.update(laake().copy(laakeId = id, lopetuspv = null), listOf(EditedTime(old, 480)))

        val rows = reminders(id).associateBy { it.muistutusId }
        assertEquals(oldEnd, rows.getValue(old).voimassaAsti)
        val restarted = rows.values.single { it.voimassaAsti == null }
        assertEquals(480, restarted.aika)
        assertEquals(today, restarted.voimassaAlkaen)
    }

    @Test
    fun update_timeChangedWhileReopening_keepsTheGapDaysOutOfTheOldReminder() = runTest {
        // One save: the end is cleared and 8.00 changed to 9.00.
        val oldEnd = today.minusDays(5)
        val id = laakkeet.add(laake().copy(lopetuspv = oldEnd), listOf(480))
        db.muistutusDao().deleteById(reminders(id).single().muistutusId)
        val old = oldReminder(id, 480)

        laakkeet.update(laake().copy(laakeId = id, lopetuspv = null), listOf(EditedTime(old, 540)))

        val rows = reminders(id)
        assertEquals(oldEnd, rows.single { it.muistutusId == old }.voimassaAsti)
        val current = rows.single { it.voimassaAsti == null }
        assertEquals(540, current.aika)
        assertEquals(today, current.voimassaAlkaen)
    }

    @Test
    fun update_timeChangedAfterTheEnd_thenReopened_keepsTheGapDaysOut() = runTest {
        // Two saves: the time changes while the medication has ended, the end is
        // cleared in a later save.
        val oldEnd = today.minusDays(5)
        val id = laakkeet.add(laake().copy(lopetuspv = oldEnd), listOf(480))
        db.muistutusDao().deleteById(reminders(id).single().muistutusId)
        val old = oldReminder(id, 480)

        laakkeet.update(
            laake().copy(laakeId = id, lopetuspv = oldEnd),
            listOf(EditedTime(old, 540))
        )
        val changed = reminders(id).single { it.voimassaAsti == null }.muistutusId
        val later = today.plusDays(2)
        val laterClock = Clock.fixed(later.atTime(10, 0).atZone(zone).toInstant(), zone)
        RoomLaakeRepository({ db }, laterClock)
            .update(laake().copy(laakeId = id), listOf(EditedTime(changed, 540)))

        val rows = reminders(id)
        assertEquals(oldEnd, rows.single { it.muistutusId == old }.voimassaAsti)
        // Nothing covers the gap between the old end and the reopening day.
        assertTrue(
            rows.none {
                it.voimassaAlkaen.isAfter(oldEnd) &&
                    it.voimassaAlkaen.isBefore(later)
            }
        )
        assertEquals(later, rows.single { it.voimassaAsti == null }.voimassaAlkaen)
    }

    @Test
    fun update_asNeededWhileReopening_endsTheReminderAtTheOldEnd() = runTest {
        val oldEnd = today.minusDays(5)
        val id = laakkeet.add(laake().copy(lopetuspv = oldEnd), listOf(480))
        db.muistutusDao().deleteById(reminders(id).single().muistutusId)
        val old = oldReminder(id, 480)

        laakkeet.update(
            laake(kuinkaUsein = KuinkaUsein.TARVITTAESSA).copy(laakeId = id),
            emptyList()
        )

        assertEquals(oldEnd, reminders(id).single { it.muistutusId == old }.voimassaAsti)
    }

    @Test
    fun update_timeReAddedOnTheDayItWasTakenAndChanged_startsTomorrow() = runTest {
        // 8.00 taken and changed to 10.00, then 8.00 added back the same day.
        val id = laakkeet.add(laake(), listOf(480))
        db.muistutusDao().deleteById(reminders(id).single().muistutusId)
        val first = oldReminder(id, 480)
        take(first, today)
        laakkeet.update(laake().copy(laakeId = id), listOf(EditedTime(first, 600)))
        val ten = reminders(id).single { it.voimassaAsti == null }.muistutusId

        laakkeet.update(
            laake().copy(laakeId = id),
            listOf(EditedTime(ten, 600), EditedTime(null, 480))
        )

        val validToday = reminders(id).filter {
            !it.voimassaAlkaen.isAfter(today) &&
                (it.voimassaAsti == null || !it.voimassaAsti.isBefore(today))
        }
        assertEquals(listOf(first), validToday.map { it.muistutusId })
        val eight = reminders(id).single { it.voimassaAsti == null && it.aika == 480 }
        assertEquals(today.plusDays(1), eight.voimassaAlkaen)
    }

    @Test
    fun update_startCorrectedIntoThePast_afterTakingAndChangingATime_addsNoDoseToday() = runTest {
        // 8.00 taken and changed to 9.00 (9.00 from tomorrow), then the start date is
        // corrected into the past, in a second save and in one save.
        for (oneSave in listOf(false, true)) {
            val nimi = if (oneSave) "Yksi tallennus" else "Kaksi tallennusta"
            val id = laakkeet.add(laake(nimi = nimi), listOf(480))
            db.muistutusDao().deleteById(reminders(id).single().muistutusId)
            val eight = oldReminder(id, 480)
            take(eight, today)
            val corrected = laake(nimi = nimi, aloituspv = today.minusDays(8)).copy(laakeId = id)

            if (oneSave) {
                laakkeet.update(corrected, listOf(EditedTime(eight, 540)))
            } else {
                laakkeet.update(
                    laake(nimi = nimi).copy(laakeId = id),
                    listOf(EditedTime(eight, 540))
                )
                val nine = reminders(id).single { it.voimassaAsti == null }.muistutusId
                laakkeet.update(corrected, listOf(EditedTime(nine, 540)))
            }

            val nine = reminders(id).single { it.voimassaAsti == null }
            assertEquals(nimi, today.plusDays(1), nine.voimassaAlkaen)
        }
    }

    @Test
    fun annosRepository_keepsDeletedMedications_andReportsTodaysTakenDoses() = runTest {
        val annokset = RoomAnnosRepository({ db }, clock)
        // Reminders with history: one started today and untaken would be deleted outright.
        val id = laakkeet.add(laake(), listOf(480))
        db.muistutusDao().deleteById(reminders(id).single().muistutusId)
        val morning = oldReminder(id, 480)
        val evening = oldReminder(id, 1200)
        take(morning, today)
        take(evening, yesterday)

        laakkeet.delete(id)

        assertEquals(listOf(id), annokset.observeLaakkeet().first().map { it.laakeId })
        assertEquals(
            setOf(morning, evening),
            annokset.observeMuistutukset().first().map {
                it.muistutusId
            }.toSet()
        )
        assertEquals(setOf(morning), annokset.observeOtetut(today).first())
        assertEquals(setOf(evening), annokset.observeOtetut(yesterday).first())
    }

    @Test
    fun annosRepository_ticksAndUnticks_onTheGivenDay_andRejectsFutureDays() = runTest {
        // A tick is a row for that day with the tick time; both directions are
        // idempotent (IGNORE keeps the first tick).
        val annokset = RoomAnnosRepository({ db }, clock)
        val id = laakkeet.add(laake(), listOf(480))
        db.muistutusDao().deleteById(reminders(id).single().muistutusId)
        val reminder = oldReminder(id, 480)
        val takenToday = suspend { db.laakkeenottoDao().observeBetween(today, today).first() }

        annokset.setOtettu(reminder, today, true)
        val first = takenToday().single()
        assertEquals(clock.instant(), first.ottoaika)
        annokset.setOtettu(reminder, today, true)
        annokset.setOtettu(reminder, yesterday, true)
        assertEquals(listOf(first), takenToday())
        assertEquals(setOf(reminder), annokset.observeOtetut(yesterday).first())

        annokset.setOtettu(reminder, today, false)
        annokset.setOtettu(reminder, today, false)
        assertEquals(emptySet<Long>(), annokset.observeOtetut(today).first())
        assertEquals(setOf(reminder), annokset.observeOtetut(yesterday).first())

        val tomorrow = today.plusDays(1)
        val error = runCatching { annokset.setOtettu(reminder, tomorrow, true) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals(emptySet<Long>(), annokset.observeOtetut(tomorrow).first())
    }

    @Test
    fun update_endDateMovedIntoThePast_isRejected() = runTest {
        val id = laakkeet.add(laake(), listOf(480))
        val reminder = reminders(id).single().muistutusId
        val error = runCatching {
            laakkeet.update(
                laake().copy(laakeId = id, lopetuspv = yesterday),
                listOf(EditedTime(reminder, 480))
            )
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertNull(db.laakeDao().getById(id)!!.lopetuspv)
    }

    @Test
    fun delete_isSoft_endsReminders_andKeepsTakenHistory() = runTest {
        val id = laakkeet.add(laake(), listOf(480))
        db.muistutusDao().deleteById(reminders(id).single().muistutusId)
        val old = oldReminder(id, 480)
        take(old, today.minusDays(2))

        laakkeet.delete(id)

        assertEquals(emptyList<Laake>(), laakkeet.observeActive().first())
        assertFalse(db.laakeDao().getById(id)!!.aktiivinen)
        assertEquals(yesterday, reminders(id).single().voimassaAsti)
        val history = db.laakkeenottoDao().observeBetween(today.minusDays(7), today).first()
        assertEquals(listOf(old), history.map { it.muistutusId })
    }

    @Test
    fun update_timeChangeAndStartMoveInOneSave_afterTakingToday() = runTest {
        // PlanEdit first, then moveStart against the re-read reminders.
        val id = laakkeet.add(laake(aloituspv = today), listOf(480))
        val first = reminders(id).single().muistutusId
        take(first, today)

        laakkeet.update(
            laake(aloituspv = today.plusDays(7)).copy(laakeId = id),
            listOf(EditedTime(first, 540))
        )

        val rows = reminders(id)
        assertEquals(today, rows.single { it.muistutusId == first }.voimassaAsti)
        val current = rows.single { it.voimassaAsti == null }
        assertEquals(540, current.aika)
        assertEquals(today.plusDays(7), current.voimassaAlkaen)
    }

    @Test
    fun delete_reminderStartedTodayAndNotTaken_isRemovedCompletely() = runTest {
        val id = laakkeet.add(laake(), listOf(480))
        laakkeet.delete(id)
        assertEquals(emptyList<Muistutus>(), reminders(id))
    }

    @Test
    fun update_deletedMedication_isRejected() = runTest {
        val id = laakkeet.add(laake(), listOf(480))
        laakkeet.delete(id)
        val error = runCatching {
            laakkeet.update(laake().copy(laakeId = id), listOf(EditedTime(null, 480)))
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun names_areStoredTrimmed_andListedInFinnishOrder() = runTest {
        listOf("Ödeema", "  Burana ", "äkäslääke", "Åbo").forEach {
            laakkeet.add(laake(it), listOf(480))
        }
        assertEquals(
            listOf("Burana", "Åbo", "äkäslääke", "Ödeema"),
            laakkeet.observeActive().first().map { it.nimi }
        )
    }

    @Test
    fun deletedMedication_nameCanBeUsedAgain() = runTest {
        val id = laakkeet.add(laake("A aamulääke"), listOf(480))
        laakkeet.delete(id)
        assertNull(laakkeet.nameError("A aamulääke"))
    }

    @Test
    fun symptoms_validatedAddedEditedDeleted() = runTest {
        val bad =
            Oiremerkinta(
                kuvaus = "Huimaus",
                laakeId = null,
                aloituspv = today,
                lopetuspv = yesterday
            )
        assertTrue(runCatching { oireet.add(bad) }.exceptionOrNull() is IllegalArgumentException)

        val id = oireet.add(bad.copy(lopetuspv = null))
        oireet.update(oireet.observeAll().first().single().copy(kuvaus = "Vatsakipu"))
        assertEquals(listOf("Vatsakipu"), oireet.observeAll().first().map { it.kuvaus })

        oireet.delete(oireet.observeAll().first().single { it.merkintaId == id })
        assertEquals(emptyList<Oiremerkinta>(), oireet.observeAll().first())
    }
}
