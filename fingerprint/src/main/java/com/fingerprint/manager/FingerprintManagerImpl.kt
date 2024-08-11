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
import com.fingerprint.scanner.FingerprintScanner
import com.fingerprint.scanner.FutronictechFingerprintScanner
import com.fingerprint.scanner.FutronictechFingerprintScanner.Companion.isFutronicDevice
import com.fingerprint.scanner.HfSecurityFingerprintScanner
import com.fingerprint.scanner.HfSecurityFingerprintScanner.Companion.isHfSecurityDevice
import com.fingerprint.utils.Constants.ACTION_USB_PERMISSION
import com.fingerprint.utils.Constants.DEFAULT_BRIGHTNESS_THRESHOLD
import com.fingerprint.utils.ScannedImageType
import com.fingerprint.utils.getPixelBrightness
import com.fingerprint.utils.returnUnit
import com.fingerprint.utils.toImageBitmap
import com.fingerprint.utils.toRawByteArray
import com.fingerprint.utils.toRawImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch


internal class FingerprintManagerImpl(
    private val scope: CoroutineScope,
    private val context: Context,
    private val lifecycle: Lifecycle,
    private val initializeFingerprintScanner: () -> FingerprintScanner?
) : FingerprintManager {
    private var fingerprintScanner: FingerprintScanner? = initializeFingerprintScanner()
    override var progress: Float = 0f
    override val eventsFlow = MutableSharedFlow<FingerprintEvent>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val captures: MutableList<ImageBitmap> by lazy { mutableStateListOf() }
    override var bestCapture: ImageBitmap? by mutableStateOf(null)
    override var bestCaptureIndex: Int = INVALID_INDEX
    override var isConnected: Boolean by mutableStateOf(false)
    override val deviceInfo: FingerprintDeviceInfo
        get() = fingerprintScanner?.deviceInfo ?: FingerprintDeviceInfo.Unknown

    private var bestCaptureValue: Float = MIN_VALUE
    private var brightnessThreshold: Float = DEFAULT_BRIGHTNESS_THRESHOLD
    private var imageType: ScannedImageType = ScannedImageType.Extra
    private var scanningJob: Job? = null
    private var isLocked: Boolean = false
    private var isCanceled: Boolean = false
    private var isUsbPermissionGranted: Boolean = false
    private var isUsbPermissionRequestInProgress: Boolean = false
    private var captureCount: Int = 0
    private var captureIndex: Int = 0
    private var captureTimeout: Int = 0
    private var permissionIntent: PendingIntent? = null
    private val usbManager: UsbManager? = context.getSystemService()

    override fun connect() {
        if (isUsbPermissionGranted || isLocked) return
        fingerprintScanner = initializeFingerprintScanner()
        registerReceiver()
        requestUsbPermission()
        if (isConnected) fingerprintScanner?.turnOffLed()
    }

    override fun disconnect() {
        isCanceled = true
        isConnected = false
        isLocked = false
        isUsbPermissionGranted = false
        isUsbPermissionRequestInProgress = false
        unregisterReceiver()
        fingerprintScanner?.disconnect()
        emitEvent(FingerprintEvent.Disconnected)
    }

    override fun scan(count: Int): Boolean {
        if (!isConnected) {
            emitEvent(FingerprintEvent.ConnectingFailed)
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

    private fun emitEvent(event: FingerprintEvent) =
        scope.launch { eventsFlow.emit(event) }.returnUnit()

    private fun requestUsbPermission() {

        if ((isUsbPermissionGranted || isUsbPermissionRequestInProgress) && isConnected) return

        usbManager.supportedDevice?.let { device ->
            val fingerprintScanner = fingerprintScanner ?: return
            isUsbPermissionGranted = usbManager!!.hasPermission(device)
            if (isUsbPermissionGranted) {
                isConnected = fingerprintScanner.reconnect(device)
                emitEvent(if (isConnected) FingerprintEvent.Connected else FingerprintEvent.ConnectingFailed)
            } else {
                isUsbPermissionRequestInProgress = true
                usbManager.requestPermission(device, permissionIntent)
            }
        } ?: emitEvent(FingerprintEvent.Idle)
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = withLock(
            lock = isLocked,
            onLockChange = { isLocked = it }
        ) {
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
                        ?: return@withLock emitEvent(FingerprintEvent.ConnectingFailed).returnUnit()

                    isUsbPermissionGranted = intent.getBooleanExtra(
                        UsbManager.EXTRA_PERMISSION_GRANTED,
                        false
                    )
                    isUsbPermissionRequestInProgress = false

                    val fingerprintScanner = fingerprintScanner ?: return@withLock
                    when {
                        isUsbPermissionGranted.not() -> emitEvent(FingerprintEvent.ConnectingFailed)
                        run { isConnected = fingerprintScanner.reconnect(device); isConnected } -> {
                            fingerprintScanner.turnOffLed()
                            emitEvent(FingerprintEvent.Connected)
                        }

                        else -> emitEvent(FingerprintEvent.ConnectingFailed)
                    }
                }

                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    isUsbPermissionGranted = false
                    emitEvent(FingerprintEvent.DeviceAttached)
                    requestUsbPermission()
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    isUsbPermissionGranted = false
                    emitEvent(FingerprintEvent.DeviceDetached)
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
        improveTheBestCapture()
        eventsFlow.emit(FingerprintEvent.CapturedSuccessfully)
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

    private suspend fun onFingerLiftDuringScanning() {
        scanningJob?.cancel()
        isCanceled = true
        eventsFlow.emit(FingerprintEvent.ProcessCanceledTheFingerLifted)
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
        bestCaptureIndex = INVALID_INDEX
        bestCaptureValue = MIN_VALUE
    }

    private suspend fun processCapture() {
        val isFirstCapture = captureIndex == FIRST_CAPTURE_INDEX
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
            val isFirstCapture = (captureIndex == FIRST_CAPTURE_INDEX)
            delay(SCAN_DELAY_IN_MILLIS)

            when {
                isCanceled -> return false
                fingerprintScanner.captureImage(imageType) -> return true
                isFirstCapture.not() -> onFingerLiftDuringScanning()
                fingerprintScanner.isCleanRequired() -> {
                    eventsFlow.emit(FingerprintEvent.CleanTheFingerprint)
                    continue
                }

                else -> eventsFlow.emit(FingerprintEvent.KeepFinger)
            }
        }
    }

    private fun initializeBrightnessThreshold(bitmap: ImageBitmap) {
        if (brightnessThreshold != DEFAULT_BRIGHTNESS_THRESHOLD) return
        brightnessThreshold = if (fingerprintScanner is FutronictechFingerprintScanner)
            bitmap.width / FUTRONICTECH_THRESHOLD_DIVISOR
        else
            bitmap.width / DEFAULT_THRESHOLD_DIVISOR
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
            eventsFlow.emit(FingerprintEvent.CapturingFailed)
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
            if (brightness <= brightnessThreshold / DARKNESS_THRESHOLD_DIVISOR)
                count += brightness
        }
        return count
    }

    private companion object {
        const val MAX_SCAN_COUNT = 5
        const val SCAN_DELAY_IN_MILLIS: Long = 50
        const val INVALID_INDEX = Int.MIN_VALUE
        const val MIN_VALUE = Float.MIN_VALUE
        const val FIRST_CAPTURE_INDEX = 0
        const val FUTRONICTECH_THRESHOLD_DIVISOR = 1.291f
        const val DEFAULT_THRESHOLD_DIVISOR = 2.3f
        const val DARKNESS_THRESHOLD_DIVISOR = 1.75f
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
        PendingIntent.FLAG_MUTABLE
    return PendingIntent.getBroadcast(this, 0, intent, flags)
}

private val UsbManager?.supportedDevice: UsbDevice?
    get() = this?.deviceList?.values?.firstOrNull(UsbDevice::isSupportedDevice)

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

private fun withLock(lock: Boolean, onLockChange: (Boolean) -> Unit, action: () -> Unit) {
    if (lock) return
    onLockChange(true)
    action()
    onLockChange(false)
}
