package chamilton0.remootioandroidws.shared

import android.content.Context
import android.content.SharedPreferences;

class SavedData constructor(context: Context) {
    private val PREFERENCE_NAME = "MyAppSettings"
    private val KEY_SETTING_1 = "setting_1"
    private val KEY_SETTING_2 = "setting_2"
    private var preferences: SharedPreferences? = null
    init {
        preferences = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
    }

    fun saveSetting1(value: String?) {
        preferences!!.edit().putString(KEY_SETTING_1, value).apply()
    }

    fun getSetting1(): String? {
        return preferences!!.getString(KEY_SETTING_1, null)
    }

    fun saveSetting2(value: String?) {
        preferences!!.edit().putString(KEY_SETTING_2, value).apply()
    }

    fun getSetting2(): String? {
        return preferences!!.getString(KEY_SETTING_2, null)
    }

}