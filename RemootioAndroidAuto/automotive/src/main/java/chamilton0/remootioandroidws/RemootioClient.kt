package chamilton0.remootioandroidws

import java.net.URI
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.lang.Exception
import java.nio.ByteBuffer

class RemootioClient(remootioUri: URI) : WebSocketClient(remootioUri) {

    override fun onOpen(handshakedata: ServerHandshake?) {
        println("new connection opened")
    }

    override fun onClose(code: Int, reason: String?, remote: Boolean) {
        println("closed with exit code: $code reason: $reason")
    }

    override fun onMessage(message: String?) {
        println("received message $message")
    }

    override fun onMessage(bytes: ByteBuffer?) {
        println("received bytebuffer")
    }

    override fun onError(ex: Exception?) {
        println("error $ex")
    }
}
