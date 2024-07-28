package com.fingerprint.manager

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import com.fingerprint.manager.FingerprintManagerImpl.Companion.ACTION_USB_PERMISSION
import com.fingerprint.scanner.FingerprintScanner
import com.fingerprint.scanner.FutronictechFingerprintScanner
import com.fingerprint.scanner.FutronictechFingerprintScanner.Companion.isFutronicDevice
import com.fingerprint.scanner.HfSecurityFingerprintScanner
import com.fingerprint.scanner.HfSecurityFingerprintScanner.Companion.isHfSecurityDevice
import com.fingerprint.utils.ScannedImageType
import com.fingerprint.utils.returnUnit
import com.fingerprint.utils.toImageBitmap
import com.fingerprint.utils.toRawByteArray
import com.fingerprint.utils.toRawImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch


internal class FingerprintManagerImpl(
    private val scope: CoroutineScope,
    private val context: Context,
    private val lifecycle: Lifecycle,
    private val initializeFingerprintScanner: () -> FingerprintScanner?
) : FingerprintManager {
    private var fingerprintScanner: FingerprintScanner? = initializeFingerprintScanner()
    override var progress: Float = 0f
    override val eventsFlow = MutableStateFlow<FingerprintEvent>(FingerprintEvent.Idle)
    override val captures: MutableList<ImageBitmap> by lazy { mutableStateListOf() }
    override var bestCapture: ImageBitmap? by mutableStateOf(null)
    override var bestCaptureIndex: Int = Int.MIN_VALUE
    override val deviceInfo: FingerprintDeviceInfo
        get() = fingerprintScanner?.deviceInfo ?: FingerprintDeviceInfo.Unknown

    private var bestCaptureValue: Float = Float.MIN_VALUE
    private var brightnessThreshold: Float = DEFAULT_BRIGHTNESS_THRESHOLD
    private var imageType: ScannedImageType = ScannedImageType.Extra
    private var scanningJob: Job? = null
    private var isCanceled: Boolean = false
    private var isConnected: Boolean = false
    private var captureCount: Int = 0
    private var captureIndex: Int = 0
    private var captureTimeout: Int = 0
    private var permissionIntent: PendingIntent? = null
    private val usbManager: UsbManager? = context.getSystemService()

    override fun connect() {
        fingerprintScanner = initializeFingerprintScanner()
        registerReceiver()
        requestUsbPermission()
        if (isConnected) fingerprintScanner?.turnOffLed()
    }

    override fun disconnect() {
        isCanceled = true
        isConnected = false
        unregisterReceiver()
        fingerprintScanner?.disconnect()
        eventsFlow.tryEmit(FingerprintEvent.Disconnected)
    }

    override fun scan(count: Int): Boolean {
        if (!isConnected) {
            eventsFlow.tryEmit(FingerprintEvent.ConnectingFailed)
            return false
        }
        reset()
        captureCount = count.coerceAtMost(MAX_SCAN_COUNT)
        scanningJob = scope.launch { startProcessing() }
        return true
    }

    private fun unregisterReceiver() = runCatching {
        if (lifecycle.currentState == Lifecycle.State.CREATED)
            context.unregisterReceiver(usbReceiver)
    }

    private fun registerReceiver() {
        permissionIntent = context.createPendingIntent()
        val filter = IntentFilter(ACTION_USB_PERMISSION).apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(usbReceiver, filter)
    }

    private fun requestUsbPermission() {
        usbManager.supportedDevice?.let { device ->
            val fingerprintScanner = fingerprintScanner ?: return
            if (usbManager!!.hasPermission(device))
                isConnected = fingerprintScanner.reconnect(device).apply {
                    eventsFlow.tryEmit(if (this) FingerprintEvent.Connected else FingerprintEvent.ConnectingFailed)
                }
            else
                usbManager.requestPermission(device, permissionIntent)
        } ?: eventsFlow.tryEmit(FingerprintEvent.Idle)
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = synchronized(this) {
            fingerprintScanner = initializeFingerprintScanner()
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    val device: UsbDevice = (
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                intent.getParcelableExtra(
                                    UsbManager.EXTRA_DEVICE,
                                    UsbDevice::class.java
                                )
                            else
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                            )
                        ?: return eventsFlow.tryEmit(FingerprintEvent.ConnectingFailed).returnUnit()

                    val isGranted = intent.getBooleanExtra(
                        UsbManager.EXTRA_PERMISSION_GRANTED,
                        false
                    )

                    val fingerprintScanner = fingerprintScanner ?: return
                    if (isGranted)
                        isConnected = fingerprintScanner.reconnect(device).apply {
                            eventsFlow.tryEmit(if (this) FingerprintEvent.Connected else FingerprintEvent.ConnectingFailed)
                        }
                    else
                        eventsFlow.tryEmit(FingerprintEvent.ConnectingFailed)
                }

                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    eventsFlow.tryEmit(FingerprintEvent.DeviceAttached)
                    requestUsbPermission()
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    eventsFlow.tryEmit(FingerprintEvent.DeviceDetached)
                    disconnect()
                }

                else -> Unit
            }
        }.returnUnit()
    }

    private suspend fun startProcessing() = runCatching {
        for (i in 0..<captureCount) {
            if (isCanceled) return@runCatching
            captureIndex = i
            processCapture()
        }
        eventsFlow.emit(FingerprintEvent.CapturedSuccessfully)
        improveTheBestCapture()
    }.onFailure { Log.e("DEBUGGING -> startProcessing() -> ", it.toString()) }

    override fun improveTheBestCapture(isApplyFilters: Boolean, isBlue: Boolean) {
        captures.getOrNull(bestCaptureIndex)?.run {
            val bitmap = asAndroidBitmap()
            val byteArray = bitmap.toRawByteArray()

            if (isApplyFilters)
                for (i in byteArray.indices step 4) {
                    val brightness = byteArray.getPixelBrightness(i)
                    if (brightness <= brightnessThreshold)
                        byteArray.writeColor(
                            red = false,
                            green = false,
                            blue = isBlue,
                            position = i,
                            alpha = (fingerprintScanner is HfSecurityFingerprintScanner)
                        )
                    else
                        byteArray.writeWhiteColor(i)
                }
            bestCapture = byteArray.toRawImageBitmap(width, height)
        }
    }

    private fun onFingerLiftDuringScanning() {
        scanningJob?.cancel()
        isCanceled = true
        eventsFlow.tryEmit(FingerprintEvent.ProcessCanceledTheFingerLifted)
    }

    private fun reset() {
        scanningJob?.cancel()
        captures.clear()
        bestCapture = null
        scanningJob = null
        isCanceled = false
        captureIndex = 0
        captureTimeout = 0
        progress = 0f
        bestCaptureIndex = Int.MIN_VALUE
        bestCaptureValue = Float.MIN_VALUE
    }

    private suspend fun processCapture() {
        val isFirstCapture = captureIndex == 0
        if (isFirstCapture)
            eventsFlow.emit(FingerprintEvent.PlaceFinger)
        else
            eventsFlow.emit(FingerprintEvent.KeepFinger)

        if (!captureImage()) return
        if (!getImageData()) return

        progress = (captureIndex + 1) / captureCount.toFloat()
    }

    private suspend fun captureImage(): Boolean {
        val fingerprintScanner = fingerprintScanner ?: return false
        while (true) {
            delay(SCAN_DELAY_IN_MILLIS)
            if (fingerprintScanner.captureImage(imageType)) return true
            if (captureIndex in 1..captureCount) onFingerLiftDuringScanning()
            if (isCanceled) return false
        }
    }

    private fun initializeBrightnessThreshold(bitmap: ImageBitmap) {
        if (brightnessThreshold != DEFAULT_BRIGHTNESS_THRESHOLD) return
        brightnessThreshold = if (fingerprintScanner is FutronictechFingerprintScanner)
            bitmap.width / 1.291f
        else
            bitmap.width / 2.3f
    }

    private suspend fun getImageData(): Boolean = runCatching {
        val fingerprintScanner = fingerprintScanner ?: return false
        val bitmapArray = fingerprintScanner.getImageBytes()
        return if (bitmapArray != null) {
            val bitmap = bitmapArray.toImageBitmap()
            initializeBrightnessThreshold(bitmap)
            findTheBestCapture(bitmapArray)
            captures.add(bitmap)
            eventsFlow.emit(FingerprintEvent.NewImage(bitmapArray))
            delay(SCAN_DELAY_IN_MILLIS)
            true
        } else {
            eventsFlow.tryEmit(FingerprintEvent.CapturingFailed)
            false
        }
    }.onFailure { Log.e("DEBUGGING -> getImageData() -> ", it.toString()) }
        .getOrDefault(false)

    private fun findTheBestCapture(byteArray: ByteArray) {
        val newValue = calculateDarkness(byteArray)
        if (newValue > bestCaptureValue) {
            bestCaptureValue = newValue
            bestCaptureIndex = captureIndex
        }
    }

    private fun calculateDarkness(imageArray: ByteArray): Float {
        var count = 0f
        for (i in imageArray.indices step 4) {
            val brightness = imageArray.getPixelBrightness(i)
            if (brightness <= brightnessThreshold / 1.75f) count += brightness
        }
        return count
    }

    companion object {
        const val MAX_SCAN_COUNT = 5
        const val SCAN_DELAY_IN_MILLIS: Long = 50
        const val DEFAULT_BRIGHTNESS_THRESHOLD = 128f
        const val ACTION_USB_PERMISSION = "com.fingerprint.USB_PERMISSION"
    }
}

private fun UsbDevice.isSupportedDevice(): Boolean =
    isHfSecurityDevice(vendorId = vendorId, productId = productId)
            || isFutronicDevice(vendorId = vendorId, productId = productId)

private fun Context.createPendingIntent(): PendingIntent {
    val intent = Intent(ACTION_USB_PERMISSION)
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    else
        PendingIntent.FLAG_IMMUTABLE
    return PendingIntent.getBroadcast(this, 0, intent, flags)
}

private val UsbManager?.supportedDevice: UsbDevice?
    get() = this?.deviceList?.values?.firstOrNull(UsbDevice::isSupportedDevice)

private fun ByteArray.getPixelBrightness(position: Int): Float = runCatching {
    val red = (this[position + 0].toInt() shr 16) and 0xFF
    val green = (this[position + 1].toInt() shr 8) and 0xFF
    val blue = this[position + 2].toInt() and 0xFF
    val brightness = 0.299f * red + 0.587f * green + 0.114f * blue
    return brightness
}.getOrDefault(0f)

private fun ByteArray.writeColor(
    position: Int,
    red: Int,
    green: Int,
    blue: Int,
    isAlpha: Boolean = false
) = runCatching {
    this[position + 0] = red.toByte()
    this[position + 1] = green.toByte()
    this[position + 2] = blue.toByte()
    if (isAlpha)
        this[position + 3] = 255.toByte()
}

private fun ByteArray.writeColor(
    position: Int,
    red: Boolean,
    green: Boolean,
    blue: Boolean,
    alpha: Boolean = false,
) = writeColor(
    position = position,
    red = if (red) 255 else 0,
    green = if (green) 255 else 0,
    blue = if (blue) 255 else 0,
    isAlpha = alpha
)

private fun ByteArray.writeWhiteColor(position: Int) = writeColor(
    position,
    red = true,
    green = true,
    blue = true
)
