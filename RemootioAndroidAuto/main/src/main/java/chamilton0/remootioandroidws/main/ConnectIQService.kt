package chamilton0.remootioandroidws.main

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
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
import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.TimeUnit


class ConnectIQService : Service() {

    private var connectIQConnection: ConnectIQ? = null
    private var garminDevice: IQDevice? = null
    private var iqApp: IQApp? = null
    private var client: RemootioClient? = null
    private lateinit var settingHelper: SavedData

    private val queuedMessages: Queue<Map<String, String>> = LinkedList()

    // TODO: Simulator values
    // private val appId = ""
    // private val iqConnectType = IQConnectType.TETHERED

    // TODO: Real values
    private val appId = "92004c45c05a44ad975651b1e314b279"
    private val iqConnectType = IQConnectType.WIRELESS

    private val TAG = "ConnectIQService"
    private val handler = Handler(Looper.getMainLooper())

    private var isReinitializing = false

    override fun onCreate() {
        super.onCreate()
        settingHelper = SavedData(applicationContext)
        createNotificationChannel()
        startForegroundService()
        initializeConnectIQ()
    }

    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, "remootio_channel")
            .setContentTitle("Remootio Listening")
            .setContentText("Waiting for Garmin device input...")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_LOW).build()

        startForeground(1001, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "remootio_channel", "Remootio ConnectIQ Listener", NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun initializeConnectIQ() {
        try {
            connectIQConnection = ConnectIQ.getInstance(applicationContext, iqConnectType)
            connectIQConnection?.initialize(applicationContext, true, object : ConnectIQListener {
                override fun onSdkReady() {
                    Log.d(TAG, "ConnectIQ SDK Ready")
                    isReinitializing = false
                    val devices = connectIQConnection?.knownDevices
                    if (devices.isNullOrEmpty()) {
                        Log.w(TAG, "No known Garmin devices found")
                        reinitializeConnectIQWithDelay()
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
        connectIQConnection!!.registerForDeviceEvents(device, object : IQDeviceEventListener {
            override fun onDeviceStatusChanged(device: IQDevice, status: IQDeviceStatus) {
                when (status) {
                    IQDeviceStatus.CONNECTED -> {
                        Log.d(TAG, "Device connected: ${device.friendlyName}")
                        garminDevice = device
                        // Set the device status since this is not done properly
                        garminDevice!!.status = IQDeviceStatus.CONNECTED;
                        fetchApplicationInfoWhenReady()

                        // Send any queued messages that we haven't sent
                        queuedMessages.forEach { message ->
                            sendMessageToGarmin(message)
                        }
                    }

                    IQDeviceStatus.NOT_CONNECTED -> {
                        Log.d(
                            TAG, "Device disconnected: ${device.friendlyName}"
                        )
                        garminDevice!!.status = IQDeviceStatus.NOT_CONNECTED;
                    }

                    IQDeviceStatus.NOT_PAIRED -> {
                        Log.d(
                            TAG, "Device not paired: ${device.friendlyName}"
                        )
                        garminDevice!!.status = IQDeviceStatus.NOT_CONNECTED;
                    }

                    IQDeviceStatus.UNKNOWN -> {
                        Log.d(
                            TAG, "Device status unknown: ${device.friendlyName}"
                        )
                        garminDevice!!.status = IQDeviceStatus.NOT_CONNECTED;
                    }

                    else -> {
                        Log.d(TAG, "Unhandled device event: $status")
                        garminDevice!!.status = IQDeviceStatus.NOT_CONNECTED;
                    }
                }
            }
        });
    }

    private fun fetchApplicationInfoWhenReady() {
        garminDevice?.let { device ->
            try {
                connectIQConnection?.getApplicationInfo(
                    appId, device, object : IQApplicationInfoListener {
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
                    // Set the Remootio State Change Listener
                    remootioStateChangeListener = FrameStateChangeListener(
                        garminDevice, queuedMessages, ::sendMessageToGarmin
                    )
                    Log.d(TAG, "Registering for app events")
                    connectIQConnection?.registerForAppEvents(
                        device, app, object : IQApplicationEventListener {
                            override fun onMessageReceived(
                                device: IQDevice?,
                                app: IQApp?,
                                messageData: MutableList<Any>?,
                                status: IQMessageStatus?
                            ) {
                                Log.d(TAG, "App event status: $status")
                                if (status == IQMessageStatus.SUCCESS) {
                                    messageData?.forEach {
                                        Log.d(TAG, "Received: $it")
                                        if (it is Map<*, *>) {
                                            // Handle each received message
                                            handleGarminMessage(it)
                                        }
                                    }
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

    private fun handleGarminMessage(message: Map<*, *>) {
        // Handle the type of message
        val messageType = message["type"]
        // Door is either "GARAGE" or "GATE"
        val door = message["door"].toString()

        // TODO: Check if the door is already set and just do the action required
        if (messageType == "check") {
            // If checking, set the door then query it
            setDoor(door)
            queryDoor()
        } else if (messageType == "trigger") {
            // If triggering, set the door then trigger it
            setDoor(door)
            triggerDoor()
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
            Log.d(TAG, "Unregistering app events")
            garminDevice?.let { device ->
                iqApp?.let { app ->
                    connectIQConnection?.unregisterForApplicationEvents(device, app)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during unregistering app events: ${e.message}")
        }
    }

    fun shutdown() {
        try {
            Log.d(TAG, "Shutting down")
            unregisterFromAppEvents()
            connectIQConnection?.shutdown(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Error during shutdown: ${e.message}")
        }
    }

    override fun onDestroy() {
        disconnect()
        shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    class FrameStateChangeListener(
        private val garminDevice: IQDevice?,
        private val queuedMessages: Queue<Map<String, String>>,
        private val sendMessageToGarmin: (Map<String, String>) -> Unit
    ) : RemootioClient.StateChangeListener {

        override fun onFrameStateChanged(newState: String) {
            val formattedState = newState.replaceFirstChar { it.titlecase() }
            Log.d("Remootio", "Frame state changed to $formattedState")

            val stateMessage = mapOf("state" to formattedState)
            if (garminDevice?.status == IQDeviceStatus.CONNECTED) {
                sendMessageToGarmin(stateMessage)
            } else {
                queuedMessages.add(stateMessage)
            }
        }
    }

    class RemootioErrorListener : RemootioClient.ErrorListener {
        override fun onError(error: Error) {
            Log.e("Remootio", "Remootio Error: ${error.message}")
        }
    }

    private var remootioStateChangeListener: FrameStateChangeListener? = null;

    private val remootioErrorListener = RemootioErrorListener()


    private fun setDoor(door: String) {
        val (ip, auth, secret) = when (door) {
            "GARAGE" -> Triple(
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

            Toast.makeText(this, "Door setup incomplete for: $door", Toast.LENGTH_LONG).show()

            // TODO: Fix the below
//            if (garminDevice?.status == IQDeviceStatus.CONNECTED) {
//                val errorMessage = mapOf("error" to "$door not set up correctly")
//                sendMessageToGarmin(errorMessage)
//            }
            return
        }

        try {
            if (client != null) {
                client?.removeFrameStateChangeListener(remootioStateChangeListener!!)
                client?.removeErrorListener(remootioErrorListener)
                client?.disconnect()
            }
            client = RemootioClient(ip, auth, secret, false)
            client?.connectBlocking(5, TimeUnit.SECONDS)

            client?.addFrameStateChangeListener(remootioStateChangeListener!!)
            client?.addErrorListener(remootioErrorListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting door: ${e.message}")
        }
    }

    private fun sendMessageToGarmin(messages: Map<String, String>) {
        connectIQConnection!!.sendMessage(
            garminDevice, iqApp, messages, object : ConnectIQ.IQSendMessageListener {
                override fun onMessageStatus(
                    device: IQDevice?, app: IQApp?, status: IQMessageStatus?
                ) {
                    Log.d(TAG, "Send message status: $status")
                    if (status == IQMessageStatus.SUCCESS) {
                        Log.d(TAG, "Sent message: $messages")
                    } else {
                        Log.w(TAG, "Failed to send message, status: $status")
                    }
                }
            });
    }

    private fun queryDoor() {
        if (client == null) {
            return
        }
        client!!.sendQuery()
    }

    private fun triggerDoor() {
        if (client == null) {
            return
        }
        client!!.sendTriggerAction()
    }

    fun disconnect() {
        client?.disconnect()
    }
}
