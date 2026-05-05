package fi.hh.laakekamu.data

import android.content.Context
import java.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
 * [clock] gives "today" in the system time zone; tests use a fixed one.
 */
class AppContainer(
    context: Context,
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    val clock: Clock = SystemZoneClock
) {
    private val appContext = context.applicationContext
    private val databaseMutex = Mutex()

    @Volatile
    private var database: AppDatabase? = null

    /** The app database, created on first use. */
    suspend fun database(): AppDatabase = database ?: databaseMutex.withLock {
        database ?: withContext(ioDispatcher) { openEncrypted() }.also { database = it }
    }

    /**
     * Loads SQLCipher, gets the key (made on the first run), encrypts a plaintext file
     * left from before encryption and builds Room on SQLCipher with the raw key.
     */
    private fun openEncrypted(): AppDatabase {
        System.loadLibrary("sqlcipher")
        val passphrase = SqlCipherSetup.rawKeyPassphrase(SqlCipherKey(appContext).getOrCreate())
        val file = appContext.getDatabasePath(AppDatabase.NAME)
        if (SqlCipherSetup.isPlaintext(file)) {
            SqlCipherSetup.encryptPlaintext(file, passphrase)
        }
        return AppDatabase.build(appContext, SupportOpenHelperFactory(passphrase))
    }

    val laakeRepository: LaakeRepository by lazy { RoomLaakeRepository(::database, clock) }

    val annosRepository: AnnosRepository by lazy { RoomAnnosRepository(::database, clock) }

    val oireRepository: OireRepository by lazy { OireRepository(::database) }
}
