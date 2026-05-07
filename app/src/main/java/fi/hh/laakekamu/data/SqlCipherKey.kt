package fi.hh.laakekamu.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import java.security.UnrecoverableKeyException
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The database key: 32 random bytes made once and kept in private
 * SharedPreferences, wrapped with an Android Keystore AES-256-GCM key ([alias]), so the stored
 * bytes are useless without this device's Keystore. The Keystore key needs neither user
 * authentication nor an unlocked device, because reminders must open the database while the
 * phone is locked: a security-vs-availability trade-off. The key itself stays in the Keystore
 * (hardware-backed where the phone has it). Call from the IO dispatcher.
 */
class SqlCipherKey(
    context: Context,
    private val alias: String = KEY_ALIAS,
    prefsName: String = PREFS_NAME
) {
    private val prefs = context.applicationContext.getSharedPreferences(
        prefsName,
        Context.MODE_PRIVATE
    )

    /**
     * The raw key: unwrapped from the prefs, or made and saved on the first call. It's saved
     * (synchronously) before it's returned, so no database file can exist without its key.
     * @throws java.security.GeneralSecurityException if a saved key can't be unwrapped, e.g.
     * its Keystore key is gone.
     */
    fun getOrCreate(): ByteArray {
        val wrapped = prefs.getString(WRAPPED, null)
        val iv = prefs.getString(IV, null)
        if (wrapped != null && iv != null) {
            val keystoreKey = keyStore().getKey(alias, null) as? SecretKey
                ?: throw UnrecoverableKeyException("Keystore key $alias is missing")
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, keystoreKey, GCMParameterSpec(TAG_BITS, decode(iv)))
            return cipher.doFinal(decode(wrapped))
        }
        val key = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKeyForNewWrap())
        val saved = prefs.edit()
            .putString(WRAPPED, encode(cipher.doFinal(key)))
            .putString(IV, encode(cipher.iv))
            .commit()
        if (!saved) {
            // commit() changes the in-memory prefs even when the disk write fails; without this
            // the next call in this process would use a key that isn't on disk.
            prefs.edit().remove(WRAPPED).remove(IV).apply()
            throw IllegalStateException("The database key couldn't be saved")
        }
        return key
    }

    /** True if a wrapped key is saved; it may still fail to unwrap. */
    fun isSaved(): Boolean = prefs.contains(WRAPPED) && prefs.contains(IV)

    /**
     * Deletes the saved key and its Keystore key, for "Aloita alusta" after the user has
     * confirmed it. A database encrypted with the key can't be opened again.
     */
    fun delete() {
        check(prefs.edit().clear().commit()) { "The database key couldn't be deleted" }
        deleteKeystoreKey()
    }

    /**
     * Deletes only the Keystore key, so the saved key can't be unwrapped any more: the debug
     * "simulate key loss" action.
     */
    fun deleteKeystoreKey() {
        keyStore().deleteEntry(alias)
    }

    /** The Keystore key to wrap a new database key with: the existing one, or a new one. */
    private fun keystoreKeyForNewWrap(): SecretKey {
        (keyStore().getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(AES_BITS)
                // No setUserAuthenticationRequired / setUnlockedDeviceRequired (see the class).
                .build()
        )
        return generator.generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    companion object {
        /** The Keystore alias of the wrapping key. */
        const val KEY_ALIAS = "laakekamu_db_key"

        /** The private prefs file with the wrapped key and its IV. */
        const val PREFS_NAME = "laakekamu_db_key"

        /** SQLCipher's raw key size: 32 bytes = 256 bits. */
        const val KEY_BYTES = 32

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val AES_BITS = 256
        private const val TAG_BITS = 128
        private const val WRAPPED = "wrapped_key"
        private const val IV = "iv"

        private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

        private fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)
    }
}
