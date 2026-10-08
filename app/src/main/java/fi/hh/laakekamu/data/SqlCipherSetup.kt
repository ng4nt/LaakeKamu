package fi.hh.laakekamu.data

import android.util.Log
import java.io.File
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * SQLCipher for the database file: the raw-key passphrase
 * and the one-time encryption of a plaintext file left from before encryption. Call from the IO
 * dispatcher, after `System.loadLibrary("sqlcipher")` and before Room opens the file.
 */
object SqlCipherSetup {
    private const val TAG = "SqlCipherSetup"

    /** The first 16 bytes of every plaintext SQLite file. */
    private val PLAINTEXT_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    /**
     * SQLCipher's raw-key form `x'<64 hex>'` of [key] (32 bytes), as UTF-8 bytes for
     * `SupportOpenHelperFactory`. SQLCipher uses such a key as is, without its PBKDF2 key
     * derivation, so opening the database stays fast (receivers have under 10 s).
     */
    fun rawKeyPassphrase(key: ByteArray): ByteArray {
        require(key.size == SqlCipherKey.KEY_BYTES) { "A raw key has 32 bytes, not ${key.size}" }
        val hex = key.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        return "x'$hex'".toByteArray(Charsets.UTF_8)
    }

    /** True if [file] exists and starts with the plaintext SQLite header. */
    fun isPlaintext(file: File): Boolean {
        if (!file.isFile || file.length() < PLAINTEXT_HEADER.size) return false
        val header = ByteArray(PLAINTEXT_HEADER.size)
        file.inputStream().use { input ->
            var read = 0
            while (read < header.size) {
                val n = input.read(header, read, header.size - read)
                if (n < 0) return false
                read += n
            }
        }
        return header.contentEquals(PLAINTEXT_HEADER)
    }

    /**
     * Encrypts the plaintext database [file] in place with [passphrase]: every table and row is
     * copied into a new encrypted file with `sqlcipher_export`, which then replaces [file]. The
     * plaintext stays untouched until that last rename, so an interrupted run only leaves a
     * temporary file that the next run removes. Room must not have the file open.
     */
    fun encryptPlaintext(file: File, passphrase: ByteArray) {
        val encrypted = temporaryFile(file)
        deleteWithCompanions(encrypted)
        // CREATE_IF_NECESSARY: an attached database gets the main one's open flags, and ATTACH
        // must create the new file. execSQL and cursors, not rawExecSQL: on the device,
        // rawExecSQL didn't report a failed ATTACH or export.
        val flags = SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY
        SQLiteDatabase.openDatabase(file.path, null, flags).use { plain ->
            // Fold a WAL left by Room into the file, so no plaintext WAL stays behind.
            plain.rawQuery("PRAGMA journal_mode = DELETE").use { it.moveToFirst() }
            val key = String(passphrase, Charsets.UTF_8)
            plain.execSQL("ATTACH DATABASE ? AS encrypted KEY ?", arrayOf(encrypted.path, key))
            plain.rawQuery("SELECT sqlcipher_export('encrypted')").use { it.moveToFirst() }
            // sqlcipher_export doesn't copy the schema version, and Room reads it.
            plain.execSQL("PRAGMA encrypted.user_version = ${plain.version}")
            plain.execSQL("DETACH DATABASE encrypted")
        }
        companions(file).forEach { it.delete() }
        check(encrypted.renameTo(file)) { "Couldn't replace the plaintext database" }
        Log.i(TAG, "Encrypted the plaintext database (schema version kept)")
    }

    /** Deletes the temporary file an interrupted [encryptPlaintext] of [file] may have left. */
    fun deleteLeftovers(file: File) {
        deleteWithCompanions(temporaryFile(file))
    }

    private fun temporaryFile(file: File) = File(file.path + ".encrypting")

    private fun companions(file: File): List<File> =
        listOf("-wal", "-shm", "-journal").map { File(file.path + it) }

    private fun deleteWithCompanions(file: File) {
        file.delete()
        companions(file).forEach { it.delete() }
    }
}
