package com.fingerprint.scanner

import android.hardware.usb.UsbDevice
import com.fingerprint.manager.FingerprintDeviceInfo
import com.fingerprint.utils.Constants.DEFAULT_BRIGHTNESS_THRESHOLD
import com.fingerprint.utils.ScannedImageType
import com.fingerprint.utils.getPixelBrightness


internal interface FingerprintScanner {
    val deviceInfo: FingerprintDeviceInfo
    fun turnOffLed() = Unit
    fun connect(usbDevice: UsbDevice): Boolean
    fun reconnect(usbDevice: UsbDevice): Boolean
    fun disconnect(): Boolean
    fun verifyPassword(password: ByteArray): Boolean = true
    fun captureImage(imageType: ScannedImageType): Boolean
    suspend fun getImageBytes(): ByteArray?
    suspend fun isCleanRequired(): Boolean

    suspend fun FingerprintScanner.getBrightness(): Float = runCatching {
        var sum = 0f
        val array = getImageBytes() ?: return 0f
        for (i in array.indices step 4) {
            val brightness = array.getPixelBrightness(i)
            if (brightness <= DEFAULT_BRIGHTNESS_THRESHOLD)
                sum += brightness
        }
        return sum
    }.getOrDefault(0f)
}
