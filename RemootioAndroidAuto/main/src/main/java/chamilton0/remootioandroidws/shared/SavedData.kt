package chamilton0.remootioandroidws.shared

import android.content.Context
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager

class SavedData(context: Context) {
    private val PREFERENCE_NAME = "RemootioSettings"
    private val KEY_GARAGE_IP = "garageIp"
    private val KEY_GATE_IP = "gateIp"
    private val KEY_GARAGE_AUTH = "garageApiAuthKey"
    private val KEY_GARAGE_SECRET = "garageApiSecretKey"
    private val KEY_GATE_AUTH = "gateApiSecretKey"
    private val KEY_GATE_SECRET = "gateApiSecretKey"
    private var preferences: SharedPreferences? = null

    init {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        println("Printing entries")
        for (mutableEntry in sharedPreferences.all) {
            println(mutableEntry.key + " " + mutableEntry.value)
        }
        println("Done printing entries")
        preferences = sharedPreferences
    }

    fun saveGarageIp(value: String?) {
        preferences!!.edit().putString(KEY_GARAGE_IP, value).apply()
        preferences!!.edit().apply()
    }

    fun saveGateIp(value: String?) {
        preferences!!.edit().putString(KEY_GATE_IP, value).apply()
        preferences!!.edit().apply()
    }

    fun saveGarageAuth(value: String?) {
        preferences!!.edit().putString(KEY_GARAGE_AUTH, value).apply()
        preferences!!.edit().apply()
    }

    fun saveGarageSecret(value: String?) {
        preferences!!.edit().putString(KEY_GARAGE_SECRET, value).apply()
        preferences!!.edit().apply()
    }

    fun saveGateAuth(value: String?) {
        preferences!!.edit().putString(KEY_GATE_AUTH, value).apply()
        preferences!!.edit().apply()
    }

    fun saveGateSecret(value: String?) {
        preferences!!.edit().putString(KEY_GATE_SECRET, value).apply()
        preferences!!.edit().apply()
    }

    fun getGarageIp(): String? {
        return preferences!!.getString(KEY_GARAGE_IP, null)
    }


    fun getGateIp(): String? {
        return preferences!!.getString(KEY_GATE_IP, null)
    }

    fun getGarageAuth(): String? {
        return preferences!!.getString(KEY_GARAGE_AUTH, null)
    }

    fun getGarageSecret(): String? {
        return preferences!!.getString(KEY_GARAGE_SECRET, null)
    }

    fun getGateAuth(): String? {
        return preferences!!.getString(KEY_GATE_AUTH, null)
    }

    fun getGateSecret(): String? {
        return preferences!!.getString(KEY_GATE_SECRET, null)
    }

}