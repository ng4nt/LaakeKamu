package fi.hh.laakekamu.data

import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Start-over is offered only when the key is lost or doesn't fit. SQLCipher's
 * "not a database" (a wrong key) is checked on the device in TietokannanTilaTest, because the
 * android.database classes can't be made on the JVM.
 */
class TietokannanTilaRulesTest {
    @Test
    fun aLostOrBrokenKey_offersTheStartOver() {
        listOf(
            UnrecoverableKeyException("Keystore key is missing"),
            AEADBadTagException(),
            GeneralSecurityException("The encrypted database has no saved key"),
            IllegalStateException("wrapped", UnrecoverableKeyException())
        ).forEach { assertEquals(TietokannanTila.EI_SAATAVILLA, tilaKunAvausEpaonnistui(it)) }
    }

    @Test
    fun otherFailures_offerOnlyARetry() {
        listOf(
            IOException("No space left on device"),
            ProviderException("Keystore operation failed"),
            UnsatisfiedLinkError("libsqlcipher.so"),
            IllegalStateException("The database key couldn't be saved")
        ).forEach {
            assertEquals(TietokannanTila.AVAUS_EPAONNISTUI, tilaKunAvausEpaonnistui(it))
        }
    }
}
