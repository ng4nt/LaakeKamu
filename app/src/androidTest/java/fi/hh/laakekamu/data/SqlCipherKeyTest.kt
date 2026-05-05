package fi.hh.laakekamu.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.GeneralSecurityException
import java.security.KeyStore
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The database key, wrapped with the Android Keystore. */
@RunWith(AndroidJUnit4::class)
class SqlCipherKeyTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun cleanUp() {
        context.deleteSharedPreferences(TEST_PREFS)
        keyStore().deleteEntry(TEST_ALIAS)
    }

    @Test
    fun theKey_isMadeOnce_andUnwrapsToTheSameBytes() {
        val first = testKey().getOrCreate()
        val again = testKey().getOrCreate()

        assertEquals(SqlCipherKey.KEY_BYTES, first.size)
        assertArrayEquals(first, again)
        // Only the wrapped key is stored, never the key itself (in hex or Base64).
        val stored = context.getSharedPreferences(TEST_PREFS, Context.MODE_PRIVATE).all.values
        val hex = first.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        val base64 = java.util.Base64.getEncoder().encodeToString(first)
        assertTrue(stored.none { it.toString().contains(hex, ignoreCase = true) })
        assertTrue(stored.none { it.toString().contains(base64) })
    }

    @Test
    fun aLostKeystoreKey_isASecurityException_notANewKey() {
        testKey().getOrCreate()
        keyStore().deleteEntry(TEST_ALIAS)

        // A later step turns this into the "Tietoja ei voitu avata" screen.
        assertThrows(GeneralSecurityException::class.java) { testKey().getOrCreate() }
    }

    private fun testKey() = SqlCipherKey(context, alias = TEST_ALIAS, prefsName = TEST_PREFS)

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private companion object {
        const val TEST_ALIAS = "laakekamu_test_key"
        const val TEST_PREFS = "laakekamu_test_key"
    }
}
