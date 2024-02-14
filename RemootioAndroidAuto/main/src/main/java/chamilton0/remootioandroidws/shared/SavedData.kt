package chamilton0.remootioandroidws.shared

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.preference.PreferenceManager
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.IvParameterSpec

class SavedData(context: Context) {
    private val KEY_ALIAS = "REMOOTIOKEYALIAS"
    private var preferences: SharedPreferences? = null

    private val KEY_GARAGE_IP = "garageIp"
    private val KEY_GATE_IP = "gateIp"
    private val KEY_GARAGE_AUTH = "garageApiAuthKey"
    private val KEY_GARAGE_SECRET = "garageApiSecretKey"
    private val KEY_GATE_AUTH = "gateApiSecretKey"
    private val KEY_GATE_SECRET = "gateApiSecretKey"
    private var keyStore = KeyStore.getInstance("AndroidKeyStore")

    init {
        keyStore.load(null)

        preferences =
            PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        createKeyIfNotExists()

    }

    private fun createKeyIfNotExists() {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            println("Generating secret key")
            val keyGenerator =
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val keyGenSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .build()
            keyGenerator.init(keyGenSpec)
            keyGenerator.generateKey()
        }
    }

    fun saveToSecureStorage(key: String, value: String) {
        try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
            val secretKeyEntry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
            val secretKey = secretKeyEntry.secretKey
            println("secret key entry $secretKeyEntry.")
            println("Secret key save ${secretKey.encoded}")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryptedValue = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val encodedValue =
                Base64.getEncoder().encodeToString(encryptedValue)
            println("$key Encoded value: $encodedValue")
            val ivEncoded = Base64.getEncoder().encodeToString(iv)
            println("save IV $ivEncoded")
            println("save IV ${iv.toString()}")
            preferences!!.edit().putString(key, encodedValue).commit()
            println("saved key")
            preferences!!.edit().putString("$key-iv", ivEncoded).commit()
            println("saved iv")
            println("current entries")
            for (mutableEntry in preferences!!.all) {
                println(mutableEntry.key + " " + mutableEntry.value)
            }
        } catch (exception: Exception) {
            exception.printStackTrace()
        }
    }

    fun getFromSecureStorage(key: String): String? {
        val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val secretKeyEntry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
        println("secret key entry $secretKeyEntry")
        val secretKey = secretKeyEntry.secretKey
        val ivEncoded = preferences!!.getString("$key-iv", null) ?: return null
        val iv = Base64.getDecoder().decode(ivEncoded)
        println("load IV $ivEncoded")
        println("load IV ${iv.toString()}")
        val ivParams = IvParameterSpec(iv)
        try {
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParams)
            val encodedValue = preferences!!.getString(key, null) ?: return null
            return encodedValue
            println("$key Encoded value: $encodedValue")
            val decodedValue = Base64.getDecoder().decode(encodedValue)
            val decryptedValue = cipher.doFinal(decodedValue)
            return String(decryptedValue, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }


    fun saveGarageIp(value: String) {
        saveToSecureStorage(KEY_GARAGE_IP, value)
    }

    fun saveGateIp(value: String) {
        saveToSecureStorage(KEY_GATE_IP, value)
    }

    fun saveGarageAuth(value: String) {
        saveToSecureStorage(KEY_GARAGE_AUTH, value)
    }

    fun saveGarageSecret(value: String) {
        saveToSecureStorage(KEY_GARAGE_SECRET, value)
    }

    fun saveGateAuth(value: String) {
        saveToSecureStorage(KEY_GATE_AUTH, value)
    }

    fun saveGateSecret(value: String) {
        saveToSecureStorage(KEY_GATE_SECRET, value)
    }

    fun getGarageIp(): String? {
        return getFromSecureStorage(KEY_GARAGE_IP)
    }


    fun getGateIp(): String? {
        return getFromSecureStorage(KEY_GATE_IP)
    }

    fun getGarageAuth(): String? {
        return getFromSecureStorage(KEY_GARAGE_AUTH)
    }

    fun getGarageSecret(): String? {
        return getFromSecureStorage(KEY_GARAGE_SECRET)
    }

    fun getGateAuth(): String? {
        return getFromSecureStorage(KEY_GATE_AUTH)
    }

    fun getGateSecret(): String? {
        return getFromSecureStorage(KEY_GATE_SECRET)
    }

}