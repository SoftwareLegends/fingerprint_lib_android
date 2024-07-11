package com.fingerprint.scanner

import android.hardware.usb.UsbDevice
import com.fingerprint.utils.ScannedImageType


internal interface FingerprintScanner {
    fun connect(usbDevice: UsbDevice): Boolean
    fun reconnect(usbDevice: UsbDevice): Boolean
    fun disconnect(): Boolean
    fun verifyPassword(password: ByteArray): Boolean
    fun generateCharacterImage(): Boolean
    fun fetchImageData(imageType: ScannedImageType): Boolean
    fun uploadImageData(): ByteArray?
    fun convertImageToBitmapArray(imageData: ByteArray): ByteArray
}
