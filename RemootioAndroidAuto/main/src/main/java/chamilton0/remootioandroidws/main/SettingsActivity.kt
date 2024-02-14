package chamilton0.remootioandroidws.main

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.text.InputType
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import chamilton0.remootioandroidws.shared.Keystore
import chamilton0.remootioandroidws.shared.SavedData

class SettingsActivity : AppCompatActivity() {
    private lateinit var dataService: DataService
    private var isServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as DataService.LocalBinder
            dataService = binder.getService()
            isServiceBound = true

            supportFragmentManager.beginTransaction()
                .replace(R.id.settings, SettingsFragment(dataService))
                .commit()


        }

        override fun onServiceDisconnected(className: ComponentName) {
            isServiceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        val serviceIntent = Intent(this, DataService::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    class SettingsFragment(dataService: DataService) : PreferenceFragmentCompat() {
        val dataService: DataService

        init {
            this.dataService = dataService
        }

        private val keystore by lazy { Keystore() }

        private val fieldAliases = mapOf(
            "garageApiAuthKey" to "garage_api_auth_key_alias",
            "garageApiSecretKey" to "garage_api_secret_key_alias",
            "gateApiAuthKey" to "gate_api_auth_key_alias",
            "gateApiSecretKey" to "gate_api_secret_key_alias",
            "garageIp" to "garage_ip_alias",
            "gateIp" to "gate_ip_alias"
        )


        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(
                R.xml.root_preferences,
                rootKey
            )

            fieldAliases.forEach { (fieldName, alias) ->
                val editTextPreference = findPreference<EditTextPreference>(fieldName)

                if (keystore.getKeystore().containsAlias(alias).not()) {
                    keystore.generateKey(alias)
                }

                if (!fieldName.lowercase().contains("ip")) {
                    hideFieldData(editTextPreference)
                }

                editTextPreference?.setOnPreferenceChangeListener { _, newValue ->
                    // val encryptedValue = keystore.encrypt(alias, newValue as String)
                    saveEncryptedValue(newValue.toString(), alias)
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

        private fun saveEncryptedValue(value: String, alias: String) {
            dataService.setUserInput("test")

            val settingHelper = SavedData(requireActivity().applicationContext)
            if (alias == fieldAliases["garageApiAuthKey"]) {
                settingHelper.saveGarageAuth(value)
            } else if (alias == fieldAliases["garageApiSecretKey"]) {
                settingHelper.saveGarageSecret(value)
            } else if (alias == fieldAliases["gateApiAuthKey"]) {
                settingHelper.saveGateAuth(value)
            } else if (alias == fieldAliases["gateApiSecretKey"]) {
                settingHelper.saveGateSecret(value)
            } else if (alias == fieldAliases["garageIp"]) {
                settingHelper.saveGarageIp(value)
            } else if (alias == fieldAliases["gateIp"]) {
                settingHelper.saveGateIp(value)
            }
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