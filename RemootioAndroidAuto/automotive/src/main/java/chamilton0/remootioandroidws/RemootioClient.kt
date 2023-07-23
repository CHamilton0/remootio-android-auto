package chamilton0.remootioandroidws

import java.net.URI
import org.java_websocket.client.WebSocketClient
import org.java_websocket.framing.TextFrame
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.lang.Exception
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.apache.commons.text.StringEscapeUtils
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec

class RemootioClient(
    private val deviceHost: URI, // Must include the full URI with port number
    private val apiAuthKey: String,
    private val apiSecretKey: String,
) : WebSocketClient(URI("$deviceHost/")) {

    // Validate the API keys are hex strings
    private val hexStringRegex = "[0-9A-Fa-f]{64}".toRegex()
    init {
        if (!hexStringRegex.matches(apiAuthKey)) {
            throw Error("auth key is not hex string")
        }
        if (!hexStringRegex.matches(apiSecretKey)) {
            throw Error("secret key is not hex string")
        }
    }

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
        sendFrame(frame)
    }

    override fun onClose(code: Int, reason: String?, remote: Boolean) {
        println("closed with exit code: $code reason: $reason")
    }

    /**
     * Calculate the MAC for a JSON string as a ByteArray
     */
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


    fun decryptAES(ciphertext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val secretKey = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val ivParameterSpec = IvParameterSpec(iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec)
        return cipher.doFinal(ciphertext)
    }

    override fun onMessage(message: String?) {
        println("received message $message")

        // TODO: Only do all this if we aren't already authenticated
        if (message == null) return
        val frame = JSONObject(message)

        // Verify the MAC for the frame
        val hexKey = apiAuthKey.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val macKey = SecretKeySpec(hexKey, "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(macKey)
        val macBytes = mac.doFinal(
            JSONObject(
                StringEscapeUtils.unescapeJson(
                    frame.get("data").toString()
                )
            ).toString().toByteArray(StandardCharsets.UTF_8) // TODO: Test this
        )
        val base64mac = Base64.getEncoder().encodeToString(macBytes)

        // Check if the calculated MAC matches the one sent by the API
        var macMatches = true
        if (base64mac != frame.get("mac")) {
            // If the MAC doesn't match - return
            println("Decryption error: calculated MAC $base64mac does not match the MAC from the API $frame.get(\"mac\")")
            macMatches = false
        }

        val frameData = frame.get("data").toString()
        val data = JSONObject(frameData)

        val payload = Base64.getDecoder().decode(data.get("payload").toString())
        val iv = Base64.getDecoder().decode(data.get("iv").toString())

        val decryptedPayloadByteArray = decryptAES(payload, hexKey, iv)
        val decryptedPayload = String(decryptedPayloadByteArray, Charsets.ISO_8859_1)

        val payloadJSON = JSONObject(decryptedPayload)


        close()
    }

    override fun onMessage(bytes: ByteBuffer?) {
        println("received bytebuffer")
    }

    override fun onError(ex: Exception?) {
        println("error $ex")
    }
}
