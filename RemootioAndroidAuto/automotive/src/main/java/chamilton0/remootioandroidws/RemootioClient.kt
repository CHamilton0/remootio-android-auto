package chamilton0.remootioandroidws

import android.view.Choreographer.FrameData
import java.net.URI
import org.java_websocket.client.WebSocketClient
import org.java_websocket.framing.BinaryFrame
import org.java_websocket.framing.DataFrame
import org.java_websocket.framing.TextFrame
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONException
import org.json.JSONObject
import java.lang.Exception
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.InvalidKeyException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.MacSpi
import javax.crypto.spec.SecretKeySpec
import org.apache.commons.text.StringEscapeUtils

class RemootioClient(
    private val deviceHost: URI, // Must include the full URI with port number
    private val apiAuthKey: String,
    private val apiSecretKey: String,
) : WebSocketClient(URI("$deviceHost/")) {

    private val hexStringRegex = "[0-9A-Fa-f]{64}".toRegex()

    init {
        if (!hexStringRegex.matches(apiAuthKey)) {
            throw Error("auth key is not hex string")
        }
        if (!hexStringRegex.matches(apiSecretKey)) {
            throw Error("secret key is not hex string")
        }
    }

    private lateinit var websocketClient: WebSocketClient
    private var apiSessionKey: String?
        get() {
            return apiSessionKey
        }
        set(value) {
            apiSessionKey = value
        }
    private var lastActionId: Number?
        get() {
            return lastActionId
        }
        set(value) {
            lastActionId = value
        }
    /*private val autoReconnect: boolean
    private val sendPingMessageEveryXMs: number
    // private val sendPingMessageIntervalHandle?: ReturnType<typeof setInterval>
    private val pingReplyTimeoutXMs: number
    // private val pingReplyTimeoutHandle?: ReturnType<typeof setTimeout>;
    private val waitingForAuthenticationQueryActionResponse?: boolean*/

    /*public fun connect(autoReconnect: boolean) {
        this.apiSessionKey = null
        this.websocketClient = this.connect()
    }*/

    override fun onOpen(handshakedata: ServerHandshake?) {
        println("new connection opened")

        // When we open a connection, we then need to send an AUTH frame and authenticate
        val data = "{\"type\":\"AUTH\"}".toByteArray()
        val frame = TextFrame()
        frame.setPayload(ByteBuffer.wrap(data))
        println(data)
        println(ByteBuffer.wrap(data))
        sendFrame(frame)
    }

    override fun onClose(code: Int, reason: String?, remote: Boolean) {
        println("closed with exit code: $code reason: $reason")
    }

    fun calculateHmacSha256(jsonString: String, key: ByteArray): ByteArray? {
        return try {
            val hmacSha256 = Mac.getInstance("HmacSHA256")
            val secretKey = SecretKeySpec(key, "HmacSHA256")
            hmacSha256.init(secretKey)
            hmacSha256.doFinal(jsonString.toByteArray())
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
            null
        } catch (e: InvalidKeyException) {
            e.printStackTrace()
            null
        }
    }

    fun parseHexToByteArray(hexString: String): ByteArray {
        val byteArray = ByteArray(hexString.length / 2)
        var index = 0
        while (index < hexString.length) {
            val byteValue = hexString.substring(index, index + 2).toInt(16).toByte()
            byteArray[index / 2] = byteValue
            index += 2
        }
        return byteArray
    }


    override fun onMessage(message: String?) {
        println("received message $message")

        if (message == null) return
        val frame = JSONObject(message)
        // Step 1 verify MAC
        // It is a HMAC-SHA256 over the JSON.stringify(data)
        val hexKey = apiAuthKey.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val macKey = SecretKeySpec(hexKey, "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(macKey)
        val macBytes = mac.doFinal(
            JSONObject(
                StringEscapeUtils.unescapeJson(
                    frame.get("data").toString()
                )
            ).toString().replace("\\/", "/").toByteArray(StandardCharsets.UTF_8)
        )
        val base64mac = Base64.getEncoder().encodeToString(macBytes)

        // Check if the calculated MAC matches the one sent by the API
        var macMatches = true
        if (base64mac != frame.get("mac")) {
            // If the MAC doesn't match - return
            println("Decryption error: calculated MAC $base64mac does not match the MAC from the API $frame.get(\"mac\")")
            macMatches = false
        }
        close()
    }

    fun String.hexStringToByteArray(): ByteArray {
        val hexChars = toCharArray()
        val byteArr = ByteArray(length / 2)

        for (i in byteArr.indices) {
            val index = i * 2
            val byteStr = "" + hexChars[index] + hexChars[index + 1]
            byteArr[i] = byteStr.toInt(16).toByte()
        }

        return byteArr
    }

    fun String.base64StringToByteArray(): ByteArray = Base64.getDecoder().decode(this)

    override fun onMessage(bytes: ByteBuffer?) {
        println("received bytebuffer")
    }

    override fun onError(ex: Exception?) {
        println("error $ex")
    }
}
