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


class ConnectIQService : Service() {

    private var connectIQConnection: ConnectIQ? = null
    private var garminDevice: IQDevice? = null
    private var iqApp: IQApp? = null
    private var client: RemootioClient? = null
    private lateinit var settingHelper: SavedData
    private var currentDoor: String? = null

    private val queuedMessages: Queue<Map<String, String>> = LinkedList()

    // TODO: Simulator values
//    private val appId = ""
//    private val iqConnectType = IQConnectType.TETHERED

    // TODO: Real values
    private val appId = "92004c45c05a44ad975651b1e314b279"
    private val iqConnectType = IQConnectType.WIRELESS

    private val tag = "ConnectIQService"
    private val handler = Handler(Looper.getMainLooper())

    private var isReinitializing = false

    override fun onCreate() {
        super.onCreate()

        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            Log.e(tag, "Uncaught exception: ${throwable.message}")
            disconnect()
            shutdown()
            throw throwable
        }

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
                    Log.d(tag, "ConnectIQ SDK Ready")
                    isReinitializing = false
                    val devices = connectIQConnection?.knownDevices
                    if (devices.isNullOrEmpty()) {
                        Log.w(tag, "No known Garmin devices found")
                        reinitializeConnectIQWithDelay()
                    } else {
                        devices.forEach { device ->
                            println(device.friendlyName)
                            registerForDeviceConnection(device)
                        }
                    }
                }

                override fun onInitializeError(status: IQSdkErrorStatus?) {
                    Log.e(tag, "ConnectIQ SDK Init Error: $status")
                    reinitializeConnectIQWithDelay()
                }

                override fun onSdkShutDown() {
                    Log.w(tag, "ConnectIQ SDK Shut Down unexpectedly")
                    reinitializeConnectIQWithDelay()
                }
            })
        } catch (e: Exception) {
            Log.e(tag, "Exception during SDK init: ${e.message}")
            reinitializeConnectIQWithDelay()
        }
    }

    private fun registerForDeviceConnection(device: IQDevice) {
        connectIQConnection!!.registerForDeviceEvents(device, object : IQDeviceEventListener {
            override fun onDeviceStatusChanged(device: IQDevice, status: IQDeviceStatus) {
                when (status) {
                    IQDeviceStatus.CONNECTED -> {
                        Log.d(tag, "Device connected: ${device.friendlyName}")
                        garminDevice = device
                        // Set the device status since this is not done properly
                        garminDevice!!.status = IQDeviceStatus.CONNECTED
                        fetchApplicationInfoWhenReady()

                        // Send any queued messages that we haven't sent
                        queuedMessages.forEach { message ->
                            sendMessageToGarmin(message)
                        }
                    }

                    IQDeviceStatus.NOT_CONNECTED -> {
                        Log.d(
                            tag, "Device disconnected: ${device.friendlyName}"
                        )
                        garminDevice!!.status = IQDeviceStatus.NOT_CONNECTED
                    }

                    IQDeviceStatus.NOT_PAIRED -> {
                        Log.d(
                            tag, "Device not paired: ${device.friendlyName}"
                        )
                        garminDevice!!.status = IQDeviceStatus.NOT_CONNECTED
                    }

                    IQDeviceStatus.UNKNOWN -> {
                        Log.d(
                            tag, "Device status unknown: ${device.friendlyName}"
                        )
                        garminDevice!!.status = IQDeviceStatus.NOT_CONNECTED
                    }

                    else -> {
                        Log.d(tag, "Unhandled device event: $status")
                        garminDevice!!.status = IQDeviceStatus.NOT_CONNECTED
                    }
                }
            }
        })
    }

    private fun fetchApplicationInfoWhenReady() {
        garminDevice?.let { device ->
            try {
                connectIQConnection?.getApplicationInfo(
                    appId, device, object : IQApplicationInfoListener {
                        override fun onApplicationInfoReceived(app: IQApp?) {
                            Log.d(tag, "Received app info")
                            iqApp = app
                            registerForAppEvents()
                        }

                        override fun onApplicationNotInstalled(appId: String?) {
                            Log.w(tag, "App not installed on device")
                        }
                    })
            } catch (e: Exception) {
                Log.e(tag, "Error fetching app info: ${e.message}")
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
                    Log.d(tag, "Registering for app events")
                    connectIQConnection?.registerForAppEvents(
                        device, app, object : IQApplicationEventListener {
                            override fun onMessageReceived(
                                device: IQDevice?,
                                app: IQApp?,
                                messageData: MutableList<Any>?,
                                status: IQMessageStatus?
                            ) {
                                Log.d(tag, "App event status: $status")
                                if (status == IQMessageStatus.SUCCESS) {
                                    messageData?.forEach {
                                        Log.d(tag, "Received: $it")
                                        if (it is Map<*, *>) {
                                            // Handle each received message
                                            handleGarminMessage(it)
                                        }
                                    }
                                } else {
                                    Log.w(tag, "Failed to receive message, status: $status")
                                }
                            }
                        })
                } catch (e: Exception) {
                    Log.e(tag, "Error registering for app events: ${e.message}")
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

        if (messageType == "check") {
            // If checking, set the door then query it
            setDoor(door)
            queryDoor()
        } else if (messageType == "trigger") {
            // If triggering, set the door then trigger it
            if (door == currentDoor && client?.isOpen == true) {
                triggerDoor()
            } else {
                setDoor(door)
                pendingTrigger = true
            }
        } else if (messageType == "disconnect") {
            disconnect()
        }
    }

    private fun reinitializeConnectIQWithDelay(delayMillis: Long = 3000) {
        if (!isReinitializing) {
            isReinitializing = true
            Log.w(tag, "Reinitializing ConnectIQ SDK in ${delayMillis / 1000}s...")
            handler.postDelayed({
                shutdown()
                initializeConnectIQ()
            }, delayMillis)
        }
    }

    private fun unregisterFromAppEvents() {
        try {
            Log.d(tag, "Unregistering app events")
            garminDevice?.let { device ->
                iqApp?.let { app ->
                    connectIQConnection?.unregisterForApplicationEvents(device, app)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error during unregistering app events: ${e.message}")
        }
    }

    private fun shutdown() {
        try {
            Log.d(tag, "Shutting down")
            unregisterFromAppEvents()
            connectIQConnection?.shutdown(applicationContext)
        } catch (e: Exception) {
            Log.e(tag, "Error during shutdown: ${e.message}")
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

    class RemootioErrorListener(
        private val sendMessageToGarmin: (Map<String, String>) -> Unit
    ) : RemootioClient.ErrorListener {
        override fun onError(error: Error) {
            val errorMessage = mapOf("error" to (error.message ?: "Unknown Error"))
            sendMessageToGarmin(errorMessage)
            Log.e("Remootio", "Remootio Error: ${error.message}")
        }
    }

    private var remootioStateChangeListener: FrameStateChangeListener? = null

    private val remootioErrorListener = RemootioErrorListener(::sendMessageToGarmin)

    private var pendingTrigger: Boolean = false
    private val remootioAuthListener = object : RemootioClient.AuthListener {
        override fun onAuthenticated(authenticated: Boolean) {
            if (authenticated) {
                Log.d(tag, "Remootio authenticated")

                // Safe to trigger now
                if (pendingTrigger) {
                    pendingTrigger = false
                    triggerDoor()
                }
            }
        }
    }

    private fun setDoor(door: String) {
        if (door == currentDoor && client?.isOpen == true) {
            Log.d(tag, "Already connected to door: $door")
            return
        }

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
            Log.w(tag, "Door setup incomplete for: $door")

            Toast.makeText(this, "Door setup incomplete for: $door", Toast.LENGTH_LONG).show()

            if (garminDevice?.status == IQDeviceStatus.CONNECTED) {
                val errorMessage = mapOf("error" to "$door not set up correctly")
                sendMessageToGarmin(errorMessage)
            }
            return
        }

        currentDoor = door

        try {
            if (client != null) {
                disconnect()
            }
            val connectionTimeoutMs = 5000L
            client = RemootioClient(ip, auth, secret, connectionTimeoutMs = connectionTimeoutMs)
            client?.addAuthListener(remootioAuthListener)
            client?.connectSafe()

            remootioStateChangeListener?.let { client?.addFrameStateChangeListener(it) }
            client?.addErrorListener(remootioErrorListener)
        } catch (e: Exception) {
            Log.e(tag, "Error setting door: ${e.message}")
        }
    }

    private fun sendMessageToGarmin(messages: Map<String, String>) {
        connectIQConnection!!.sendMessage(
            garminDevice, iqApp, messages, object : ConnectIQ.IQSendMessageListener {
                override fun onMessageStatus(
                    device: IQDevice?, app: IQApp?, status: IQMessageStatus?
                ) {
                    Log.d(tag, "Send message status: $status")
                    if (status == IQMessageStatus.SUCCESS) {
                        Log.d(tag, "Sent message: $messages")
                    } else {
                        Log.w(tag, "Failed to send message, status: $status")
                    }
                }
            })
    }

    private fun queryDoor() {
        if (client == null) {
            return
        }
        client!!.sendQuery()
    }

    private fun triggerDoor() {
        pendingTrigger = false
        if (client == null) {
            return
        }
        client!!.sendTriggerAction()
    }

    private fun disconnect() {
        client?.removeFrameStateChangeListener(remootioStateChangeListener!!)
        client?.removeErrorListener(remootioErrorListener)
        client?.removeAuthListener(remootioAuthListener)
        client?.disconnect()
        client = null
    }
}
