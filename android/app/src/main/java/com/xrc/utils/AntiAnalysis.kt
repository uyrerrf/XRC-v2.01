// ============================================================
// FILE: android/app/src/main/java/com/xrc/utils/AntiAnalysis.kt
// ============================================================
package com.xrc.utils

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.os.Process
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * AntiAnalysis — detection of emulators, debuggers, root, and analysis tools.
 *
 * Checks:
 * - Emulator artifacts (build props, files, drivers)
 * - Debugger attachment
 * - Root detection (su binaries, test keys)
 * - Analysis tools (Frida, Xposed, Substrate)
 * - Hook frameworks
 * - VPN/proxy detection for MITM detection
 */
class AntiAnalysis(private val context: Context) {
    companion object {
        private const val TAG = "AntiAnalysis"
    }

    /**
     * Run full analysis check.
     * Returns true if device is flagged (emulator/debug/hooked).
     */
    fun isFlagged(): Boolean {
        return isEmulator() || isDebuggerAttached() || isRooted() || isHooked()
    }

    /**
     * Check if running on an emulator.
     */
    fun isEmulator(): Boolean {
        val props = listOf(
            Build.BRAND.lowercase(),
            Build.DEVICE.lowercase(),
            Build.MODEL.lowercase(),
            Build.MANUFACTURER.lowercase(),
            Build.HARDWARE.lowercase(),
            Build.PRODUCT.lowercase(),
            Build.FINGERPRINT.lowercase()
        )

        val emulatorSignals = listOf(
            "sdk", "generic", "google_sdk", "emulator", "android_x86",
            "android_x86_64", "vbox", "goldfish", "ranchu",
            "cutf_x86", "cutf_arm64", "Android SDK", "X86_64"
        )

        val matchCount = props.count { prop ->
            emulatorSignals.any { signal -> prop.contains(signal) }
        }

        if (matchCount >= 3) return true

        // Check for emulator-specific files
        val emulatorFiles = listOf(
            "/system/lib/libc_malloc_leak.so",
            "/sys/qemu_trace",
            "/system/bin/qemu-props",
            "/system/lib/libgoldfish.so",
            "/system/build.prop"
        )

        // Check for QEMU driver
        try {
            val driverFile = File("/proc/tty/drivers")
            if (driverFile.exists()) {
                val content = driverFile.readText()
                if (content.contains("goldfish")) return true
            }
        } catch (e: Exception) {}

        // Check ADB
        if (Build.DEBUG) return true

        return false
    }

    /**
     * Check if debugger is attached.
     */
    fun isDebuggerAttached(): Boolean {
        return Debug.isDebuggerConnected() || Debug.waitingForDebugger()
    }

    /**
     * Check if device is rooted.
     */
    fun isRooted(): Boolean {
        // Check su binary paths
        val suPaths = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/data/local/su", "/data/local/xbin/su",
            "/data/local/bin/su", "/system/sd/xbin/su",
            "/system/bin/failsafe/su", "/data/local/su"
        )
        for (path in suPaths) {
            if (File(path).exists()) return true
        }

        // Check root manager apps
        val rootApps = listOf(
            "com.noshufou.android.su",
            "com.noshufou.android.su.elite",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.thirdparty.superuser",
            "com.topjohnwu.magisk",
            "com.kingroot.master",
            "com.kingoapp.root",
            "com.qihoo.permmgr",
            "com.dianxinos.root"
        )
        val pm = context.packageManager
        for (pkg in rootApps) {
            try {
                pm.getPackageInfo(pkg, 0)
                return true
            } catch (e: PackageManager.NameNotFoundException) {}
        }

        // Check if running as root
        try {
            val process = Runtime.getRuntime().exec("id")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            if (output.contains("uid=0")) return true
        } catch (e: Exception) {}

        // Check for Magisk
        val magiskFiles = listOf("/sbin/.magisk", "/cache/magisk", "/data/adb/magisk")
        for (f in magiskFiles) {
            if (File(f).exists()) return true
        }

        return false
    }

    /**
     * Check if hook frameworks are detected.
     */
    fun isHooked(): Boolean {
        // Check for Xposed
        try {
            Class.forName("de.robv.android.xposed.XposedBridge")
            return true
        } catch (e: ClassNotFoundException) {}

        // Check for Frida
        try {
            val fridaLibs = listOf("frida-agent", "frida-gum", "frida-core")
            for (lib in fridaLibs) {
                try {
                    System.loadLibrary(lib)
                    return true
                } catch (e: UnsatisfiedLinkError) {}
            }
        } catch (e: Exception) {}

        // Check for Frida by scanning /proc/self/maps
        try {
            val maps = File("/proc/self/maps")
            if (maps.exists()) {
                val content = maps.readText()
                if (content.contains("frida") || content.contains("gum")) return true
            }
        } catch (e: Exception) {}

        // Check for Substrate/Cydia
        try {
            Class.forName("com.saurik.substrate.MS")
            return true
        } catch (e: ClassNotFoundException) {}

        return false
    }

    /**
     * Check if running in safe mode.
     */
    fun isSafeMode(): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            pm.isPowerSaveMode
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check debug flags in application info.
     */
    fun isDebuggable(): Boolean {
        return (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    /**
     * Check for specific analysis tools in process list.
     */
    fun hasAnalysisTools(): Boolean {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val processes = am.runningAppProcesses
            if (processes != null) {
                for (proc in processes) {
                    val name = proc.processName.lowercase()
                    if (name.contains("frida") || name.contains("xposed") ||
                        name.contains("hook") || name.contains("inspeckage") ||
                        name.contains("objection") || name.contains("drozer") ||
                        name.contains("android_server")) return true
                }
            }
        } catch (e: Exception) {}
        return false
    }

    /**
     * Get detailed analysis report.
     */
    fun getReport(): Map<String, Boolean> {
        return mapOf(
            "emulator" to isEmulator(),
            "debugger" to isDebuggerAttached(),
            "rooted" to isRooted(),
            "hooked" to isHooked(),
            "safe_mode" to isSafeMode(),
            "debuggable" to isDebuggable(),
            "analysis_tools" to hasAnalysisTools()
        )
    }
}
