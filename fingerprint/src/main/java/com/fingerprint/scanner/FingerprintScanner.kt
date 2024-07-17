package com.fingerprint.scanner

import android.hardware.usb.UsbDevice
import com.fingerprint.device.FingerprintDeviceInfo
import com.fingerprint.utils.ScannedImageType


internal interface FingerprintScanner {
    val deviceInfo: FingerprintDeviceInfo
    fun tunOffLed()
    fun connect(usbDevice: UsbDevice): Boolean
    fun reconnect(usbDevice: UsbDevice): Boolean
    fun disconnect(): Boolean
    fun verifyPassword(password: ByteArray): Boolean
    fun captureImage(imageType: ScannedImageType): Boolean
    fun getImageData(): ByteArray?
    fun convertImageToBitmapArray(imageData: ByteArray): ByteArray
}
