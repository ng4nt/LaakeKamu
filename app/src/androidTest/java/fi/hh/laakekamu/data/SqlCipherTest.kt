package fi.hh.laakekamu.data

import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteException
import android.os.SystemClock
import android.util.Log
import androidx.room.Room
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
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The plaintext → encrypted migration with every row intact, and the raw key
 * skipping SQLCipher's key derivation. The key itself: SqlCipherKeyTest.
 */
@RunWith(AndroidJUnit4::class)
class SqlCipherTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val today = LocalDate.of(2025, 12, 18)
    private val zone = ZoneId.of("Europe/Helsinki")
    private val clock = Clock.fixed(today.atTime(10, 0).atZone(zone).toInstant(), zone)
    private val dbFiles = mutableListOf<String>()

    @Before
    fun loadSqlCipher() {
        System.loadLibrary("sqlcipher")
    }

    @After
    fun cleanUp() {
        dbFiles.forEach { context.deleteDatabase(it) }
        context.deleteSharedPreferences(TEST_PREFS)
        keyStore().deleteEntry(TEST_ALIAS)
    }

    @Test
    fun aPlaintextDatabase_isEncrypted_withEveryRowIntact() = runBlocking<Unit> {
        val name = database("plaintext-fixture.db")
        // The fixture: a plaintext Room database as earlier builds left on the devices.
        val plain = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addCallback(SeedCallback)
            .build()
        val laakeId = RoomLaakeRepository({ plain }, clock).add(burana(plain), listOf(480, 1200))
        val reminders = plain.muistutusDao().getByLaake(laakeId)
        RoomAnnosRepository({ plain }, clock).setOtettu(reminders.first().muistutusId, today, true)
        plain.close()
        val file = context.getDatabasePath(name)
        assertTrue(SqlCipherSetup.isPlaintext(file))

        val passphrase = SqlCipherSetup.rawKeyPassphrase(testKey().getOrCreate())
        SqlCipherSetup.encryptPlaintext(file, passphrase)

        assertFalse(SqlCipherSetup.isPlaintext(file))
        assertFalse(File(file.path + ".encrypting").exists())
        val encrypted = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .addCallback(SeedCallback)
            .build()
        try {
            assertEquals(listOf("Burana"), encrypted.laakeDao().getActive().map { it.nimi })
            assertEquals(reminders, encrypted.muistutusDao().getByLaake(laakeId))
            assertEquals(
                listOf(reminders.first().muistutusId),
                encrypted.laakkeenottoDao().observeBetween(today, today).first()
                    .map { it.muistutusId }
            )
            // The seeds were copied, not added again (Room saw the schema version).
            assertEquals(7, encrypted.valintaDao().observeTyypit().first().size)
            assertEquals(7, encrypted.valintaDao().observeYksikot().first().size)
        } finally {
            encrypted.close()
        }
        // Plain SQLite can't read it. The no-op handler keeps Android from deleting it.
        val noOp = DatabaseErrorHandler { }
        assertThrows(SQLiteException::class.java) {
            android.database.sqlite.SQLiteDatabase.openDatabase(
                file.path,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
                noOp
            ).use { it.rawQuery("SELECT count(*) FROM sqlite_master", null).use { c -> c.count } }
        }
    }

    @Test
    fun theRawKey_skipsKeyDerivation_soItOpensMuchFaster() {
        // A passphrase goes through 256 000 PBKDF2 rounds; the raw x'…' key is used as is.
        val raw = SqlCipherSetup.rawKeyPassphrase(testKey().getOrCreate())
        val rawMillis = openTwiceAndTime(database("raw-key.db"), raw)
        val passMillis = openTwiceAndTime(database("passphrase.db"), "salasana".toByteArray())
        Log.i("SqlCipherTest", "Open + query: raw key $rawMillis ms, passphrase $passMillis ms")

        assertTrue("raw $rawMillis ms vs passphrase $passMillis ms", rawMillis * 3 < passMillis)
        assertTrue("raw key open took $rawMillis ms", rawMillis < 1_000)
    }

    /** Creates [name] with [password], then times opening it again and reading the schema. */
    private fun openTwiceAndTime(name: String, password: ByteArray): Long {
        val path = context.getDatabasePath(name).path
        SQLiteDatabase.openOrCreateDatabase(path, password, null, null, null).use {
            it.execSQL("CREATE TABLE t (x INTEGER)")
        }
        val start = SystemClock.elapsedRealtime()
        val flags = SQLiteDatabase.OPEN_READWRITE
        SQLiteDatabase.openDatabase(path, password, null, flags, null, null).use {
            it.rawQuery("SELECT count(*) FROM sqlite_master").use { c -> c.moveToFirst() }
        }
        return SystemClock.elapsedRealtime() - start
    }

    private fun database(name: String): String {
        context.deleteDatabase(name)
        dbFiles += name
        return name
    }

    private fun testKey() = SqlCipherKey(context, alias = TEST_ALIAS, prefsName = TEST_PREFS)

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
        const val TEST_ALIAS = "laakekamu_test_cipher"
        const val TEST_PREFS = "laakekamu_test_cipher"
    }
}
