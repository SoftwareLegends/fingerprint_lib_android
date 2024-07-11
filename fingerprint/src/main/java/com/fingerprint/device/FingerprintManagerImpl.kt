package com.fingerprint.device

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
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
import com.fingerprint.device.FingerprintManagerImpl.Companion.ACTION_USB_PERMISSION
import com.fingerprint.device.FingerprintManagerImpl.Companion.isHfSecurityDevice
import com.fingerprint.scanner.FingerprintScanner
import com.fingerprint.utils.Constants
import com.fingerprint.utils.ScannedImageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch


internal class FingerprintManagerImpl(
    private val scope: CoroutineScope,
    private val context: Context,
    private val fingerprintScanner: FingerprintScanner
) : FingerprintManager {
    override val eventsFlow = MutableStateFlow<FingerprintEvent>(FingerprintEvent.Idle)
    override val captures: MutableList<ImageBitmap> by lazy { mutableStateListOf() }
    override var bestCapture: ImageBitmap? by mutableStateOf(null)
    override var bestCaptureIndex: Int = Int.MIN_VALUE
    private var bestCaptureValue: Int = Int.MIN_VALUE
    private var imageType: ScannedImageType = ScannedImageType.Extra
    private var isCanceled = false
    private var isOpening = false
    private var captureCount = 0
    private var captureIndex = 0
    private var captureTimeout = 0
    private var timeoutCount = Constants.TIMEOUT_LONG
    private var permissionIntent: PendingIntent? = null
    private val usbManager: UsbManager? = context.getSystemService()

    override fun connect() {
        isCanceled = false
        isOpening = false

        registerReceiver()
        requestUsbPermission()
    }

    override fun disconnect() {
        isCanceled = true
        isOpening = false

        runCatching { unregisterReceiver() }
        fingerprintScanner.disconnect()
        emitEvent(FingerprintEvent.Disconnected)
    }

    override fun scan(count: Int): Boolean {
        if (!isOpening) return false
        captures.clear()
        bestCapture = null
        captureCount = count.coerceAtMost(MAX_SCAN_COUNT)
        captureIndex = 0
        captureTimeout = 0
        scope.launch { startProcessing() }
        return true
    }

    private fun emitEvent(message: FingerprintEvent) {
        eventsFlow.value = message
    }

    private fun unregisterReceiver() = context.unregisterReceiver(usbReceiver)

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerReceiver() {
        permissionIntent = context.createPendingIntent()
        val filter = IntentFilter(ACTION_USB_PERMISSION).apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else
            context.registerReceiver(usbReceiver, filter)
    }

    private fun requestUsbPermission() {
        usbManager.supportedDevice?.let { device ->
            if (usbManager!!.hasPermission(device))
                isOpening = fingerprintScanner.reconnect(device).apply {
                    emitEvent(if (this) FingerprintEvent.Connected else FingerprintEvent.ConnectingFailed)
                }
            else synchronized(usbReceiver) {
                usbManager.requestPermission(device, permissionIntent)
            }
        } ?: emitEvent(FingerprintEvent.Idle)
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = when (intent.action) {
            ACTION_USB_PERMISSION -> synchronized(this) {
                val device: UsbDevice = (
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                            intent.getParcelableExtra(
                                UsbManager.EXTRA_DEVICE,
                                UsbDevice::class.java
                            )
                        else
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        ) ?: return@synchronized emitEvent(FingerprintEvent.ConnectingFailed)

                val isGranted = intent.getBooleanExtra(
                    UsbManager.EXTRA_PERMISSION_GRANTED,
                    false
                )

                if (isGranted)
                    isOpening = fingerprintScanner.reconnect(device).apply {
                        emitEvent(if (this) FingerprintEvent.Connected else FingerprintEvent.ConnectingFailed)
                    }
                else
                    emitEvent(FingerprintEvent.ConnectingFailed)
            }

            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                emitEvent(FingerprintEvent.DeviceAttached)
                requestUsbPermission()
            }

            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                emitEvent(FingerprintEvent.DeviceDetached)
                disconnect()
            }

            else -> Unit
        }
    }

    private suspend fun startProcessing() = runCatching {
        if (!isOpening) {
            emitEvent(FingerprintEvent.ConnectingFailed)
            return@runCatching
        }

        captureIndex = 0
        while (isCanceled.not()) {
            processCapture()
            captureIndex++

            if (captureIndex >= captureCount) {
                emitEvent(FingerprintEvent.CapturedSuccessfully)
                break
            }
        }
        isCanceled = false
    }.onFailure { Log.e("DEBUGGING", it.toString()) }

    private fun cancel() {
        isCanceled = true
        captures.clear()
        bestCapture = null
        emitEvent(FingerprintEvent.ProcessCanceledTheFingerLifted)
    }

    private suspend fun processCapture() {
        emitEvent(if (captureIndex == 0) FingerprintEvent.PlaceFinger else FingerprintEvent.KeepFinger)
        if (!captureImage()) return

        if (!uploadAndProcessImage()) return

        if (!generateCharacterImage()) return
    }

    private suspend fun captureImage(): Boolean {
        var timeout = 0

        while (true) {
            if (fingerprintScanner.fetchImageData(imageType)) return true
            else if (captureIndex in 1..captureCount)
                cancel()
            delay(SCAN_DELAY_IN_MILLIS)
            timeout++
            if (timeout > timeoutCount) {
                emitEvent(FingerprintEvent.Timeout)
                return false
            }
            if (isCanceled) return false
        }
    }

    private fun uploadAndProcessImage(): Boolean = runCatching {
        val imageData = fingerprintScanner.uploadImageData()
        if (imageData != null) {
            val bitmapArray = fingerprintScanner.convertImageToBitmapArray(imageData)
            captures.add(bitmapArray.toBitmap())
            findTheBestCapture(bitmapArray)
            emitEvent(FingerprintEvent.NewImage(bitmapArray))
        } else {
            emitEvent(FingerprintEvent.CapturingFailed)
            return false
        }
        return true
    }.getOrDefault(false)

    private fun generateCharacterImage(): Boolean = runCatching {
        if (fingerprintScanner.generateCharacterImage()) return true
        emitEvent(FingerprintEvent.CapturingFailed)
        return false
    }.onFailure { Log.e("DEBUGGING", it.toString()) }
        .getOrDefault(false)

    private fun findTheBestCapture(byteArray: ByteArray) {
        val newValue = byteArray.sum()
        if (newValue > bestCaptureValue) {
            bestCaptureValue = newValue
            bestCaptureIndex = captureIndex
            bestCapture = captures.last()
        }
    }

    companion object {
        const val MAX_SCAN_COUNT = 5
        const val SCAN_DELAY_IN_MILLIS: Long = 100
        const val ACTION_USB_PERMISSION = "com.fingerprint.USB_PERMISSION"

        fun isHfSecurityDevice(vendorId: Int, productId: Int): Boolean = when (vendorId) {
            1107 -> productId == 36869
            8201 -> productId == 30264
            8457 -> productId == 30264
            1155 -> productId in listOf(22304, 22240)
            else -> false
        }
    }
}

private fun UsbDevice.isSupportedDevice(): Boolean =
    isHfSecurityDevice(vendorId = vendorId, productId = productId)

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

private fun ByteArray.toBitmap(): ImageBitmap =
    BitmapFactory.decodeByteArray(this, 0, size).asImageBitmap()
