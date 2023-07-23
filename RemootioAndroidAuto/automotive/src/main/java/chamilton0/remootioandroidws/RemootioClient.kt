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
import java.security.SecureRandom
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


    /**
     * Decrypts an AES cipher text into a ByteArray
     */
    fun decryptAES(ciphertext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val secretKey = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val ivParameterSpec = IvParameterSpec(iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec)
        return cipher.doFinal(ciphertext)
    }

    fun decryptEncryptedFrame(frame: JSONObject): JSONObject {
        // Verify the MAC for the frame
        val hexKey = apiAuthKey.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val macKey = SecretKeySpec(hexKey, "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(macKey)
        println("mac")

        val jsonDecode = mac.doFinal(JSONObject(frame.get("data").toString()).toString().replace("\\/", "/").toByteArray(StandardCharsets.UTF_8))
        val jsonDecoded = StringEscapeUtils.unescapeJson(JSONObject(frame.get("data").toString()).toString())

        val macBytes = mac.doFinal(jsonDecoded.toString().toByteArray(StandardCharsets.UTF_8))
        val base64mac = Base64.getEncoder().encodeToString(macBytes)

        // Check if the calculated MAC matches the one sent by the API
        if (base64mac != frame.get("mac")) {
            // If the MAC doesn't match - return
            println("Decryption error: calculated MAC $base64mac does not match the MAC from the API $frame.get(\"mac\")")
        }

        val frameData = frame.get("data").toString()
        val data = JSONObject(frameData)

        val payload = Base64.getDecoder().decode(data.get("payload").toString())
        val iv = Base64.getDecoder().decode(data.get("iv").toString())

        println("use secret key")
        val secretHexKey = apiSecretKey.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        println("decrypting payload")
        val decryptedPayloadByteArray = decryptAES(payload, secretHexKey, iv)
        val decryptedPayload = String(decryptedPayloadByteArray, Charsets.ISO_8859_1)

        val payloadJSON = JSONObject(decryptedPayload)
        println("payload JSON")
        println(payloadJSON.toString())
        return payloadJSON
    }

    fun encryptAES(payload: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val secretKey = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val ivParameterSpec = IvParameterSpec(iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec)
        return cipher.doFinal(payload)
    }

    fun sendEncryptedFrame(unencryptedPayload: JSONObject) {
        // STEP 0 - Get the relevant keys used for encryption
        val currentlyUsedSecretKeyByteArray: ByteArray = Base64.getDecoder().decode(apiSessionKey)
            ?: return

        // The auth key is used for calculating the MAC (Message Authentication Code), which is a HMAC-SHA256
        val apiAuthKeyByteArray: ByteArray = Base64.getDecoder().decode(apiAuthKey)

        // STEP 1 encrypt the payload
        // Generate random IV
        val ivByteArray = ByteArray(16)
        SecureRandom().nextBytes(ivByteArray)

        // Convert the unencrypted payload to ByteArray
        val unencryptedPayloadByteArray = unencryptedPayload.toString().toByteArray(Charsets.UTF_8)

        // Do the encryption
        val encryptedPayloadByteArray = encryptAES(
            unencryptedPayloadByteArray,
            currentlyUsedSecretKeyByteArray,
            ivByteArray
        )

        // Step 2 create the {data:...} object of the encrypted frame used for HMAC calculation
        val toHMACObj = JSONObject().apply {
            put("iv", Base64.getEncoder().encodeToString(ivByteArray))
            put("payload", Base64.getEncoder().encodeToString(encryptedPayloadByteArray))
        }

        // STEP 3 calculate the HMAC-SHA256 of JSON.stringify(frame.data)
        val toHMAC = toHMACObj.toString()
        val mac = calculateHmacSha256(toHMAC, apiAuthKeyByteArray)
        val base64mac = Base64.getEncoder().encodeToString(mac)

        // STEP 4 construct and return the full encrypted frame
        val data = JSONObject().apply {
            put("type", "ENCRYPTED")
            put("data", toHMACObj)
            put("mac", base64mac)
        }
        val frame = TextFrame()
        frame.setPayload(ByteBuffer.wrap(data.toString().toByteArray()))
        sendFrame(frame)
        return
    }

    fun sendQuery () {
        val payload = JSONObject("""
            {
                "action": {
                    "type": "QUERY",
                    "id": ${(((lastActionId?.toInt() ?: 0) + 1) % 0x7FFFFFFF)}
                }
            }
        """.trimIndent())

        sendEncryptedFrame(payload)

    }

    fun sendTriggerAction (){
        if (apiSessionKey == null){ //if we are not authenticated, this message is invalid
            println("This action requires authentication, authenticate session first")
            return
        }
        val payload = JSONObject("""
            {
                "action": {
                    "type": "TRIGGER",
                    "id": ${(((lastActionId?.toInt() ?: 0) + 1) % 0x7FFFFFFF)}
                }
            }
        """.trimIndent())
        sendEncryptedFrame(payload)
    }

    override fun onMessage(message: String?) {
        println("received message $message")

        // TODO: Only do all this if we aren't already authenticated
        if (message == null) return
        val frame = JSONObject(message)

        println("test")

        if (frame.get("type") == "ENCRYPTED") {
            try {
                val decryptedFrame = decryptEncryptedFrame(frame)
                val challenge = JSONObject(decryptedFrame.get("challenge").toString())
                if (decryptedFrame.has("challenge") && challenge.has("sessionKey") && challenge.has("initialActionId")) {
                    lastActionId = challenge.get("initialActionId").toString().toInt()
                    apiSessionKey =  challenge.get("sessionKey").toString()
                    println("Authentication challenge received, setting encryption key (session key) to " + apiSessionKey)

                    // Send the QUERY action to finish auth
                    sendQuery()
                }
            } catch (error: Error) {
                println(error)
            }
        }

        close()
    }

    override fun onMessage(bytes: ByteBuffer?) {
        println("received bytebuffer")
    }

    override fun onError(ex: Exception?) {
        println("error $ex")
    }
}
