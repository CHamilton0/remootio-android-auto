package chamilton0.remootioandroidws.shared

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator

class Keystore {

    private val keystore: KeyStore = KeyStore.getInstance("AndroidKeyStore")

    init {
        keystore.load(null)
    }

    fun getKeystore(): KeyStore {
        return keystore
    }

    fun generateKey(alias: String) {
        val keyGenerator =
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_CBC).setUserAuthenticationRequired(false)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7).build()

        keyGenerator.init(keyGenParameterSpec)
        keyGenerator.generateKey()
    }

    fun encrypt(alias: String, inputValue: String): ByteArray {
        val cipher =
            Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_CBC + "/" + KeyProperties.ENCRYPTION_PADDING_PKCS7)
        cipher.init(Cipher.ENCRYPT_MODE, keystore.getKey(alias, null))
        return cipher.doFinal(inputValue.toByteArray())
    }

    fun decrypt(alias: String, encryptedValue: ByteArray): String {
        val cipher =
            Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_CBC + "/" + KeyProperties.ENCRYPTION_PADDING_PKCS7)
        cipher.init(Cipher.DECRYPT_MODE, keystore.getKey(alias, null))
        val decryptedValue = cipher.doFinal(encryptedValue)
        return String(decryptedValue)
    }
}
