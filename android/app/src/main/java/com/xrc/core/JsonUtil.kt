// ============================================================
// FILE: android/app/src/main/java/com/xrc/core/JsonUtil.kt
// ============================================================
package com.xrc.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Recursively converts arbitrary Kotlin values (including
 * Map<String, Any>, List<Map<String, Any>>, ByteArray) into
 * kotlinx.serialization JsonElements, then to JSON strings.
 *
 * This replaces Protocol.json.encodeToString(...) for dynamic maps,
 * which kotlinx.serialization cannot serialize directly.
 */
fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is String -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is ByteArray -> JsonPrimitive(
        android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)
    )
    is Map<*, *> -> JsonObject(
        this.entries.associate { (k, v) -> k.toString() to v.toJsonElement() }
    )
    is List<*> -> JsonArray(this.map { it.toJsonElement() })
    else -> JsonPrimitive(this.toString())
}

fun Any?.toJsonString(): String = toJsonElement().toString()
