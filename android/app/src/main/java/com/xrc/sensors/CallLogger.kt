// ============================================================
// FILE: android/app/src/main/java/com/xrc/sensors/CallLogger.kt
// ============================================================
package com.xrc.sensors

import android.content.Context
import android.provider.CallLog

/**
 * CallLogger — reads call history from the device.
 *
 * Requires READ_CALL_LOG permission.
 */
class CallLogger(private val context: Context) {
    companion object {
        private const val TAG = "CallLogger"
    }

    /**
     * Get call log entries.
     */
    fun getCallLog(limit: Int = 100): List<Map<String, Any>> {
        val calls = mutableListOf<Map<String, Any>>()
        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls._ID,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION
                ),
                null, null,
                "${CallLog.Calls.DATE} DESC LIMIT $limit"
            )
            cursor?.use { c ->
                while (c.moveToNext()) {
                    val number = c.getString(c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)) ?: ""
                    val name = c.getString(c.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)) ?: ""
                    val type = c.getInt(c.getColumnIndexOrThrow(CallLog.Calls.TYPE))
                    val date = c.getLong(c.getColumnIndexOrThrow(CallLog.Calls.DATE))
                    val duration = c.getLong(c.getColumnIndexOrThrow(CallLog.Calls.DURATION))

                    val typeStr = when (type) {
                        CallLog.Calls.INCOMING_TYPE -> "incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                        CallLog.Calls.MISSED_TYPE -> "missed"
                        CallLog.Calls.REJECTED_TYPE -> "rejected"
                        CallLog.Calls.BLOCKED_TYPE -> "blocked"
                        else -> "unknown"
                    }

                    calls.add(mapOf(
                        "number" to number,
                        "name" to name,
                        "type" to typeStr,
                        "date" to date,
                        "timestamp" to java.text.SimpleDateFormat(
                            "yyyy-MM-dd HH:mm:ss", java.util.Locale.US
                        ).format(java.util.Date(date)),
                        "duration_sec" to duration
                    ))
                }
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Call log failed: ${e.message}")
        }
        return calls
    }

    /**
     * Delete call log (privacy scrub).
     */
    fun deleteAll(): Boolean {
        return try {
            context.contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null) > 0
        } catch (e: Exception) {
            false
        }
    }
}
