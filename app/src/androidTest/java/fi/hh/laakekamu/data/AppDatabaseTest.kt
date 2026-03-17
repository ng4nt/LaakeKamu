package fi.hh.laakekamu.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.hh.laakekamu.data.entity.Laake
import fi.hh.laakekamu.data.entity.Laakkeenotto
import fi.hh.laakekamu.data.entity.Muistutus
import fi.hh.laakekamu.data.entity.Oiremerkinta
import fi.hh.laakekamu.domain.KuinkaUsein
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Integration tests: in-memory Room with the real schema, DAOs and seed callback. */
@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
    private lateinit var db: AppDatabase
    private val today = LocalDate.of(2025, 12, 18)

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).addCallback(SeedCallback).build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    private suspend fun insertLaake(nimi: String = "A aamulääke"): Long {
        val tablettiId = db.valintaDao().observeTyypit().first().first { it.tyyppi == "tabletti" }
            .tyyppiId
        return db.laakeDao().insert(
            Laake(
                nimi = nimi,
                tyyppiId = tablettiId,
                annosmaara = "5",
                yksikko = "mg",
                kuinkaUsein = KuinkaUsein.PAIVITTAIN,
                aloituspv = today.minusDays(10),
                lopetuspv = null
            )
        )
    }

    private suspend fun insertMuistutus(laakeId: Long, aika: Int = 480): Long =
        db.muistutusDao().insert(
            Muistutus(laakeId = laakeId, aika = aika, voimassaAlkaen = today, voimassaAsti = null)
        )

    @Test
    fun seeds_typesAndUnitsExist() = runTest {
        assertEquals(
            SeedCallback.TYYPIT.sorted(),
            db.valintaDao().observeTyypit().first().map { it.tyyppi }
        )
        assertEquals(
            SeedCallback.YKSIKOT.sorted(),
            db.valintaDao().observeYksikot().first().map { it.yksikko }
        )
    }

    @Test(expected = SQLiteConstraintException::class)
    fun foreignKey_unknownUnit_isRejected() = runTest {
        db.laakeDao().insert(
            Laake(
                nimi = "X",
                tyyppiId = 1,
                annosmaara = "1",
                yksikko = "kuppia",
                kuinkaUsein = KuinkaUsein.PAIVITTAIN,
                aloituspv = today,
                lopetuspv = null
            )
        )
    }

    @Test(expected = SQLiteConstraintException::class)
    fun foreignKey_reminderForMissingMedication_isRejected() = runTest {
        insertMuistutus(laakeId = 999)
    }

    @Test
    fun uniqueDosePerDay_secondInsertIsIgnored() = runTest {
        val muistutusId = insertMuistutus(insertLaake())
        val dao = db.laakkeenottoDao()
        val first = dao.insert(
            Laakkeenotto(
                muistutusId = muistutusId,
                paivamaara = today,
                ottoaika = Instant.ofEpochMilli(1)
            )
        )
        val second = dao.insert(
            Laakkeenotto(
                muistutusId = muistutusId,
                paivamaara = today,
                ottoaika = Instant.ofEpochMilli(2)
            )
        )

        assertTrue(first > 0)
        assertEquals(-1L, second)
        val rows = dao.observeBetween(today, today).first()
        assertEquals(1, rows.size)
        // The first tick is kept, not replaced by the second.
        assertEquals(Instant.ofEpochMilli(1), rows.single().ottoaika)
        // The same reminder can be taken on another day.
        assertTrue(
            dao.insert(
                Laakkeenotto(
                    muistutusId = muistutusId,
                    paivamaara = today.minusDays(1),
                    ottoaika = Instant.ofEpochMilli(3)
                )
            ) >
                0
        )
    }

    @Test
    fun untick_deletesOnlyThatDay() = runTest {
        val muistutusId = insertMuistutus(insertLaake())
        val dao = db.laakkeenottoDao()
        dao.insert(
            Laakkeenotto(muistutusId = muistutusId, paivamaara = today, ottoaika = Instant.EPOCH)
        )
        dao.insert(
            Laakkeenotto(
                muistutusId = muistutusId,
                paivamaara = today.minusDays(1),
                ottoaika = Instant.EPOCH
            )
        )

        assertEquals(1, dao.delete(muistutusId, today))
        assertEquals(0, dao.delete(muistutusId, today))
        assertEquals(
            listOf(today.minusDays(1)),
            dao.observeBetween(today.minusDays(7), today).first().map {
                it.paivamaara
            }
        )
    }

    @Test
    fun deletingAReminder_cascadesToItsTakenDoses() = runTest {
        val laakeId = insertLaake()
        val muistutusId = insertMuistutus(laakeId)
        db.laakkeenottoDao().insert(
            Laakkeenotto(muistutusId = muistutusId, paivamaara = today, ottoaika = Instant.EPOCH)
        )

        db.muistutusDao().delete(db.muistutusDao().getByLaake(laakeId).single())

        assertEquals(
            emptyList<Laakkeenotto>(),
            db.laakkeenottoDao().observeBetween(today, today).first()
        )
    }

    @Test
    fun hardDeletedMedication_leavesSymptomButClearsLink() = runTest {
        val laakeId = insertLaake()
        db.oiremerkintaDao().insert(
            Oiremerkinta(kuvaus = "Huimaus", laakeId = laakeId, aloituspv = today, lopetuspv = null)
        )

        // The app only soft-deletes; this checks the schema's ON DELETE SET NULL safety net.
        db.openHelper.writableDatabase.execSQL(
            "DELETE FROM Laakkeet WHERE laake_id = ?",
            arrayOf(laakeId)
        )

        val merkinta = db.oiremerkintaDao().observeAll().first().single()
        assertEquals("Huimaus", merkinta.kuvaus)
        assertNull(merkinta.laakeId)
    }

    @Test
    fun softDeletedMedication_leavesTheActiveList_butStaysReadable() = runTest {
        val aId = insertLaake("A aamulääke")
        insertLaake("B iltalääke")
        val a = db.laakeDao().getById(aId)!!

        db.laakeDao().update(a.copy(aktiivinen = false))

        assertEquals(listOf("B iltalääke"), db.laakeDao().observeActive().first().map { it.nimi })
        assertEquals(false, db.laakeDao().getById(aId)?.aktiivinen)
    }

    @Test
    fun activeMedicationsFlow_emitsAgainAfterAnInsert() = runTest {
        val emissions = Channel<List<String>>(Channel.UNLIMITED)
        // Room's Flow runs on its own executor, so wait in real time, not the test's virtual time.
        withContext(Dispatchers.Default) {
            val collector = launch {
                db.laakeDao().observeActive().collect { list ->
                    emissions.send(list.map { it.nimi })
                }
            }
            withTimeout(5_000) {
                assertEquals(emptyList<String>(), emissions.receive())
                insertLaake("A aamulääke")
                assertEquals(listOf("A aamulääke"), emissions.receive())
            }
            collector.cancel()
        }
    }
}
