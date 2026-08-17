package com.xrc.core.config

import android.content.Context
import org.json.JSONObject

data class XrcConfig(
    val c2Host: String,
    val c2Port: Int,
    val beaconIntervalMs: Long,
    val useTls: Boolean
) {
    companion object {
        private const val ASSET_NAME = "xrc_config.json"

        fun load(context: Context): XrcConfig {
            return try {
                val raw = context.assets.open(ASSET_NAME)
                    .bufferedReader()
                    .use { it.readText() }
                val json = JSONObject(raw)
                XrcConfig(
                    c2Host = json.optString("c2_host", "127.0.0.1"),
                    c2Port = json.optInt("c2_port", 4444),
                    beaconIntervalMs = json.optLong("beacon_interval_ms", 30_000L),
                    useTls = json.optBoolean("use_tls", false)
                )
            } catch (_: Exception) {
                XrcConfig(c2Host = "127.0.0.1", c2Port = 4444, beaconIntervalMs = 30_000L, useTls = false)
            }
        }
    }
}
