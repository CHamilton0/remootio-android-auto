package chamilton0.remootioandroidws

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.util.Base64
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
        private val keystore by lazy { Keystore() }

        private val fieldAliases = mapOf(
            "garageApiAuthKey" to "garage_api_auth_key_alias",
            "garageApiSecretKey" to "gate_api_auth_key_alias",
            "gateApiAuthKey" to "gate_api_auth_key_alias",
            "gateApiSecretKey" to "gate_api_secret_key_alias"
        )


        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            fieldAliases.forEach { (fieldName, alias) ->
                val editTextPreference = findPreference<EditTextPreference>(fieldName)

                if (keystore.getKeystore().containsAlias(alias).not()) {
                    keystore.generateKey(alias)
                }

                hideFieldData(editTextPreference)

                editTextPreference?.setOnPreferenceChangeListener { _, newValue ->
                    val encryptedValue = keystore.encrypt(alias, newValue as String)
                    saveEncryptedValue(encryptedValue, alias)
                    true
                }
            }
        }

        private fun hideFieldData(field: EditTextPreference?) {
            field?.setOnBindEditTextListener { editText ->
                editText.inputType =
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            field?.summaryProvider = PasswordSummaryProvider()
        }

        private fun saveEncryptedValue(encryptedValue: ByteArray, alias: String) {
            // Save the encrypted value, for example, in SharedPreferences
            val sharedPreferences = requireActivity().getPreferences(Context.MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            editor.putString(alias, Base64.encodeToString(encryptedValue, Base64.DEFAULT))
            editor.apply()
        }

        // Custom SummaryProvider to hide the password in the summary
        private class PasswordSummaryProvider : Preference.SummaryProvider<EditTextPreference> {

            override fun provideSummary(preference: EditTextPreference): CharSequence {
                return if (preference.text.isNullOrEmpty()) {
                    ""
                } else {
                    "*".repeat(16)
                }
            }
        }
    }
}