package com.fingerprint.communication

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import androidx.core.content.getSystemService
import com.fingerprint.utils.Constants.DEVICE_FAIL
import com.fingerprint.utils.Constants.RETURN_FAIL
import com.fingerprint.utils.Constants.RETURN_OK


internal class DefaultUsbDeviceCommunicatorImpl(
    context: Context
) : UsbDeviceCommunicator {
    private var endpointInMaxSize = 0
    private var endpointOutMaxSize = 0
    private var usbEndpointIn: UsbEndpoint? = null
    private var usbEndpointOut: UsbEndpoint? = null
    private var usbInterface: UsbInterface? = null
    private var usbConnection: UsbDeviceConnection? = null
    private var usbManager: UsbManager? = context.getSystemService()

    override fun closeUsbDevice(): Boolean {
        runCatching { usbConnection?.releaseInterface(usbInterface) }
        usbConnection?.close()
        return true
    }

    override fun openUsbDeviceConnection(usbDevice: UsbDevice): Boolean {
        usbConnection = usbManager?.openDevice(usbDevice) ?: return false

        usbInterface = usbDevice.getInterface(0)
        usbConnection?.claimInterface(usbInterface, true)

        usbInterface?.let { usbInterface ->
            for (i in 0 until usbInterface.endpointCount)
                when (usbInterface.getEndpoint(i).direction) {
                    UsbConstants.USB_DIR_IN -> usbEndpointIn = usbInterface.getEndpoint(i)
                    UsbConstants.USB_DIR_OUT -> usbEndpointOut = usbInterface.getEndpoint(i)
                }
        }

        endpointInMaxSize = usbEndpointIn?.maxPacketSize ?: return false
        endpointOutMaxSize = usbEndpointOut?.maxPacketSize ?: return false
        return true
    }

    override fun sendUsbControlMessage(
        request: Int,
        value: Int,
        buffer: ByteArray,
        index: Int,
        length: Int,
        requestType: Int,
        timeout: Int
    ): Int = usbConnection?.controlTransfer(
        requestType,
        request,
        value,
        index,
        buffer,
        length,
        timeout
    ) ?: RETURN_FAIL

    override fun readUsbBulkData(buffer: ByteArray, length: Int, timeout: Int): Int =
        usbConnection?.bulkTransfer(usbEndpointIn, buffer, length, timeout) ?: RETURN_FAIL

    override fun readUsbDevice(dataBuffer: ByteArray, dataSize: Int, timeout: Int): Int {
        usbConnection ?: return RETURN_FAIL

        val numFullTransfers = dataSize / endpointInMaxSize
        val remainingBytes = dataSize % endpointInMaxSize
        var transferCount = 0
        val tempBuffer = ByteArray(512)

        while (transferCount < numFullTransfers) {
            usbConnection?.bulkTransfer(usbEndpointIn, tempBuffer, endpointInMaxSize, timeout)
            System.arraycopy(
                tempBuffer,
                0,
                dataBuffer,
                transferCount * endpointInMaxSize,
                endpointInMaxSize
            )
            transferCount++
        }

        if (remainingBytes > 0) {
            usbConnection?.bulkTransfer(usbEndpointIn, tempBuffer, remainingBytes, timeout)
            System.arraycopy(
                tempBuffer,
                0,
                dataBuffer,
                transferCount * endpointInMaxSize,
                remainingBytes
            )
        }
        return RETURN_OK
    }

    override fun writeUsbDevice(dataBuffer: ByteArray, dataSize: Int, timeout: Int): Int {
        usbConnection ?: return DEVICE_FAIL

        val numFullTransfers = dataSize / endpointOutMaxSize
        val remainingBytes = dataSize % endpointOutMaxSize
        val tempBuffer = ByteArray(512)
        var transferCount = 0

        while (transferCount < numFullTransfers) {
            System.arraycopy(
                dataBuffer,
                transferCount * endpointOutMaxSize,
                tempBuffer,
                0,
                endpointOutMaxSize
            )
            usbConnection?.bulkTransfer(
                usbEndpointOut,
                tempBuffer,
                endpointOutMaxSize,
                timeout
            )
            transferCount++
        }

        if (remainingBytes > 0) {
            System.arraycopy(
                dataBuffer,
                transferCount * endpointOutMaxSize,
                tempBuffer,
                0,
                remainingBytes
            )
            usbConnection?.bulkTransfer(usbEndpointOut, tempBuffer, remainingBytes, timeout)
        }
        return RETURN_OK
    }
}
