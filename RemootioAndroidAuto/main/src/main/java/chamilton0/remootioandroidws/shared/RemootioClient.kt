package chamilton0.remootioandroidws.shared

import java.net.URI
import org.java_websocket.client.WebSocketClient
import org.java_websocket.framing.TextFrame
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.apache.commons.text.StringEscapeUtils
import java.lang.Exception
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec

class RemootioClient(
    deviceHost: String, // The full device URI including WebSocket scheme and port number
    private val apiAuthKey: String, // The API Auth hex key as a string
    private val apiSecretKey: String, // The API Secret hex key as a string
) : WebSocketClient(URI(deviceHost)) {
    init {
        // Validate the API keys are hex strings
        val hexStringRegex = "[0-9A-Fa-f]{64}".toRegex()
        if (!hexStringRegex.matches(apiAuthKey)) {
            throw IllegalArgumentException("API Auth key is not hex string, check input")
        }
        if (!hexStringRegex.matches(apiSecretKey)) {
            throw IllegalArgumentException("API Secret key is not hex string, check input")
        }
    }

    var state: String = ""
    private var apiSessionKey: String? = null // The current API session Base64 encoded string
    private var lastActionId: Long = 0 // The last action ID
    /*private val autoReconnect: boolean
    private val sendPingMessageEveryXMs: number
    // private val sendPingMessageIntervalHandle?: ReturnType<typeof setInterval>
    private val pingReplyTimeoutXMs: number
    // private val pingReplyTimeoutHandle?: ReturnType<typeof setTimeout>;
    private val waitingForAuthenticationQueryActionResponse?: boolean*/

    /**
     * Called when the connection is opened
     * Sets up the authenticated session for the Remootio device
     */
    override fun onOpen(handshakedata: ServerHandshake?) {
        // When we open a connection, we then need to send an AUTH frame and authenticate
        val data = "{\"type\":\"AUTH\"}".toByteArray()
        val frame = TextFrame()
        frame.setPayload(ByteBuffer.wrap(data))
        /**
         * This begins that authentication handshake that we will continue when we receive the
         * challenge message
         */
        sendFrame(frame)
    }

    /**
     * Called when the connection is closed
     */
    override fun onClose(code: Int, reason: String?, remote: Boolean) {
        println("Connection closed with exit code: $code reason: $reason")
    }

    /**
     * Handle a WebSocket error
     */
    override fun onError(ex: Exception?) {
        val errorMessage = ex?.message
        close(1011, errorMessage)
        throw Error("WebSocket error: $errorMessage")
    }

    /**
     * Decrypts an AES cipher text into a ByteArray
     */
    private fun decryptAES(ciphertext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val secretKey = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
        val ivParameterSpec = IvParameterSpec(iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec)
        return cipher.doFinal(ciphertext)
    }

    /**
     * Generates a MAC with the API Auth Key for frame data
     */
    private fun generateFrameMac(frameData: String): String {
        // Convert the API Auth key into a ByteArray
        val apiAuthHexKey = apiAuthKey.chunked(2).map {
            it.toInt(16).toByte()
        }.toByteArray()

        // Create a MAC for the frame data, using the API Auth Key
        val macKey = SecretKeySpec(apiAuthHexKey, "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(macKey)

        // Create a MAC to ensure that the message has not been tampered with
        val macBytes = mac.doFinal(
            // Unescape JSON here to convert the frame data to the correct type
            StringEscapeUtils.unescapeJson(
                JSONObject(frameData).toString()
            ).toByteArray(StandardCharsets.UTF_8)
        )

        return Base64.getEncoder().encodeToString(macBytes)
    }

    /**
     * Decrypts an encrypted RemootioFrame to a JSONObject
     */
    private fun decryptEncryptedFrame(frame: JSONObject): JSONObject {
        val frameData = frame.get("data").toString()

        val base64mac = generateFrameMac(frameData)
        // Check if the calculated MAC matches the one sent by the API
        if (base64mac != frame.get("mac")) {
            // If the MAC doesn't match, disconnect and throw error
            close(1011, "Failed to decrypt message")
            throw Error(
                "Decryption error: calculated MAC $base64mac does not match the MAC" +
                        " from the API ${frame.get("mac")}"
            )
        }

        // Convert the frame data to a JSON object
        val data = JSONObject(frameData)

        // Get the frame payload and IV as ByteArrays
        val payload = Base64.getDecoder().decode(data.get("payload").toString())
        val iv = Base64.getDecoder().decode(data.get("iv").toString())

        // Convert the API Secret key or Session key into a ByteArray
        var secretHexKey = apiSecretKey.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        if (apiSessionKey != null) {
            secretHexKey = Base64.getDecoder().decode(apiSessionKey)
        }

        // Decrypt the payload with the key and IV
        val decryptedPayloadByteArray = decryptAES(payload, secretHexKey, iv)
        val decryptedPayload = String(decryptedPayloadByteArray, Charsets.ISO_8859_1)

        return JSONObject(decryptedPayload)
    }

    /**
     * Utility function to generate a random ByteArray
     */
    private fun generateCryptographicallySecureRandomBytes(): ByteArray {
        val random = SecureRandom()
        val randomBytes = ByteArray(16)
        random.nextBytes(randomBytes)
        return randomBytes
    }

    /**
     * Utility function to encrypt a ByteArray using AES
     */
    private fun aesCBCEncrypt(data: ByteArray, iv: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
        val secretKey = SecretKeySpec(key, "AES")
        val ivParameterSpec = IvParameterSpec(iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec)
        return cipher.doFinal(data)
    }

    /**
     * Sends a payload as an encrypted frame
     */
    private fun sendEncryptedFrame(unencryptedPayload: String) {
        // Add PKCS7 padding to the UNENCRYPTED_PAYLOAD
        val unencryptedPayloadBytes = unencryptedPayload.toByteArray(Charsets.US_ASCII)

        // Generate a random IV of 16 bytes
        val iv = generateCryptographicallySecureRandomBytes()
        val ivBase64Encoded = Base64.getEncoder().encodeToString(iv)

        // Encrypt the paddedUnencryptedPayload using AES-CBC
        val apiSessionKeyBytes = Base64.getDecoder().decode(apiSessionKey)
        val encryptedPayload = aesCBCEncrypt(
            unencryptedPayloadBytes,
            iv,
            apiSessionKeyBytes,
        )
        val payloadBase64Encoded = Base64.getEncoder().encodeToString(encryptedPayload)

        // Construct the data for MAC calculation
        val macBase = """{"iv":"$ivBase64Encoded","payload":"$payloadBase64Encoded"}"""

        val macBase64Encoded = generateFrameMac(macBase)
        // Construct the ENCRYPTED frame
        val encryptedFrame = """{"type":"ENCRYPTED","data":{"iv":"$ivBase64Encoded",
            |"payload":"$payloadBase64Encoded"},"mac":"$macBase64Encoded"}""".trimMargin()
        val frame = TextFrame()
        frame.setPayload(ByteBuffer.wrap(encryptedFrame.toByteArray()))
        sendFrame(frame)
        return
    }

    /**
     * Sends an action to the remootio device
     */
    private fun sendAction(action: String) {
        // If we are not authenticated, this message is invalid
        if (apiSessionKey == null) {
            println("This action requires authentication, authenticate session first")
            return
        }

        lastActionId += 1
        val payload = """{"action":{"type":"$action","id":$lastActionId}}"""

        sendEncryptedFrame(payload)
    }

    /**
     * Sends a query to the Remootio Device
     */
    fun sendQuery() {
        sendAction("QUERY")
    }

    /**
     * Sends a trigger action to the Remootio Device
     */
    fun sendTriggerAction() {
        sendAction("TRIGGER")
    }

    /**
     * Ensures the challenge
     */
    private fun handleChallengeFrame(decryptedFrame: JSONObject) {
        val challenge = JSONObject(decryptedFrame.get("challenge").toString())
        if (!challenge.has("sessionKey") || !challenge.has("initialActionId")) {
            close(1011, "Challenge frame not set up correctly")
            throw IllegalArgumentException("Challenge frame missing sessionKey or initialActionId")
        }
        lastActionId = challenge.get("initialActionId").toString().toLong()
        apiSessionKey = String(
            challenge.get("sessionKey").toString().toByteArray(),
            Charsets.ISO_8859_1
        )

        // Send the QUERY action to finish auth
        lastActionId += 1
        val payload = """{"action":{"type":"QUERY","id":${lastActionId}}}"""
        sendEncryptedFrame(payload)
        println("Now authenticated")
    }

    /**
     * Handles a decrypted frame response
     */
    private fun handleDecryptedFrame(decryptedFrame: JSONObject) {
        // The frame should always have a type
        if (!decryptedFrame.has("type")) {
            throw IllegalArgumentException(
                "Received frame $decryptedFrame does not have a 'type' field"
            )
        }

        if (decryptedFrame.get("type") == "QUERY") {
            state = decryptedFrame.get(("state")).toString()
            println("Door state is now $state")
        }
    }

    /**
     * Handles receiving a message from the Remootio Device
     */
    override fun onMessage(message: String?) {
        println("Received message: $message")
        // If there is no message, nothing to do
        if (message == null) return

        // Convert the message to an object
        val frame = JSONObject(message)

        // The frame should always have a type
        if (!frame.has("type")) {
            throw IllegalArgumentException("Received frame $message does not have a 'type' field")
        }

        if (frame.get("type") == "ERROR") {
            if (frame.has("errorMessage")) {
                val errorMessage = frame.get("errorMessage").toString()
                close(1011, errorMessage)
                throw Error("Received error from Remootio: $errorMessage")
            }
        }

        // Handle receiving an encrypted frame
        if (frame.get("type") == "ENCRYPTED") {
            val decryptedFrame = decryptEncryptedFrame(frame)
            println("Decrypted frame: $decryptedFrame")

            // Check if this is a challenge to our auth frame
            if (decryptedFrame.has("challenge")) {
                handleChallengeFrame(decryptedFrame)
            } else {
                // Otherwise decrypt the message and use it somehow
                // TODO: The query should have a response but other types may not
                if (decryptedFrame.has("response")) {
                    handleDecryptedFrame(JSONObject(decryptedFrame.get("response").toString()))
                }
            }
        }
    }
}
