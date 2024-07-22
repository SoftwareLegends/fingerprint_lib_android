package com.fingerprint.scanner

import android.hardware.usb.UsbDevice
import android.util.Log
import com.fingerprint.communication.UsbDeviceCommunicator
import com.fingerprint.device.FingerprintDeviceInfo
import com.fingerprint.utils.ScannedImageType
import com.fingerprint.utils.Constants.DATA_PACKET
import com.fingerprint.utils.UsbOperationHelper.CSW_LENGTH
import com.fingerprint.utils.UsbOperationHelper.CSW_SIGNATURE_INDEX
import com.fingerprint.utils.UsbOperationHelper.CSW_SIGNATURE_OK
import com.fingerprint.utils.UsbOperationHelper.CSW_STATUS_INDEX
import com.fingerprint.utils.UsbOperationHelper.EMPTY_BYTE
import com.fingerprint.utils.UsbOperationHelper.createCommandBlockWrapper
import com.fingerprint.utils.UsbOperationHelper.intToByteArray
import com.fingerprint.utils.Constants.BMP_DESTINATION_OFFSET
import com.fingerprint.utils.Constants.END_DATA_PACKET
import com.fingerprint.utils.Constants.FILL_PACKAGE_COMMAND
import com.fingerprint.utils.Constants.MAX_PACKAGE_SIZE
import com.fingerprint.utils.Constants.RESPONSE_PACKET
import com.fingerprint.utils.Constants.RETURN_FAIL
import com.fingerprint.utils.Constants.TIMEOUT
import com.fingerprint.utils.Constants.GENERAL_SEND_PACKAGE_ADDRESS
import com.fingerprint.utils.Constants.VERIFY_PASSWORD_COMMAND
import com.fingerprint.utils.DeviceFailException
import com.fingerprint.utils.returnUnit


internal class HfSecurityFingerprint(
    private val usbDeviceCommunicator: UsbDeviceCommunicator
) : FingerprintScanner {
    private var deviceType: Int = 0
    private var imageType: ScannedImageType = ScannedImageType.Normal
    private var device: UsbDevice? = null
    override val deviceInfo: FingerprintDeviceInfo
        get() = FingerprintDeviceInfo(
            vendorId = device?.vendorId,
            productId = device?.productId,
            model = if (deviceType in 1..2) "HF4000" else "Unknown",
            product = device?.productName,
            manufacturer = device?.manufacturerName
        )

    override fun tunOffLed() = captureImage(imageType).returnUnit()

    override fun connect(usbDevice: UsbDevice): Boolean {
        device = usbDevice
        if (usbDeviceCommunicator.openUsbDeviceConnection(usbDevice).not()) return false

        val passwordOptions = listOf(
            byteArrayOf(0x78, 0x70, 0x62, 0x65),
            byteArrayOf(0x78, 0x69, 0x61, 0x6f),
            ByteArray(4)
        )

        for (password in passwordOptions) runCatching {
            if (verifyPassword(password)) return true
        }.onFailure { Log.e("DEBUGGING", "connect() -> $it") }
        return false
    }

    override fun reconnect(usbDevice: UsbDevice): Boolean {
        disconnect()
        return if (initializeUsbDeviceType(usbDevice))
            connect(usbDevice)
        else false
    }

    override fun disconnect(): Boolean = usbDeviceCommunicator.closeUsbDevice()

    override fun convertImageToBitmapArray(imageData: ByteArray): ByteArray {
        val bmpHeader: Byte = 14
        val dibHeader: Byte = 40
        val headerSize: Byte = (bmpHeader + dibHeader).toByte()
        val bitmapArray = ByteArray(imageType.size).apply {
            this[0] = 'B'.code.toByte()
            this[1] = 'M'.code.toByte()
            this[10] = headerSize
            this[11] = 4
            this[14] = dibHeader
            this[26] = 1
            this[28] = 8
        }

        val imageWidthBytes = intToByteArray(imageType.imageWidth)
        val imageHeightBytes = intToByteArray(imageType.imageHeight)

        System.arraycopy(imageWidthBytes, 0, bitmapArray, 18, 4)
        System.arraycopy(imageHeightBytes, 0, bitmapArray, 22, 4)

        for ((j, i) in (headerSize until BMP_DESTINATION_OFFSET step 4).withIndex()) {
            bitmapArray[i] = j.toByte() // BLUE
            bitmapArray[i + 1] = j.toByte() // GREEN
            bitmapArray[i + 2] = j.toByte() // RED
        }

        // Copying buffer
        for (i in 0 until (imageType.size - BMP_DESTINATION_OFFSET))
            bitmapArray[BMP_DESTINATION_OFFSET + i] = imageData[i]
        return bitmapArray
    }

    override suspend fun getImageData(): ByteArray? {
        val imageData = ByteArray(imageType.size)
        val result = if (imageType == ScannedImageType.Normal)
            getImageData(imageData)
        else
            getImageDataExtra(imageData)
        return if (result) imageData else null
    }

    override fun captureImage(imageType: ScannedImageType): Boolean = runCatching {
        this.imageType = imageType
        val command = ByteArray(10).apply { this[0] = imageType.captureCommand }
        val sendData = ByteArray(MAX_PACKAGE_SIZE)
        val receiveData = ByteArray(MAX_PACKAGE_SIZE)

        fillPackage(sendData, FILL_PACKAGE_COMMAND.toInt(), 1, command)

        if (!sendPackage(GENERAL_SEND_PACKAGE_ADDRESS, sendData)) return false

        if (!receivePackage(receiveData, 64, TIMEOUT)) throw DeviceFailException()

        return verifyResponsePackage(receiveData)
    }.onFailure { Log.e("DEBUGGING", "captureImage() -> $it") }
        .getOrDefault(false)

    private fun initializeUsbDeviceType(device: UsbDevice): Boolean {
        deviceType = when {
            (device.vendorId == 1107) && (device.productId == 36869) -> 0
            (device.vendorId in listOf(8201, 8457)) && (device.productId == 30264) -> 1
            (device.vendorId == 1155) && (device.productId == 22304) -> 2
            else -> return false
        }
        return true
    }

    private fun sendUsbData(dataBuffer: ByteArray, length: Int): Int = when (deviceType) {
        0 -> {
            val buffer = ByteArray(10)
            usbDeviceCommunicator.sendUsbControlMessage(
                request = 0,
                value = length,
                buffer = buffer
            )
            usbDeviceCommunicator.writeUsbDevice(dataBuffer, length, TIMEOUT)
        }
        // HF4000
        1, 2 -> sendUsbDataForTypeOneAndTwo(length, dataBuffer)
        else -> RETURN_FAIL
    }

    private fun sendUsbDataForTypeOneAndTwo(length: Int, dataBuffer: ByteArray): Int {
        val commandStatusWrapper = ByteArray(CSW_LENGTH)
        val commandBlockWrapper = createCommandBlockWrapper(
            length = length,
            isDataOut = true
        )

        if (usbDeviceCommunicator.writeUsbDevice(
                commandBlockWrapper,
                31,
                TIMEOUT
            ) != 0
        ) return RETURN_FAIL
        if (usbDeviceCommunicator.writeUsbDevice(
                dataBuffer,
                length,
                TIMEOUT
            ) != 0
        ) return RETURN_FAIL

        val transferResult = usbDeviceCommunicator.readUsbDevice(commandStatusWrapper, 13, TIMEOUT)
        if (commandStatusWrapper[CSW_SIGNATURE_INDEX] != CSW_SIGNATURE_OK
            || commandStatusWrapper[CSW_STATUS_INDEX] != EMPTY_BYTE
        ) return RETURN_FAIL
        commandStatusWrapper[3] = 0x43

        if (deviceType == 1)
            for (i in 0..<12)
                if (commandStatusWrapper[i] != commandBlockWrapper[i])
                    return RETURN_FAIL
        return transferResult
    }

    private fun receiveUsbData(
        dataBuffer: ByteArray,
        length: Int,
        timeout: Int = TIMEOUT
    ): Boolean =
        when (deviceType) {
            0 -> {
                val buffer = ByteArray(10)
                usbDeviceCommunicator.sendUsbControlMessage(
                    request = 1,
                    value = length,
                    buffer = buffer
                )

                usbDeviceCommunicator.readUsbBulkData(dataBuffer, length, TIMEOUT) >= 0
            }

            1, 2 -> receiveUsbDataForTypeOneAndTwo(length, timeout, dataBuffer)
            else -> false
        }

    private fun receiveUsbDataForTypeOneAndTwo(
        length: Int,
        timeout: Int,
        dataBuffer: ByteArray
    ): Boolean {
        val commandStatusWrapper = ByteArray(CSW_LENGTH)
        val commandBlockWrapper = createCommandBlockWrapper(
            length = length,
            isDataOut = false
        )

        if (usbDeviceCommunicator.writeUsbDevice(
                commandBlockWrapper,
                31,
                timeout
            ) < 0
        ) return false
        if (usbDeviceCommunicator.readUsbDevice(dataBuffer, length, timeout) < 0) return false

        val transferResult = usbDeviceCommunicator.readUsbDevice(commandStatusWrapper, 13, timeout)
        if (commandStatusWrapper[CSW_SIGNATURE_INDEX] != CSW_SIGNATURE_OK
            || commandStatusWrapper[CSW_STATUS_INDEX] != EMPTY_BYTE
        ) return false

        if (deviceType == 1)
            for (i in 4..<8)
                if (commandStatusWrapper[i] != commandBlockWrapper[i])
                    return false
        return transferResult >= 0
    }

    private fun receiveUsbImage(dataBuffer: ByteArray, length: Int): Boolean {
        when (deviceType) {
            0 -> {
                val buffer = ByteArray(10)
                val n = 8
                val len = length / n
                val tmp = ByteArray(len)

                var result = usbDeviceCommunicator.sendUsbControlMessage(
                    request = 1,
                    value = length,
                    index = 0,
                    buffer = buffer,
                    length = 10,
                )

                for (k in 0..<8) {
                    result = usbDeviceCommunicator.readUsbBulkData(tmp, len, TIMEOUT)
                    val t = len * k
                    for (i in 0..<len)
                        dataBuffer[t + i] = tmp[i]
                }
                return result >= 0
            }
            // HF4000
            1, 2 -> {
                var result = false
                val n = 8
                val len = length / n
                val tmp = ByteArray(len)

                for (k in 0..<n) {
                    result = receiveUsbData(tmp, len, TIMEOUT)
                    val t = len * k
                    for (i in 0..<len)
                        dataBuffer[t + i] = tmp[i]
                }
                return result
            }
        }
        return false
    }

    private fun encodeData(
        address: Int,
        sourceData: ByteArray,
        sourceLength: Int,
        destinationData: ByteArray,
        destinationLength: IntArray
    ): Boolean {
        if (sourceLength > MAX_PACKAGE_SIZE - 4)
            return false

        destinationData[0] = 0xEF.toByte()
        destinationData[1] = 0x01.toByte()
        destinationData[2] = ((address shr 24) and 0xff).toByte()
        destinationData[3] = ((address shr 16) and 0xff).toByte()
        destinationData[4] = ((address shr 8) and 0xff).toByte()
        destinationData[5] = (address and 0xff).toByte()

        var i = 0
        var destIndex = 6
        var checksum = 0
        while (i < sourceLength - 2) {
            checksum += sourceData[i].toInt()
            destinationData[destIndex++] = (sourceData[i])
            i++
        }

        val checksumLow = checksum and 0xff
        val checksumHigh = checksum shr 8 and 0xff
        destinationData[destIndex++] = checksumHigh.toByte()
        destinationData[destIndex++] = checksumLow.toByte()
        destinationLength[0] = sourceLength + 6
        return true
    }

    private fun decodeData(
        sourceData: ByteArray,
        destinationData: ByteArray,
        destinationLength: IntArray
    ): Boolean {
        val tag1 = (if (sourceData[0] >= 0) sourceData[0].toInt() else sourceData[0] + 256)
        val tag2 = (if (sourceData[1] >= 0) sourceData[1].toInt() else sourceData[1] + 256)
        if (tag1 != 0xEF || tag2 != 0x01) return false

        val highByte = (if (sourceData[7] >= 0) sourceData[7].toInt() else sourceData[7] + 256)
        val lowByte = (if (sourceData[8] >= 0) sourceData[8].toInt() else sourceData[8] + 256)
        val length = ((highByte shl 8) and 0xff00) + (lowByte) + 1

        for (i in 0 until length) destinationData[i] = (sourceData[i + 6])

        destinationLength[0] = length
        return true
    }

    private fun receivePackage(dataBuffer: ByteArray, length: Int, timeout: Int): Boolean {
        val receiveBuffer = ByteArray(1024)
        val decodedLength = IntArray(1)
        val configuredTimeout = if (timeout == 0) TIMEOUT else timeout
        if (receiveUsbData(receiveBuffer, length, configuredTimeout).not()) return false
        if (decodeData(receiveBuffer, dataBuffer, decodedLength).not()) return false
        return true
    }

    private fun getPackageLength(dataBuffer: ByteArray): Int =
        dataBuffer[1] * 256 + dataBuffer[2] + 1 + 2

    private fun sendPackage(address: Int, dataBuffer: ByteArray): Boolean {
        val encodedLength = IntArray(1)
        val encodedBuffer = ByteArray(MAX_PACKAGE_SIZE + 20)

        val packageLength = getPackageLength(dataBuffer)
        if (packageLength > MAX_PACKAGE_SIZE) return false

        if (!encodeData(
                address,
                dataBuffer,
                packageLength,
                encodedBuffer,
                encodedLength
            )
        ) return false

        if (encodedLength[0] > MAX_PACKAGE_SIZE) return false

        if (sendUsbData(encodedBuffer, encodedLength[0]) != 0) return false
        return true
    }

    private fun fillPackage(
        dataBuffer: ByteArray,
        packageType: Int,
        length: Int,
        contentBuffer: ByteArray
    ): Int {
        if (length < 0 || length > MAX_PACKAGE_SIZE) return 0
        if (packageType.toByte() !in listOf(
                FILL_PACKAGE_COMMAND,
                DATA_PACKET,
                END_DATA_PACKET
            )
        ) return 0

        var lengthModified = length + 2
        dataBuffer[0] = packageType.toByte()
        dataBuffer[1] = ((lengthModified shr 8) and 0xff).toByte()
        dataBuffer[2] = (lengthModified and 0xff).toByte()

        for (i in 0 until lengthModified)
            dataBuffer[3 + i] = contentBuffer[i]

        lengthModified += 3
        return lengthModified
    }

    private fun verifyResponsePackage(dataBuffer: ByteArray): Boolean {
        val packageType = dataBuffer[0]
        return if (packageType == RESPONSE_PACKET)
            (dataBuffer[3].toInt() == 0).also { println("DEBUGGING -> $it") }
        else
            false
    }

    private fun getImageData(
        imageData: ByteArray,
        imageType: ScannedImageType = ScannedImageType.Normal
    ): Boolean {
        val command = ByteArray(10).apply {
            this[0] = imageType.getCommand
        }

        val sendData = ByteArray(MAX_PACKAGE_SIZE)
        fillPackage(sendData, FILL_PACKAGE_COMMAND.toInt(), 1, command)

        if (sendPackage(GENERAL_SEND_PACKAGE_ADDRESS, sendData).not()) return false

        return receiveUsbImage(imageData, imageType.size)
    }

    private fun getImageDataExtra(imageData: ByteArray): Boolean = getImageData(
        imageData = imageData,
        imageType = ScannedImageType.Extra
    )

    override fun verifyPassword(password: ByteArray): Boolean {
        val content = ByteArray(10)
        val sendData = ByteArray(MAX_PACKAGE_SIZE)
        val receiveData = ByteArray(MAX_PACKAGE_SIZE)

        content[0] = VERIFY_PASSWORD_COMMAND
        content[1] = password[0]
        content[2] = password[1]
        content[3] = password[2]
        content[4] = password[3]

        fillPackage(sendData, FILL_PACKAGE_COMMAND.toInt(), 5, content)

        if (!sendPackage(GENERAL_SEND_PACKAGE_ADDRESS, sendData)) return false
        if (!receivePackage(receiveData, 64, 1000)) throw DeviceFailException()
        return verifyResponsePackage(receiveData)
    }

    companion object {
        fun isHfSecurityDevice(vendorId: Int, productId: Int): Boolean = when (vendorId) {
            1107 -> productId == 36869
            8201 -> productId == 30264
            8457 -> productId == 30264
            1155 -> productId in listOf(22304, 22240)
            else -> false
        }
    }
}
