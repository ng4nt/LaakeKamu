package fi.hh.laakekamu.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.hh.laakekamu.data.entity.Laake
import fi.hh.laakekamu.domain.KuinkaUsein
import java.io.File
import java.security.KeyStore
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A database that can't be opened is reported as EI_SAATAVILLA
 * and kept as it is; only the confirmed start-over deletes it. The test files have their own
 * names, so the app's data isn't touched.
 */
@RunWith(AndroidJUnit4::class)
class TietokannanTilaTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val today = LocalDate.of(2025, 12, 18)
    private val zone = ZoneId.of("Europe/Helsinki")
    private val clock = Clock.fixed(today.atTime(10, 0).atZone(zone).toInstant(), zone)
    private val file: File get() = context.getDatabasePath(NAME)

    @Before
    @After
    fun cleanUp() {
        context.deleteDatabase(NAME)
        context.deleteSharedPreferences(PREFS)
        keyStore().deleteEntry(ALIAS)
    }

    @Test
    fun aLostKeystoreKey_isUnavailable_keepsTheData_andStartOverOpensAnEmptyDatabase() =
        runBlocking<Unit> {
            val bytes = createDatabaseWithBurana()
            keyStore().deleteEntry(ALIAS) // What the debug "simulate key loss" action does.

            val next = container() // The next start of the app.
            assertTrue(openError(next) is TietokantaEiSaatavillaException)
            assertEquals(TietokannanTila.EI_SAATAVILLA, next.tietokannanTila.value)
            // A retry fails the same way, and nothing was deleted on the way.
            assertTrue(openError(next) is TietokantaEiSaatavillaException)
            assertArrayEquals(bytes, file.readBytes())

            next.startOver()
            assertEquals(TietokannanTila.AUKI, next.tietokannanTila.value)
            val db = next.database()
            try {
                assertEquals(emptyList<Laake>(), db.laakeDao().getActive())
                assertEquals(7, db.valintaDao().observeTyypit().first().size)
                assertEquals(7, db.valintaDao().observeYksikot().first().size)
            } finally {
                db.close()
            }
        }

    @Test
    fun anEncryptedFileWithoutItsSavedKey_isUnavailable_andNoNewKeyIsMade() = runBlocking<Unit> {
        val bytes = createDatabaseWithBurana()
        context.deleteSharedPreferences(PREFS)

        val next = container()
        assertTrue(openError(next) is TietokantaEiSaatavillaException)
        assertEquals(TietokannanTila.EI_SAATAVILLA, next.tietokannanTila.value)
        assertFalse(SqlCipherKey(context, ALIAS, PREFS).isSaved())
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test
    fun aKeyThatDoesntFit_isUnavailable_andSqlCipherKeepsTheFile() = runBlocking<Unit> {
        // SQLCipher reports a wrong key as a corrupt file; its error handler must not delete it.
        val bytes = createDatabaseWithBurana()
        SqlCipherKey(context, ALIAS, PREFS).delete()
        SqlCipherKey(context, ALIAS, PREFS).getOrCreate()

        val next = container()
        assertTrue(openError(next) is TietokantaEiSaatavillaException)
        assertEquals(TietokannanTila.EI_SAATAVILLA, next.tietokannanTila.value)
        assertArrayEquals(bytes, file.readBytes())
    }

    /** Creates the encrypted test database with Burana, closes it and returns its bytes. */
    private suspend fun createDatabaseWithBurana(): ByteArray {
        val first = container()
        val db = first.database()
        assertEquals(TietokannanTila.AUKI, first.tietokannanTila.value)
        first.laakeRepository.add(burana(db), listOf(480))
        db.close()
        assertFalse(SqlCipherSetup.isPlaintext(file))
        return file.readBytes()
    }

    private suspend fun openError(container: AppContainer): Throwable? =
        runCatching { container.database() }.exceptionOrNull()

    private fun container() = AppContainer(
        context,
        clock = clock,
        databaseName = NAME,
        keyAlias = ALIAS,
        keyPrefs = PREFS
    )

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private suspend fun burana(db: AppDatabase) = Laake(
        nimi = "Burana",
        tyyppiId = db.valintaDao().observeTyypit().first().first().tyyppiId,
        annosmaara = "400",
        yksikko = "mg",
        kuinkaUsein = KuinkaUsein.PAIVITTAIN,
        aloituspv = today,
        lopetuspv = null
    )

    private companion object {
        const val NAME = "tila-test.db"
        const val ALIAS = "laakekamu_test_tila"
        const val PREFS = "laakekamu_test_tila"
    }
}
