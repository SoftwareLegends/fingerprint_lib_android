package com.fingerprint.device

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import com.fingerprint.device.FingerprintManagerImpl.Companion.ACTION_USB_PERMISSION
import com.fingerprint.scanner.FingerprintScanner
import com.fingerprint.scanner.FutronictechFingerprintScanner
import com.fingerprint.scanner.FutronictechFingerprintScanner.Companion.isFutronicDevice
import com.fingerprint.scanner.HfSecurityFingerprint.Companion.isHfSecurityDevice
import com.fingerprint.utils.ScannedImageType
import com.fingerprint.utils.returnUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch


internal class FingerprintManagerImpl(
    private val scope: CoroutineScope,
    private val context: Context,
    private val lifecycle: Lifecycle,
    private val initializeFingerprintScanner: ()-> FingerprintScanner
) : FingerprintManager {
    private var fingerprintScanner: FingerprintScanner = initializeFingerprintScanner()
    override var progress: Float = 0f
    override val eventsFlow = MutableStateFlow<FingerprintEvent>(FingerprintEvent.Idle)
    override val captures: MutableList<ImageBitmap> by lazy { mutableStateListOf() }
    override var bestCapture: ImageBitmap? by mutableStateOf(null)
    override var bestCaptureIndex: Int = Int.MIN_VALUE
    override val deviceInfo: FingerprintDeviceInfo
        get() = fingerprintScanner.deviceInfo

    private var bestCaptureValue: Int = Int.MIN_VALUE
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
        if (isConnected) fingerprintScanner.tunOffLed()
    }

    override fun disconnect() {
        isCanceled = true
        isConnected = false
        unregisterReceiver()
        fingerprintScanner.disconnect()
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
        bestCapture = captures[bestCaptureIndex]
    }.onFailure { Log.e("DEBUGGING -> startProcessing() -> ", it.toString()) }

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
        bestCaptureValue = Int.MIN_VALUE
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
        while (true) {
            delay(SCAN_DELAY_IN_MILLIS)
            if (fingerprintScanner.captureImage(imageType)) return true
            if (captureIndex in 1..captureCount) onFingerLiftDuringScanning()
            if (isCanceled) return false
        }
    }

    private suspend fun getImageData(): Boolean = runCatching {
        val imageData = fingerprintScanner.getImageData()
        if (imageData != null) {
            val bitmapArray = fingerprintScanner.convertImageToBitmapArray(imageData)
            findTheBestCapture(bitmapArray)
            captures.add(bitmapArray.toBitmap())
            eventsFlow.emit(FingerprintEvent.NewImage(bitmapArray))
            delay(SCAN_DELAY_IN_MILLIS)
        } else {
            eventsFlow.tryEmit(FingerprintEvent.CapturingFailed)
            return false
        }
        return true
    }.onFailure { Log.e("DEBUGGING -> getImageData() -> ", it.toString()) }
        .getOrDefault(false)

    private fun findTheBestCapture(byteArray: ByteArray) {
        val newValue = byteArray.sum()
        if (newValue > bestCaptureValue) {
            bestCaptureValue = newValue
            bestCaptureIndex = captureIndex
        }
    }

    companion object {
        const val MAX_SCAN_COUNT = 5
        const val SCAN_DELAY_IN_MILLIS: Long = 50
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

private fun ByteArray.toBitmap(): ImageBitmap {
    val config = Bitmap.Config.RGB_565
    val bitmap: Bitmap = runCatching {
        BitmapFactory.decodeByteArray(this, 0, size) ?: error("Bitmap is null")
    }.getOrElse {
        val imageWidth = FutronictechFingerprintScanner.IMAGE_WIDTH
        val imageHeight = FutronictechFingerprintScanner.IMAGE_HEIGHT
        val pixels = IntArray(imageWidth * imageHeight)

        for (i in indices) {
            val pixelValue = this[i].toInt() and 0xFF
            val color = Color.rgb(pixelValue, pixelValue, pixelValue)
            pixels[i] = color
        }

        Bitmap.createBitmap(pixels, imageWidth, imageHeight, config)
    }

    return bitmap.applyFilters(config).asImageBitmap()
}

private fun Bitmap.applyFilters(config: Bitmap.Config): Bitmap {
    val contrast = 1.75f
    val contrastMatrix = ColorMatrix().apply {
        setScale(contrast, contrast, contrast, 1f)
        setSaturation(0f)
    }

    val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(contrastMatrix) }
    val enhancedBitmap = Bitmap.createBitmap(width, height, config)
    val canvas = Canvas(enhancedBitmap)
    canvas.drawBitmap(this, 0f, 0f, paint)
    return enhancedBitmap
}
