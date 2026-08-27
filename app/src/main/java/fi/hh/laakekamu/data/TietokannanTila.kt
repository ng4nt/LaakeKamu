package fi.hh.laakekamu.data

import android.database.sqlite.SQLiteDatabaseCorruptException
import android.security.keystore.KeyPermanentlyInvalidatedException
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException
import net.zetetic.database.sqlcipher.SQLiteNotADatabaseException

/** Whether the encrypted database could be opened. */
enum class TietokannanTila {
    /** Not opened yet, or being opened. */
    AVAAMATTA,

    /** Open and working. */
    AUKI,

    /**
     * The key or the file can't be used (e.g. the Keystore key is gone). The data is kept; the
     * error screen offers a retry and a start-over the user must confirm.
     */
    EI_SAATAVILLA,

    /**
     * The open failed for another reason (full storage, a Keystore hiccup, a missing native
     * library): the data is probably fine, so the error screen offers only a retry.
     */
    AVAUS_EPAONNISTUI
}

/**
 * The state for an open that failed with [cause]: EI_SAATAVILLA only when the data can't be
 * read with the key the app has, that is, the key is lost ([AvainPuuttuuException],
 * [KeyPermanentlyInvalidatedException]), the wrapped key doesn't authenticate
 * ([AEADBadTagException]), or SQLCipher says the file isn't a database (a wrong key) or is
 * corrupt. Only then can starting over help. Other security exceptions (a temporary Keystore
 * error such as `UnrecoverableKeyException` or `InvalidKeyException`) only get a retry, or the
 * start-over would delete intact data.
 */
fun tilaKunAvausEpaonnistui(cause: Throwable): TietokannanTila {
    val keyOrFileLost = generateSequence(cause) { it.cause }.take(MAX_CAUSES).any {
        it is AvainPuuttuuException ||
            it is KeyPermanentlyInvalidatedException ||
            it is AEADBadTagException ||
            it is SQLiteNotADatabaseException ||
            it is SQLiteDatabaseCorruptException
    }
    return if (keyOrFileLost) TietokannanTila.EI_SAATAVILLA else TietokannanTila.AVAUS_EPAONNISTUI
}

/** The database key, or its Keystore key, is known to be gone (not a temporary error). */
class AvainPuuttuuException(message: String) : GeneralSecurityException(message)

private const val MAX_CAUSES = 10

/** The database couldn't be opened; [AppContainer.tietokannanTila] is then EI_SAATAVILLA. */
class TietokantaEiSaatavillaException(cause: Throwable) :
    Exception("The database can't be opened", cause)
