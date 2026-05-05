package fi.hh.laakekamu.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The raw-key passphrase and the plaintext check before the migration. */
class SqlCipherSetupTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun theRawKey_isXQuoted64LowercaseHexDigits() {
        val key = ByteArray(SqlCipherKey.KEY_BYTES) { it.toByte() }
        key[0] = 0x00
        key[1] = 0xFF.toByte()
        key[2] = 0x80.toByte()

        val passphrase = String(SqlCipherSetup.rawKeyPassphrase(key), Charsets.UTF_8)

        // 2 + 64 + 1 characters, the form SQLCipher takes as a raw key.
        assertEquals(67, passphrase.length)
        assertTrue(passphrase.startsWith("x'00ff80030405"))
        assertTrue(passphrase.endsWith("1d1e1f'"))
        assertTrue(passphrase.substring(2, 66).all { it in "0123456789abcdef" })
    }

    @Test
    fun aKeyOfAnotherSize_isRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SqlCipherSetup.rawKeyPassphrase(ByteArray(16))
        }
    }

    @Test
    fun aPlaintextSqliteFile_isRecognisedByItsHeader() {
        val plain = file("plain.db", "SQLite format 3\u0000".toByteArray() + ByteArray(84))
        assertTrue(SqlCipherSetup.isPlaintext(plain))
    }

    @Test
    fun anEncryptedShortOrMissingFile_isNotPlaintext() {
        // An encrypted file starts with its random salt.
        assertFalse(SqlCipherSetup.isPlaintext(file("encrypted.db", ByteArray(100) { 7 })))
        assertFalse(SqlCipherSetup.isPlaintext(file("short.db", "SQLite".toByteArray())))
        assertFalse(SqlCipherSetup.isPlaintext(File(folder.root, "missing.db")))
    }

    private fun file(name: String, bytes: ByteArray) =
        folder.newFile(name).apply { writeBytes(bytes) }
}
