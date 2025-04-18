package chamilton0.remootioandroidws.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.InputType
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import chamilton0.remootioandroidws.R
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

    @SuppressLint("SuspiciousIndentation")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        val serviceIntent = Intent(this, DataService::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = mutableListOf<String>()

            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)

            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }

            if (permissions.isNotEmpty()) {
                requestPermissions(permissions.toTypedArray(), 101)
                return
            }
        }

        val connectIQServiceIntent = Intent(this, ConnectIQService::class.java)
        startService(connectIQServiceIntent)
    }

    class SettingsFragment(dataService: DataService?) : PreferenceFragmentCompat() {
        constructor() : this(null)

        private val dataService: DataService?

        init {
            this.dataService = dataService
        }

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

                if (!fieldName.lowercase().contains("ip")) {
                    hideFieldData(editTextPreference)
                }

                editTextPreference?.setOnPreferenceChangeListener { _, newValue ->
                    if (!isValidValue(newValue.toString(), alias)) {
                        showToast("Invalid value entered")
                        return@setOnPreferenceChangeListener false
                    }

                    saveEncryptedValue(newValue.toString(), alias)
                    true
                }

            }
        }

        private fun isValidValue(value: String, alias: String): Boolean {
            val ipPattern = Regex("^ws://(?:[0-9]{1,3}\\.){3}[0-9]{1,3}:[0-9]{1,5}\$")
            val apiKeyPattern = Regex("^[a-zA-Z0-9]{64}\$")

            when (alias) {
                fieldAliases["garageApiAuthKey"],
                fieldAliases["garageApiSecretKey"],
                fieldAliases["gateApiAuthKey"],
                fieldAliases["gateApiSecretKey"] -> return apiKeyPattern.matches(value)

                fieldAliases["garageIp"],
                fieldAliases["gateIp"] -> return ipPattern.matches(value)

                else -> return false
            }
        }

        private fun showToast(message: String) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }

        private fun hideFieldData(field: EditTextPreference?) {
            field?.setOnBindEditTextListener { editText ->
                editText.inputType =
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            field?.summaryProvider = PasswordSummaryProvider()
        }

        private fun saveEncryptedValue(value: String, alias: String) {
            val settingHelper = SavedData(requireActivity().applicationContext)
            when (alias) {
                fieldAliases["garageApiAuthKey"] -> settingHelper.saveGarageAuth(value)
                fieldAliases["garageApiSecretKey"] -> settingHelper.saveGarageSecret(value)
                fieldAliases["gateApiAuthKey"] -> settingHelper.saveGateAuth(value)
                fieldAliases["gateApiSecretKey"] -> settingHelper.saveGateSecret(value)
                fieldAliases["garageIp"] -> settingHelper.saveGarageIp(value)
                fieldAliases["gateIp"] -> settingHelper.saveGateIp(value)
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