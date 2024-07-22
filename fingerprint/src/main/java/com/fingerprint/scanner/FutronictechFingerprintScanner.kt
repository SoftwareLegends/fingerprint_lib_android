@file:Suppress("unused")

package com.fingerprint.scanner

import android.content.Context
import android.hardware.usb.UsbDevice
import android.util.Log
import com.fingerprint.communicator.DefaultUsbDeviceCommunicatorImpl
import com.fingerprint.communicator.UsbDeviceCommunicator
import com.fingerprint.manager.FingerprintDeviceInfo
import com.fingerprint.utils.ScannedImageType
import com.futronictech.Scanner
import kotlinx.coroutines.delay


internal class FutronictechFingerprintScanner(
    private val context: Context,
    usbDeviceCommunicator: UsbDeviceCommunicator
) : FingerprintScanner {
    private lateinit var scanner: Scanner
    private var device: UsbDevice? = null
    private val usbDeviceCommunicator = usbDeviceCommunicator as? DefaultUsbDeviceCommunicatorImpl
    private var transferBuffer: ByteArray = ByteArray(TRANSFER_BUFFER_SIZE)
    override val deviceInfo: FingerprintDeviceInfo
        get() = FingerprintDeviceInfo(
            vendorId = device?.vendorId,
            productId = device?.productId,
            model = "FS80H",
            product = device?.productName,
            manufacturer = device?.manufacturerName
        )


    override fun connect(usbDevice: UsbDevice): Boolean {
        usbDeviceCommunicator?.openUsbDeviceConnection(usbDevice)
        this.device = usbDevice
        return true
    }

    override fun reconnect(usbDevice: UsbDevice): Boolean {
        disconnect()
        return connect(usbDevice)
    }

    override fun disconnect() =
        synchronized(this) {
            usbDeviceCommunicator?.usbConnection?.run {
                releaseInterface(usbDeviceCommunicator.usbInterface)
                close()
            } != null
        }

    override fun captureImage(imageType: ScannedImageType): Boolean {
        scanner = Scanner(context.cacheDir)
        val scannerInitialized = initializeScanner()
        if (scanner.isSyncDirInitialized.not())
            return false

        if (initializeScannerOptions().not() || scannerInitialized.not())
            return false
        return true
    }

    private fun initializeScanner(): Boolean {
        val openDeviceSuccess = scanner.openDeviceOnInterfaceUsbHost(this)
        return !(!openDeviceSuccess || !scanner.getImageSize())
    }

    private fun initializeScannerOptions(): Boolean {
        val invertImage = true
        val mask = Scanner.FTR_OPTIONS_DETECT_FAKE_FINGER or Scanner.FTR_OPTIONS_INVERT_IMAGE
        val flag = (if (invertImage) Scanner.FTR_OPTIONS_INVERT_IMAGE else 0)
        return scanner.setOptions(mask, flag)
    }

    override fun convertImageToBitmapArray(imageData: ByteArray): ByteArray = imageData

    override suspend fun getImageData(): ByteArray {
        val imageData = ByteArray(IMAGE_SIZE)
        while (true) {
            val result = scanner.getFrame(imageData)
            if (result) {
                scanner.closeDevice()
                return imageData
            }
            delay(150)
        }
    }

    @JvmName("DataExchange")
    @Suppress("UNUSED_PARAMETER")
    fun dataExchange(
        outData: ByteArray,
        inData: ByteArray,
        inTimeOut: Int,
        outTimeOut: Int,
        keepOpen: Boolean,
        useMaxEndPointSize: Boolean,
        traceLevel: Int
    ): Boolean {
        synchronized(this) {
            if (usbDeviceCommunicator?.writeUsbDevice(outData, outData.size, outTimeOut) == -1) {
                Log.e(LOG_TAG, String.format("Send %d bytes failed", outData.size))
                return false
            }

            var toReadSize = inData.size
            var copyPos = 0
            while (toReadSize > 0) {
                usbDeviceCommunicator?.run {
                    if (
                        usbConnection?.bulkTransfer(
                            usbEndpointIn,
                            transferBuffer,
                            if (useMaxEndPointSize)
                                usbEndpointIn!!.maxPacketSize
                            else
                                toReadSize,
                            inTimeOut
                        )
                        == -1
                    ) {
                        Log.e(LOG_TAG, String.format("Receive(3) %d bytes failed", toReadSize))
                        return false
                    }
                    val real_read =
                        if (toReadSize > usbEndpointIn!!.maxPacketSize)
                            usbEndpointIn!!.maxPacketSize
                        else
                            toReadSize

                    if (copyPos + real_read > inData.size) {
                        Log.e(
                            LOG_TAG,
                            String.format(
                                "Small receive buffer. Need %d bytes",
                                (copyPos + real_read) - inData.size
                            )
                        )
                        return false
                    }
                    System.arraycopy(transferBuffer, 0, inData, copyPos, real_read)
                    toReadSize -= real_read
                    copyPos += real_read
                }
            }
            return true
        }
    }

    @JvmName("ValidateContext")
    fun validateContext(): Boolean {
        synchronized(this) {
            return  usbDeviceCommunicator?.run {
                !(usbInterface == null || (usbConnection == null) || (usbEndpointIn == null) || (usbEndpointOut == null))
            } ?: false
        }
    }

    @JvmName("DataExchangeEnd")
    fun dataExchangeEnd() {
        synchronized(this) {
            usbDeviceCommunicator?.usbConnection?.releaseInterface(usbDeviceCommunicator.usbInterface)
        }
    }

    @JvmName("GetDeviceInfo")
    fun getDeviceInfo(packData: ByteArray): Boolean {
        var vendorId = 0
        var packDataIndex = 0
        synchronized(this) {
            runCatching {
                vendorId = device?.vendorId ?: error("vendorId")
                packDataIndex = 0 + 1
            }
            return runCatching {
                packDataIndex = packData.insertAt(vendorId, 0)

                val productId = device?.productId ?: error("productId")
                packDataIndex = packData.insertAt(productId, packDataIndex)

                val sn = usbDeviceCommunicator?.usbConnection?.serial
                if (sn != null) {
                    packData[packDataIndex] = 1
                    packDataIndex += 1
                    val stringBytes = sn.toByteArray()
                    val snSize = stringBytes.size
                    packDataIndex = packData.insertAt(snSize, packDataIndex)
                    System.arraycopy(
                        stringBytes,
                        0,
                        packData,
                        packDataIndex,
                        stringBytes.size
                    )
                    packDataIndex += stringBytes.size
                } else {
                    packData[packDataIndex] = 0
                    packDataIndex += 1
                }
                return true
            }.onFailure {
                Log.e(LOG_TAG, "Get device info failed: $it")
            }.getOrDefault(false)
        }
    }

    companion object {
        const val IMAGE_WIDTH = 320
        const val IMAGE_HEIGHT = 480
        const val IMAGE_SIZE = IMAGE_WIDTH * IMAGE_HEIGHT
        const val LOG_TAG: String = "DEBUGGING"
        const val TRANSFER_BUFFER_SIZE: Int = 4096

        fun isFutronicDevice(vendorId: Int, productId: Int): Boolean = when (vendorId) {
            2100 -> productId == 32
            2392 -> productId == 775
            8122 -> productId in listOf(18, 19, 39)
            5265 -> productId in listOf(32, 37, 136, 144, 80, 96, 152, 32920, 39008)
            else -> false
        }
    }
}

fun ByteArray.insertAt(value: Int, index: Int): Int {
    this[index] = value.toByte()
    this[index + 1] = (value shr 8).toByte()
    this[index + 2] = (value shr 16).toByte()
    this[index + 3] = (value shr 24).toByte()
    return index + 4
}
