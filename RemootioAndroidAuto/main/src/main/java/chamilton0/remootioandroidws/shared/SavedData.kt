package chamilton0.remootioandroidws.shared

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys


class SavedData(context: Context) {
    private val keyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val sharedPreferences = EncryptedSharedPreferences.create(
        "remootio_secret_preferences",
        keyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    private val preferenceEditor = sharedPreferences.edit()

    private val keyGarageIp = "garageIp"
    private val keyGateIp = "gateIp"
    private val keyGarageAuth = "garageApiAuthKey"
    private val keyGarageSecret = "garageApiSecretKey"
    private val keyGateAuth = "gateApiSecretKey"
    private val keyGateSecret = "gateApiSecretKey"

    fun saveGarageIp(value: String) {
        preferenceEditor.putString(keyGarageIp, value).apply()
    }

    fun saveGateIp(value: String) {
        preferenceEditor.putString(keyGateIp, value).apply()
    }

    fun saveGarageAuth(value: String) {
        preferenceEditor.putString(keyGarageAuth, value).apply()
    }

    fun saveGarageSecret(value: String) {
        preferenceEditor.putString(keyGarageSecret, value).apply()
    }

    fun saveGateAuth(value: String) {
        preferenceEditor.putString(keyGateAuth, value).apply()
    }

    fun saveGateSecret(value: String) {
        preferenceEditor.putString(keyGateSecret, value).apply()
    }

    fun getGarageIp(): String? {
        return sharedPreferences.getString(keyGarageIp, null)
    }


    fun getGateIp(): String? {
        return sharedPreferences.getString(keyGateIp, null)
    }

    fun getGarageAuth(): String? {
        return sharedPreferences.getString(keyGarageAuth, null)
    }

    fun getGarageSecret(): String? {
        return sharedPreferences.getString(keyGarageSecret, null)
    }

    fun getGateAuth(): String? {
        return sharedPreferences.getString(keyGateAuth, null)
    }

    fun getGateSecret(): String? {
        return sharedPreferences.getString(keyGateSecret, null)
    }

}