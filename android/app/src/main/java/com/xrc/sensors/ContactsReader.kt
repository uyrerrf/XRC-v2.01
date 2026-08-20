// ============================================================
// FILE: android/app/src/main/java/com/xrc/sensors/ContactsReader.kt
// ============================================================
package com.xrc.sensors

import android.content.Context
import android.provider.ContactsContract

/**
 * ContactsReader — reads device contacts.
 *
 * Requires READ_CONTACTS permission.
 */
class ContactsReader(private val context: Context) {
    companion object {
        private const val TAG = "ContactsReader"
    }

    /**
     * Get all contacts with phone numbers.
     */
    fun getAll(): List<Map<String, Any>> {
        val contacts = mutableListOf<Map<String, Any>>()
        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE
                ),
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )
            cursor?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(c.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)) ?: "Unknown"
                    val number = c.getString(c.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.NUMBER)) ?: ""
                    val type = c.getInt(c.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.TYPE))

                    contacts.add(mapOf(
                        "name" to name,
                        "number" to number,
                        "type" to when (type) {
                            ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "home"
                            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "mobile"
                            ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "work"
                            else -> "other"
                        }
                    ))
                }
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Read contacts failed: ${e.message}")
        }
        return contacts
    }

    /**
     * Search contacts by name or number.
     */
    fun search(query: String): List<Map<String, Any>> {
        return getAll().filter { contact ->
            (contact["name"] as? String)?.contains(query, ignoreCase = true) == true ||
            (contact["number"] as? String)?.contains(query) == true
        }
    }

    /**
     * Get contact count.
     */
    fun getCount(): Int = getAll().size
}
