// ============================================================
// FILE: android/app/src/main/java/com/xrc/utils/ShellUtils.kt
// ============================================================
package com.xrc.utils

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * ShellUtils — executes shell commands on the device.
 *
 * Tries `su` (root) first, falls back to `sh` (non-root).
 * Returns stdout and stderr as a pair.
 */
object ShellUtils {
    private const val TAG = "ShellUtils"

    /**
     * Execute a shell command.
     * @return Pair(stdout, stderr) — null values indicate failure
     */
    fun execute(command: String): Pair<String?, String?> {
        Log.d(TAG, "Exec: $command")
        return try {
            // Try su first
            val process = try {
                Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            } catch (e: Exception) {
                // Fallback to sh
                Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            }

            val stdout = BufferedReader(InputStreamReader(process.inputStream))
            val stderr = BufferedReader(InputStreamReader(process.errorStream))

            val stdoutText = stdout.readText()
            val stderrText = stderr.readText()

            process.waitFor()
            stdout.close()
            stderr.close()

            Pair(stdoutText.ifEmpty { null }, stderrText.ifEmpty { null })
        } catch (e: Exception) {
            Log.w(TAG, "Shell exec failed: ${e.message}")
            Pair(null, e.message)
        }
    }

    /**
     * Execute a shell command and return only stdout.
     */
    fun exec(command: String): String? = execute(command).first

    /**
     * Check if root is available.
     */
    fun isRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()
            output.contains("uid=0")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if device is rooted (alternative method).
     */
    fun checkRootPaths(): Boolean {
        val rootPaths = listOf(
            "/system/app/Superuser.apk",
            "/system/bin/su",
            "/system/xbin/su",
            "/system/framework/services.jar",
            "/sbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        return rootPaths.any { java.io.File(it).exists() }
    }
}
