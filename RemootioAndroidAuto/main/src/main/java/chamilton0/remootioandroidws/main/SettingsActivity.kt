package chamilton0.remootioandroidws.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.InputType
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import chamilton0.remootioandroidws.R
import chamilton0.remootioandroidws.shared.SavedData
import org.json.JSONObject
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

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

    class SettingsFragment(private val dataService: DataService?) : PreferenceFragmentCompat() {
        constructor() : this(null)

        private val fieldAliases = mapOf(
            "garageApiAuthKey" to "garage_api_auth_key_alias",
            "garageApiSecretKey" to "garage_api_secret_key_alias",
            "gateApiAuthKey" to "gate_api_auth_key_alias",
            "gateApiSecretKey" to "gate_api_secret_key_alias",
            "garageIp" to "garage_ip_alias",
            "gateIp" to "gate_ip_alias"
        )

        private fun fetchPublicIp() {
            // Use AsyncTask or another threading mechanism for network calls
            FetchIpTask().execute("https://api.ipify.org?format=json")
        }

        private inner class FetchIpTask : AsyncTask<String, Void, String?>() {
            override fun doInBackground(vararg params: String?): String? {
                val urlString = params[0] ?: return null
                var result: String? = null

                try {
                    val url = URL(urlString)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 10000 // 10 seconds timeout
                    connection.readTimeout = 10000

                    val reader = InputStreamReader(connection.inputStream)
                    val stringBuilder = StringBuilder()
                    var ch: Int
                    while (reader.read().also { ch = it } != -1) {
                        stringBuilder.append(ch.toChar())
                    }

                    result = stringBuilder.toString() // The response will be a JSON string
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                return result
            }

            override fun onPostExecute(result: String?) {
                super.onPostExecute(result)

                if (result != null) {
                    try {
                        // Assuming the API returns JSON in the format: {"ip": "xxx.xxx.xxx.xxx"}
                        val json = JSONObject(result)
                        val ipAddress = json.getString("ip")

                        // TODO: Do something with the IP Address

                        // Show the IP in a Toast for user feedback
                        Toast.makeText(requireContext(), "Public IP: $ipAddress", Toast.LENGTH_LONG)
                            .show()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(requireContext(), "Failed to fetch IP", Toast.LENGTH_SHORT)
                            .show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Failed to fetch IP", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }

        private fun setupFetchIpButton() {
            val fetchIpPref = findPreference<Preference>("fetch_ip_button")
            fetchIpPref?.setOnPreferenceClickListener {
                fetchPublicIp() // Call the function to fetch IP
                true
            }
        }


        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(
                R.xml.root_preferences,
                rootKey
            )

            val wifiPreference = findPreference<ListPreference>("selectedNetwork")
            wifiPreference?.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()

            setupFetchIpButton() // Set up button listener to fetch IP

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

            return when (alias) {
                fieldAliases["garageApiAuthKey"],
                fieldAliases["garageApiSecretKey"],
                fieldAliases["gateApiAuthKey"],
                fieldAliases["gateApiSecretKey"] -> apiKeyPattern.matches(value)

                fieldAliases["garageIp"],
                fieldAliases["gateIp"] -> ipPattern.matches(value)

                else -> false
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