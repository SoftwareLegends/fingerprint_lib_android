package com.fingerprint.scanner

import android.hardware.usb.UsbDevice
import com.fingerprint.manager.FingerprintDeviceInfo
import com.fingerprint.utils.ScannedImageType


internal interface FingerprintScanner {
    val deviceInfo: FingerprintDeviceInfo
    fun tunOffLed() = Unit
    fun connect(usbDevice: UsbDevice): Boolean
    fun reconnect(usbDevice: UsbDevice): Boolean
    fun disconnect(): Boolean
    fun verifyPassword(password: ByteArray): Boolean = true
    fun captureImage(imageType: ScannedImageType): Boolean
    suspend fun getImageData(): ByteArray?
    fun convertImageToBitmapArray(imageData: ByteArray): ByteArray
}
