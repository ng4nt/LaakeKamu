package fi.hh.laakekamu.data

import android.content.Context
import android.util.Log
import fi.hh.laakekamu.reminders.AjastavaLaakeRepository
import fi.hh.laakekamu.reminders.Ilmoitukset
import fi.hh.laakekamu.reminders.KuittaavaAnnosRepository
import fi.hh.laakekamu.reminders.MuistutusScheduler
import java.security.GeneralSecurityException
import java.time.Clock
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Manual dependency container, created once in LaakeKamuApp.
 *
 * The database is opened lazily through [database], never in Application.onCreate. The Mutex
 * makes sure only one instance is created even when a receiver and the UI ask at the same time.
 * Non-Room I/O (the Keystore and SQLCipher setup) runs on the injected [ioDispatcher].
 * [clock] gives "today" in the system time zone; tests use a fixed one. Tests also pass their
 * own [databaseName], [keyAlias] and [keyPrefs], so they never touch the app's data.
 */
class AppContainer(
    context: Context,
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    val clock: Clock = SystemZoneClock,
    private val databaseName: String = AppDatabase.NAME,
    private val keyAlias: String = SqlCipherKey.KEY_ALIAS,
    private val keyPrefs: String = SqlCipherKey.PREFS_NAME
) {
    private val appContext = context.applicationContext
    private val databaseMutex = Mutex()

    @Volatile
    private var database: AppDatabase? = null

    private val tila = MutableStateFlow(TietokannanTila.AVAAMATTA)

    /** Whether the database could be opened; the UI shows the error screen when it can't. */
    val tietokannanTila: StateFlow<TietokannanTila> = tila.asStateFlow()

    /**
     * The app database, opened on first use. After a failed open the next call tries again.
     * @throws TietokantaEiSaatavillaException if it can't be opened.
     */
    suspend fun database(): AppDatabase = database ?: databaseMutex.withLock {
        database ?: open().also { database = it }
    }

    /**
     * "Aloita alusta", only after the user has confirmed it: deletes the saved key and its Keystore key, then the database files, then
     * opens a new database with only the seeds. The key goes first: if the process dies in
     * between, the file is left without its key and the error screen offers the start-over
     * again, instead of a missing file with a key that no longer unwraps.
     *
     * Then the old data's notifications are removed and the alarms reset: the
     * new database has no reminders, so every old slot is cancelled. This runs after the lock,
     * because the reschedule reads through [database].
     */
    suspend fun startOver(): AppDatabase = deleteAndReopen().also {
        Ilmoitukset.poistaKaikki(appContext)
        scheduler.rescheduleAfterChange()
    }

    private suspend fun deleteAndReopen(): AppDatabase = databaseMutex.withLock {
        try {
            withContext(ioDispatcher) {
                database?.close()
                database = null
                key().delete()
                appContext.deleteDatabase(databaseName)
                SqlCipherSetup.deleteLeftovers(appContext.getDatabasePath(databaseName))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Starting over failed before the new database was made", e)
            throw e
        }
        Log.i(TAG, "Started over with an empty database, as the user confirmed")
        open().also { database = it }
    }

    private suspend fun open(): AppDatabase = try {
        withContext(ioDispatcher) { openEncrypted() }.also { tila.value = TietokannanTila.AUKI }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        unavailable(e)
    } catch (e: UnsatisfiedLinkError) {
        unavailable(e)
    }

    private fun unavailable(cause: Throwable): Nothing {
        Log.e(TAG, "The database can't be opened", cause)
        tila.value = tilaKunAvausEpaonnistui(cause)
        throw TietokantaEiSaatavillaException(cause)
    }

    /**
     * Loads SQLCipher, gets the key (made on the first run), encrypts a plaintext file
     * left from before encryption and opens Room on SQLCipher with the raw key. The file is opened
     * here, not at the first query, so a key that doesn't fit fails here. SQLCipher keeps the
     * file then: its error handler deletes nothing.
     */
    private fun openEncrypted(): AppDatabase {
        System.loadLibrary("sqlcipher")
        val file = appContext.getDatabasePath(databaseName)
        val key = key()
        val plaintext = SqlCipherSetup.isPlaintext(file)
        if (file.exists() && !plaintext && !key.isSaved()) {
            // The saved key is gone; a new one couldn't open the file, so don't make one.
            throw GeneralSecurityException("The encrypted database has no saved key")
        }
        val passphrase = SqlCipherSetup.rawKeyPassphrase(key.getOrCreate())
        if (plaintext) {
            SqlCipherSetup.encryptPlaintext(file, passphrase)
        }
        val db = AppDatabase.build(appContext, SupportOpenHelperFactory(passphrase), databaseName)
        try {
            db.openHelper.writableDatabase
        } catch (e: Exception) {
            db.close()
            throw e
        }
        return db
    }

    private fun key() = SqlCipherKey(appContext, keyAlias, keyPrefs)

    /** The reminder alarms; one instance, so its Mutex covers the whole process. */
    val scheduler: MuistutusScheduler by lazy {
        MuistutusScheduler(appContext, ::database, clock, ioDispatcher)
    }

    // Every medication change resets the reminder alarms.
    val laakeRepository: LaakeRepository by lazy {
        AjastavaLaakeRepository(RoomLaakeRepository(::database, clock)) {
            scheduler.rescheduleAfterChange()
        }
    }

    // A tick in the app also removes that dose's notification.
    val annosRepository: AnnosRepository by lazy {
        KuittaavaAnnosRepository(RoomAnnosRepository(::database, clock), appContext)
    }

    val oireRepository: OireRepository by lazy { OireRepository(::database) }

    val paivakirjaRepository: PaivakirjaRepository by lazy { RoomPaivakirjaRepository(::database) }

    private companion object {
        const val TAG = "AppContainer"
    }
}
