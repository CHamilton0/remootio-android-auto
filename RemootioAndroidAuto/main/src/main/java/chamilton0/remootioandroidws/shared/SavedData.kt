package chamilton0.remootioandroidws.shared

import android.content.Context
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager

class SavedData(context: Context) {
    private val PREFERENCE_NAME = "RemootioSettings"
    private val KEY_GARAGE_IP = "garage_ip"
    private val KEY_GATE_IP = "gate_ip"
    private val KEY_GARAGE_AUTH = "garage_auth"
    private val KEY_GARAGE_SECRET = "garage_secret"
    private val KEY_GATE_AUTH = "gate_auth"
    private val KEY_GATE_SECRET = "gate_secret"
    private var preferences: SharedPreferences? = null

    init {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        for (mutableEntry in sharedPreferences.all) {
            println(mutableEntry.key + " " + mutableEntry.value)
        }
        preferences = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
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
        return preferences!!.getString(KEY_GATE_SECRET, null)
    }

    fun getGateAuth(): String? {
        return preferences!!.getString(KEY_GATE_AUTH, null)
    }

    fun getGateSecret(): String? {
        return preferences!!.getString(KEY_GATE_SECRET, null)
    }

}