package chamilton0.remootioandroidws

import android.os.Bundle
import android.text.InputType
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(R.id.settings, SettingsFragment())
                .commit()
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            val garageApiAuthKeyPreference = findPreference<EditTextPreference>("garageApiAuthKey")
            val garageApiSecretKeyPreference =
                findPreference<EditTextPreference>("garageApiSecretKey")
            val gateApiAuthKeyPreference = findPreference<EditTextPreference>("gateApiAuthKey")
            val gateApiSecretKeyPreference = findPreference<EditTextPreference>("gateApiSecretKey")

            hideFieldData(garageApiAuthKeyPreference)
            hideFieldData(garageApiSecretKeyPreference)
            hideFieldData(gateApiAuthKeyPreference)
            hideFieldData(gateApiSecretKeyPreference)
        }

        fun hideFieldData(field: EditTextPreference?) {
            field?.setOnBindEditTextListener { editText ->
                editText.inputType =
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            field?.summaryProvider = PasswordSummaryProvider()
        }

        // Custom SummaryProvider to hide the password in the summary
        private class PasswordSummaryProvider : Preference.SummaryProvider<EditTextPreference> {

            override fun provideSummary(preference: EditTextPreference): CharSequence? {
                return if (preference.text.isNullOrEmpty()) {
                    ""
                } else {
                    "*".repeat(16)
                }
            }
        }
    }
}