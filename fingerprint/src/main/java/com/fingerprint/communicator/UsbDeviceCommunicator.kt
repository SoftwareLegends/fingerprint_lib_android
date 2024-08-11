package com.fingerprint.communicator

import android.hardware.usb.UsbDevice
import com.fingerprint.utils.Constants.DEFAULT_TIMEOUT
import com.fingerprint.utils.UsbOperationHelper


internal interface UsbDeviceCommunicator {
    fun closeUsbDevice(): Boolean
    fun openUsbDeviceConnection(usbDevice: UsbDevice): Boolean
    fun sendUsbControlMessage(
        request: Int,
        value: Int,
        buffer: ByteArray,
        index: Int = 0,
        length: Int = buffer.size,
        requestType: Int = UsbOperationHelper.USB_CONTROL_MESSAGE_TYPE,
        timeout: Int = DEFAULT_TIMEOUT
    ): Int

    fun readUsbBulkData(buffer: ByteArray, length: Int, timeout: Int = DEFAULT_TIMEOUT): Int
    fun readUsbDevice(dataBuffer: ByteArray, dataSize: Int, timeout: Int = DEFAULT_TIMEOUT): Int
    fun writeUsbDevice(dataBuffer: ByteArray, dataSize: Int, timeout: Int = DEFAULT_TIMEOUT): Int
}
