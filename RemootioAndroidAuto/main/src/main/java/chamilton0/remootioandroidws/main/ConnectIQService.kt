package chamilton0.remootioandroidws.main

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import chamilton0.remootioandroidws.shared.RemootioClient
import chamilton0.remootioandroidws.shared.SavedData
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.ConnectIQ.ConnectIQListener
import com.garmin.android.connectiq.ConnectIQ.IQApplicationEventListener
import com.garmin.android.connectiq.ConnectIQ.IQApplicationInfoListener
import com.garmin.android.connectiq.ConnectIQ.IQConnectType
import com.garmin.android.connectiq.ConnectIQ.IQDeviceEventListener
import com.garmin.android.connectiq.ConnectIQ.IQMessageStatus
import com.garmin.android.connectiq.ConnectIQ.IQSdkErrorStatus
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.IQDevice.IQDeviceStatus
import java.util.concurrent.TimeUnit


interface ConnectIQServiceCallback {
    fun onConnectIQServiceConnected(connectIQService: ConnectIQService)
}

class ConnectIQService : Service() {

    private var connectIQ: ConnectIQ? = null
    private var garminDevice: IQDevice? = null
    private var iqApp: IQApp? = null
    private var client: RemootioClient? = null
    private lateinit var settingHelper: SavedData
    // Leave this empty for running in simulator
    private val appId = ""
    // Use the real value for running on device
    // private val appId = "92004c45c05a44ad975651b1e314b279"
    private val TAG = "ConnectIQService"
    private val handler = Handler(Looper.getMainLooper())

    private val connectIQServiceCallback = ArrayList<ConnectIQServiceCallback>()
    private var isReinitializing = false

    fun addDataServiceCallback(callback: ConnectIQServiceCallback) {
        connectIQServiceCallback.add(callback)
    }

    fun removeDataServiceCallback(callback: ConnectIQServiceCallback) {
        connectIQServiceCallback.remove(callback)
    }

    private fun notifyConnectIQServiceConnected() {
        for (callback in connectIQServiceCallback) {
            callback.onConnectIQServiceConnected(this)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        settingHelper = SavedData(applicationContext)
        initializeConnectIQ()
    }

    private fun initializeConnectIQ() {
        try {
            connectIQ = ConnectIQ.getInstance(applicationContext, IQConnectType.TETHERED)
            connectIQ?.initialize(applicationContext, true, object : ConnectIQListener {
                override fun onSdkReady() {
                    Log.d(TAG, "ConnectIQ SDK Ready")
                    // No devices yet, let's register to wait for any known device to connect
                    isReinitializing = false
                    val devices = connectIQ?.knownDevices
                    if (devices.isNullOrEmpty()) {
                        Log.w(TAG, "No known Garmin devices found")
                    } else {
                        devices.forEach { device ->
                            println(device.friendlyName)
                            registerForDeviceConnection(device)
                        }
                    }
                }

                override fun onInitializeError(status: IQSdkErrorStatus?) {
                    Log.e(TAG, "ConnectIQ SDK Init Error: $status")
                    reinitializeConnectIQWithDelay()
                }

                override fun onSdkShutDown() {
                    Log.w(TAG, "ConnectIQ SDK Shut Down unexpectedly")
                    reinitializeConnectIQWithDelay()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Exception during SDK init: ${e.message}")
            reinitializeConnectIQWithDelay()
        }
    }
    
    private fun registerForDeviceConnection(device: IQDevice) {
        connectIQ!!.registerForDeviceEvents(device, object : IQDeviceEventListener {
            override fun onDeviceStatusChanged(device: IQDevice, status: IQDeviceStatus) {
                when (status) {
                    IQDeviceStatus.CONNECTED -> {
                        Log.d(TAG, "Device connected: ${device.friendlyName}")
                        garminDevice = device
                        fetchApplicationInfoWhenReady()
                    }

                    IQDeviceStatus.NOT_CONNECTED -> Log.d(
                        TAG,
                        "Device disconnected: ${device.friendlyName}"
                    )

                    IQDeviceStatus.NOT_PAIRED -> Log.d(
                        TAG,
                        "Device not paired: ${device.friendlyName}"
                    )

                    IQDeviceStatus.UNKNOWN -> Log.d(
                        TAG,
                        "Device status unknown: ${device.friendlyName}"
                    )

                    else -> Log.d(TAG, "Unhandled device event: $status")
                }
            }
        });
    }

    private fun fetchApplicationInfoWhenReady() {
        garminDevice?.let { device ->
            try {
                connectIQ?.getApplicationInfo(appId, device, object : IQApplicationInfoListener {
                    override fun onApplicationInfoReceived(app: IQApp?) {
                        Log.d(TAG, "Received app info")
                        iqApp = app
                        registerForAppEvents()
                    }

                    override fun onApplicationNotInstalled(appId: String?) {
                        Log.w(TAG, "App not installed on device")
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching app info: ${e.message}")
                handler.postDelayed({ fetchApplicationInfoWhenReady() }, 2000)
            }
        }
    }

    private fun registerForAppEvents() {
        garminDevice?.let { device ->
            iqApp?.let { app ->
                try {
                    Log.d(TAG, "Registering for app events")
                    connectIQ?.registerForAppEvents(
                        device,
                        app,
                        object : IQApplicationEventListener {
                            override fun onMessageReceived(
                                device: IQDevice?,
                                app: IQApp?,
                                messageData: MutableList<Any>?,
                                status: IQMessageStatus?
                            ) {
                                Log.d(TAG, "App event status: $status")
                                if (status == IQMessageStatus.SUCCESS) {
                                    messageData?.forEach { Log.d(TAG, "Received: $it") }
                                    // setDoor("Garage Door")
                                } else {
                                    Log.w(TAG, "Failed to receive message, status: $status")
                                }
                            }
                        })
                } catch (e: Exception) {
                    Log.e(TAG, "Error registering for app events: ${e.message}")
                    reinitializeConnectIQWithDelay()
                }
            }
        }
    }

    private fun reinitializeConnectIQWithDelay(delayMillis: Long = 3000) {
        if (!isReinitializing) {
            isReinitializing = true
            Log.w(TAG, "Reinitializing ConnectIQ SDK in ${delayMillis / 1000}s...")
            handler.postDelayed({
                shutdown()
                initializeConnectIQ()
            }, delayMillis)
        }
    }

    fun unregisterFromAppEvents() {
        try {
            garminDevice?.let { device ->
                iqApp?.let { app ->
                    connectIQ?.unregisterForApplicationEvents(device, app)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during unregistering app events: ${e.message}")
        }
    }

    fun shutdown() {
        try {
            unregisterFromAppEvents()
            connectIQ?.shutdown(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Error during shutdown: ${e.message}")
        }
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun setDoor(door: String) {
        val (ip, auth, secret) = when (door) {
            "Garage Door" -> Triple(
                settingHelper.getGarageIp(),
                settingHelper.getGarageAuth(),
                settingHelper.getGarageSecret()
            )

            else -> Triple(
                settingHelper.getGateIp(),
                settingHelper.getGateAuth(),
                settingHelper.getGateSecret()
            )
        }

        if (ip.isNullOrEmpty() || auth.isNullOrEmpty() || secret.isNullOrEmpty()) {
            Log.w(TAG, "Door setup incomplete for: $door")
            return
        }

        try {
            client?.disconnect()
            client = RemootioClient(ip, auth, secret, true)
            client?.connectBlocking(10, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting door: ${e.message}")
        }
    }

    private fun queryDoor() {
        client?.sendQuery()
    }

    private fun triggerDoor() {
        client?.sendTriggerAction()
    }

    fun disconnect() {
        client?.disconnect()
    }
}
