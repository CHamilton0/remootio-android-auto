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
import kotlin.concurrent.timer

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

    private var apiSessionKey: String? = null
    private var lastActionId: Number = 0
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
        var hexKey = apiAuthKey.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        val macKey = SecretKeySpec(hexKey, "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(macKey)

        val macBytes = mac.doFinal(
            StringEscapeUtils.unescapeJson(
                JSONObject(
                    frame.get("data").toString()
                ).toString()
            ).toByteArray(StandardCharsets.UTF_8)
        )
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
        var secretHexKey = apiSecretKey.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        if (apiSessionKey != null) {
            secretHexKey = Base64.getDecoder().decode(apiSessionKey)
        }

        println("decrypting payload")
        val decryptedPayloadByteArray = decryptAES(payload, secretHexKey, iv)
        val decryptedPayload = String(decryptedPayloadByteArray, Charsets.ISO_8859_1)

        val payloadJSON = JSONObject(decryptedPayload)
        println("payload JSON")
        println(payloadJSON.toString())
        return payloadJSON
    }

    fun addPKCS7Padding(data: ByteArray): ByteArray {
        val blockSize = 16
        val paddingLength = blockSize - data.size % blockSize
        val paddedData = data.copyOf(data.size + paddingLength)
        for (i in 0 until paddingLength) {
            paddedData[data.size + i] = paddingLength.toByte()
        }
        return paddedData
    }

    fun generateCryptographicallySecureRandomBytes(length: Int): ByteArray {
        val random = SecureRandom()
        val randomBytes = ByteArray(length)
        random.nextBytes(randomBytes)
        return randomBytes
    }

    fun aesCBCEncrypt(data: ByteArray, iv: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val secretKey = SecretKeySpec(key, "AES")
        val ivParameterSpec = IvParameterSpec(iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec)
        return cipher.doFinal(data)
    }

    fun calculateHMACSHA256(data: String, key: ByteArray): ByteArray {
        val hmacSha256 = Mac.getInstance("HmacSHA256")
        val secretKeySpec = SecretKeySpec(key, "HmacSHA256")
        hmacSha256.init(secretKeySpec)
        return hmacSha256.doFinal(data.toByteArray())
    }


    fun sendEncryptedFrame(unencryptedPayload: String) {
        // Step 1: Remove any formatting from the UNENCRYPTED_PAYLOAD JSON string
        println(unencryptedPayload)

        // Step 2: Add PKCS7 padding to the UNENCRYPTED_PAYLOAD
        val unencryptedPayloadBytes = unencryptedPayload.toByteArray(Charsets.US_ASCII)
        val paddedUnencryptedPayload = addPKCS7Padding(unencryptedPayloadBytes)

        // Step 3: Generate a random IV of 16 bytes
        val iv = generateCryptographicallySecureRandomBytes(16)
        val ivBase64Encoded = Base64.getEncoder().encodeToString(iv)

        // Step 4: Encrypt the paddedUnencryptedPayload using AES-CBC
        val APISessionKey = Base64.getDecoder().decode(apiSessionKey)
        val encryptedPayload = aesCBCEncrypt(paddedUnencryptedPayload, iv, APISessionKey)
        val payloadBase64Encoded = Base64.getEncoder().encodeToString(encryptedPayload)

        // Step 5: Construct the data for MAC calculation
        val macBase = """{"iv":"$ivBase64Encoded","payload":"$payloadBase64Encoded"}"""

        // Step 6: Calculate the HMAC-SHA256 using API Auth Key
        val APIAuthKey = apiAuthKey.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val mac = calculateHMACSHA256(macBase, APIAuthKey)
        val macBase64Encoded = Base64.getEncoder().encodeToString(mac)

        // Step 7: Construct the ENCRYPTED frame
        val encryptedFrame = """{"type":"ENCRYPTED","data":{"iv":"$ivBase64Encoded","payload":"$payloadBase64Encoded"},"mac":"$macBase64Encoded"}"""
        val frame = TextFrame()
        frame.setPayload(ByteBuffer.wrap(encryptedFrame.toByteArray()))
        sendFrame(frame)
        return
    }

    fun sendQuery () {
        if (apiSessionKey == null) { //if we are not authenticated, this message is invalid
            println("This action requires authentication, authenticate session first")
            return
        }

        lastActionId = lastActionId as Long + 1
        val payload = """{"action":{"type":"QUERY","id":$lastActionId}}"""

        sendEncryptedFrame(payload)
    }
    /*fun sendTriggerAction (){
        if (apiSessionKey == null){ //if we are not authenticated, this message is invalid
            println("This action requires authentication, authenticate session first")
            return
        }

        lastActionId = (lastActionId.toLong() ?: 0) + 1

        val payload = JSONObject("""
            {
                "action": {
                    "type": "TRIGGER",
                    "id": ${lastActionId.toLong() % 0x7FFFFFFF}
                }
            }
        """.trimIndent())
        sendEncryptedFrame(payload)
    }*/

    override fun onMessage(message: String?) {
        println("received message $message")

        // TODO: Only do all this if we aren't already authenticated
        if (message == null) return
        val frame = JSONObject(message)
        if (frame.get("type") == "ENCRYPTED") {
            try {
                val decryptedFrame = decryptEncryptedFrame(frame)
                println(decryptedFrame)
                if (decryptedFrame.has("challenge")) {
                    val challenge = JSONObject(decryptedFrame.get("challenge").toString())
                    if (challenge.has("sessionKey") && challenge.has("initialActionId")) {
                        lastActionId = challenge.get("initialActionId").toString().toLong()
                        println("action id: $lastActionId")
                        apiSessionKey = String(
                            challenge.get("sessionKey").toString().toByteArray(),
                            Charsets.ISO_8859_1
                        )
                        println("Authentication challenge received, setting encryption key (session key) to $apiSessionKey")

                        // Send the QUERY action to finish auth
                        lastActionId = lastActionId as Long + 1
                        val payload = """{"action":{"type":"QUERY","id":${lastActionId}}}"""
                        sendEncryptedFrame(payload)
                    }
                }
            } catch (error: Error) {
                println("This is the error:")
                println(error)
            }
        }
    }

    override fun onMessage(bytes: ByteBuffer?) {
        println("received bytebuffer")
    }

    override fun onError(ex: Exception?) {
        println("error $ex")
    }
}
