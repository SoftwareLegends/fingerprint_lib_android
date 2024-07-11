package com.fingerprint.communication

import android.hardware.usb.UsbDevice
import com.fingerprint.utils.Constants.TIMEOUT
import com.fingerprint.utils.UsbOperationHelper


internal interface UsbDeviceCommunicator {
    fun closeUsbDevice(): Boolean
    fun openUsbDeviceConnection(usbDevice: UsbDevice): Boolean
    fun sendUsbControlMessage(
        request: Int,
        value: Int,
        buffer: ByteArray,
        index: Int = 0,
        length: Int = 10,
        requestType: Int = UsbOperationHelper.USB_CONTROL_MESSAGE_TYPE,
        timeout: Int = TIMEOUT
    ): Int

    fun readUsbBulkData(buffer: ByteArray, length: Int, timeout: Int): Int
    fun readUsbDevice(dataBuffer: ByteArray, dataSize: Int, timeout: Int): Int
    fun writeUsbDevice(dataBuffer: ByteArray, dataSize: Int, timeout: Int): Int
}
