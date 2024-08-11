package com.fingerprint.scanner

import android.graphics.Bitmap
import android.hardware.usb.UsbDevice
import android.util.Log
import com.fingerprint.communicator.UsbDeviceCommunicator
import com.fingerprint.manager.FingerprintDeviceInfo
import com.fingerprint.utils.Constants.DATA_PACKET
import com.fingerprint.utils.Constants.END_DATA_PACKET
import com.fingerprint.utils.Constants.FILL_PACKAGE_COMMAND
import com.fingerprint.utils.Constants.GENERAL_SEND_PACKAGE_ADDRESS
import com.fingerprint.utils.Constants.MAX_PACKAGE_SIZE
import com.fingerprint.utils.Constants.RECEIVED_PACKAGE_LENGTH
import com.fingerprint.utils.Constants.RECEIVED_PACKAGE_TIMEOUT
import com.fingerprint.utils.Constants.RESPONSE_PACKET
import com.fingerprint.utils.Constants.RETURN_FAIL
import com.fingerprint.utils.Constants.DEFAULT_TIMEOUT
import com.fingerprint.utils.Constants.RECEIVE_CONTROL_MESSAGE_REQUEST
import com.fingerprint.utils.Constants.SEND_CONTROL_MESSAGE_REQUEST
import com.fingerprint.utils.Constants.VERIFY_PASSWORD_COMMAND
import com.fingerprint.utils.DeviceFailException
import com.fingerprint.utils.ScannedImageType
import com.fingerprint.utils.UsbOperationHelper.CSW_LENGTH
import com.fingerprint.utils.UsbOperationHelper.CSW_SIGNATURE_INDEX
import com.fingerprint.utils.UsbOperationHelper.CSW_SIGNATURE_OK
import com.fingerprint.utils.UsbOperationHelper.CSW_STATUS_INDEX
import com.fingerprint.utils.UsbOperationHelper.EMPTY_BYTE
import com.fingerprint.utils.UsbOperationHelper.createCommandBlockWrapper
import com.fingerprint.utils.applyFilters
import com.fingerprint.utils.convertImageDataToBitmapArray
import com.fingerprint.utils.isEqual
import com.fingerprint.utils.removeQuestionMark
import com.fingerprint.utils.returnUnit


internal class HfSecurityFingerprintScanner(
    private val usbDeviceCommunicator: UsbDeviceCommunicator
) : FingerprintScanner {
    var deviceType: DeviceType = DeviceType.Unknown
        private set
    private var imageType: ScannedImageType = ScannedImageType.Normal
    private var device: UsbDevice? = null

    override val deviceInfo: FingerprintDeviceInfo
        get() = FingerprintDeviceInfo(
            model = deviceType.name,
            vendorId = device?.vendorId,
            productId = device?.productId,
            product = device?.productName.removeQuestionMark(),
            manufacturer = device?.manufacturerName.removeQuestionMark()
        )

    override fun turnOffLed() = captureImage(imageType).returnUnit()

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

    override suspend fun getImageBytes(): ByteArray? {
        val imageData = ByteArray(imageType.size)
        val isSuccess = if (imageType == ScannedImageType.Normal)
            getImageData(imageData = imageData)
        else
            getImageDataExtra(imageData = imageData)
        return if (isSuccess) imageData.convertImageDataToBitmapArray(
            height = imageType.imageHeight,
            width = imageType.imageWidth,
            config = Bitmap.Config.ARGB_8888,
            applyFilters = Bitmap::applyFilters
        ) else null
    }

    override suspend fun isCleanRequired(): Boolean =
        getBrightness().also {
            Log.i(
                "DEBUGGING",
                "BRIGHTNESS -> $it"
            )
        } in MIN_CLEAN_REQUIRED_BRIGHTNESS_THRESHOLD..MAX_CLEAN_REQUIRED_BRIGHTNESS_THRESHOLD

    override fun captureImage(imageType: ScannedImageType): Boolean = runCatching {
        this.imageType = if (deviceType == DeviceType.HF4000_V1)
            ScannedImageType.Normal
        else
            imageType

        val sendData = ByteArray(MAX_PACKAGE_SIZE)
        val receiveData = ByteArray(MAX_PACKAGE_SIZE)
        val content = ByteArray(10).also { array -> array[0] = this.imageType.captureCommand }

        fillPackage(dataBuffer = sendData, length = 1, contentBuffer = content)

        if (!sendPackage(dataBuffer = sendData)) return false

        if (!receivePackage(
                dataBuffer = receiveData,
                timeout = DEFAULT_TIMEOUT
            )
        ) throw DeviceFailException()

        return verifyResponsePackage(dataBuffer = receiveData)
    }.onFailure { Log.e("DEBUGGING", "captureImage() -> $it") }
        .getOrDefault(false)

    private fun initializeUsbDeviceType(device: UsbDevice): Boolean {
        deviceType = when {
            (device.vendorId == 1107) && (device.productId == 36869) -> DeviceType.OtherTypes
            device.vendorId in listOf(
                8201,
                8457
            ) && (device.productId == 30264) -> DeviceType.HF4000_V1

            (device.vendorId == 1155) && (device.productId == 22304) -> DeviceType.HF4000_V2
            else -> return false
        }
        return true
    }

    private fun sendUsbData(dataBuffer: ByteArray, length: Int): Int = when (deviceType) {
        DeviceType.HF4000_V1,
        DeviceType.HF4000_V2 -> sendUsbDataForHF4000(length = length, dataBuffer = dataBuffer)

        DeviceType.OtherTypes -> sendUsbDataForOtherTypes(length = length, dataBuffer = dataBuffer)

        else -> RETURN_FAIL
    }

    private fun sendUsbDataForOtherTypes(length: Int, dataBuffer: ByteArray): Int {
        usbDeviceCommunicator.sendUsbControlMessage(
            request = SEND_CONTROL_MESSAGE_REQUEST,
            value = length,
            buffer = ByteArray(10)
        )
        return usbDeviceCommunicator.writeUsbDevice(
            dataSize = length,
            dataBuffer = dataBuffer,
            timeout = DEFAULT_TIMEOUT
        )
    }

    private fun sendUsbDataForHF4000(length: Int, dataBuffer: ByteArray): Int {
        val commandStatusWrapper = ByteArray(CSW_LENGTH)
        val commandBlockWrapper = createCommandBlockWrapper(
            length = length,
            isDataOut = true
        )

        if (usbDeviceCommunicator.writeUsbDevice(
                dataSize = 31,
                dataBuffer = commandBlockWrapper
            ) != 0
        ) return RETURN_FAIL

        if (usbDeviceCommunicator.writeUsbDevice(
                dataSize = length,
                dataBuffer = dataBuffer
            ) != 0
        ) return RETURN_FAIL

        val transferResult = usbDeviceCommunicator.readUsbDevice(
            dataSize = 13,
            dataBuffer = commandStatusWrapper,
        )

        if (commandStatusWrapper[CSW_SIGNATURE_INDEX] != CSW_SIGNATURE_OK
            || commandStatusWrapper[CSW_STATUS_INDEX] != EMPTY_BYTE
        ) return RETURN_FAIL
        commandStatusWrapper[3] = 0x43

        if (deviceType == DeviceType.HF4000_V1)
            for (i in 0..<12)
                if (commandStatusWrapper[i] != commandBlockWrapper[i])
                    return RETURN_FAIL
        return transferResult
    }

    private fun receiveUsbData(
        dataBuffer: ByteArray,
        length: Int,
        timeout: Int = DEFAULT_TIMEOUT
    ): Boolean = when (deviceType) {
        DeviceType.HF4000_V1,
        DeviceType.HF4000_V2 -> receiveUsbDataForHF4000(
            length = length,
            timeout = timeout,
            dataBuffer = dataBuffer
        )

        DeviceType.OtherTypes -> receiveUsbDataForOtherTypes(
            length = length,
            dataBuffer = dataBuffer
        )

        else -> false
    }

    private fun receiveUsbDataForOtherTypes(length: Int, dataBuffer: ByteArray): Boolean {
        val buffer = ByteArray(10)
        usbDeviceCommunicator.sendUsbControlMessage(
            request = 1,
            value = length,
            buffer = buffer
        )

        return usbDeviceCommunicator.readUsbBulkData(buffer = dataBuffer, length = length) >= 0
    }

    private fun receiveUsbDataForHF4000(
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
                dataSize = 31,
                timeout = timeout,
                dataBuffer = commandBlockWrapper
            ) < 0
        ) return false

        if (usbDeviceCommunicator.readUsbDevice(
                dataSize = length,
                timeout = timeout,
                dataBuffer = dataBuffer
            ) < 0
        ) return false

        val transferResult = usbDeviceCommunicator.readUsbDevice(
            dataSize = 13,
            timeout = timeout,
            dataBuffer = commandStatusWrapper
        )
        if (commandStatusWrapper[CSW_SIGNATURE_INDEX] != CSW_SIGNATURE_OK
            || commandStatusWrapper[CSW_STATUS_INDEX] != EMPTY_BYTE
        ) return false

        if (deviceType == DeviceType.HF4000_V1)
            for (i in 4..<8)
                if (commandStatusWrapper[i] != commandBlockWrapper[i])
                    return false
        return transferResult >= 0
    }

    private fun receiveUsbImage(dataBuffer: ByteArray, length: Int): Boolean {
        when (deviceType) {
            DeviceType.OtherTypes -> {
                var result: Int = usbDeviceCommunicator.sendUsbControlMessage(
                    request = RECEIVE_CONTROL_MESSAGE_REQUEST,
                    value = length,
                    buffer = ByteArray(10),
                )

                result = receiveUsbImageImpl(
                    length = length,
                    dataBuffer = dataBuffer,
                    receiver = usbDeviceCommunicator::readUsbBulkData
                ) ?: result
                return result >= 0
            }

            DeviceType.HF4000_V1,
            DeviceType.HF4000_V2 -> return receiveUsbImageImpl(
                length = length,
                dataBuffer = dataBuffer,
                receiver = ::receiveUsbData
            ) == true

            else -> return false
        }
    }

    private fun <R> receiveUsbImageImpl(
        length: Int,
        dataBuffer: ByteArray,
        receiver: (tempBuffer: ByteArray, len: Int) -> R
    ): R? {
        val n = 8
        val len = length / n
        var result: R? = null
        val tempBuffer = ByteArray(len)
        for (k in 0..<n) {
            result = receiver(tempBuffer, len)
            val t = len * k
            for (i in 0..<len)
                dataBuffer[t + i] = tempBuffer[i]
        }
        return result
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

        var checksum = 0
        var destinationIndex = 6
        for (i in 0 until (sourceLength - 1)) {
            checksum += sourceData[i].toInt()
            destinationData[destinationIndex++] = (sourceData[i])
        }

        val checksumLow = checksum and 0xff
        val checksumHigh = checksum shr 8 and 0xff
        destinationData[destinationIndex++] = checksumHigh.toByte()
        destinationData[destinationIndex] = checksumLow.toByte()
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

    private fun receivePackage(
        dataBuffer: ByteArray,
        length: Int = RECEIVED_PACKAGE_LENGTH,
        timeout: Int = RECEIVED_PACKAGE_TIMEOUT
    ): Boolean {
        val receiveBuffer = ByteArray(1024)
        val decodedLength = IntArray(1)
        if (receiveUsbData(receiveBuffer, length, timeout).not()) return false
        if (decodeData(receiveBuffer, dataBuffer, decodedLength).not()) return false
        return true
    }

    private fun getPackageLength(dataBuffer: ByteArray): Int =
        dataBuffer[1] * 256 + dataBuffer[2] + 1 + 2

    private fun sendPackage(
        dataBuffer: ByteArray,
        address: Int = GENERAL_SEND_PACKAGE_ADDRESS
    ): Boolean {
        val encodedLength = IntArray(1)
        val encodedBuffer = ByteArray(MAX_PACKAGE_SIZE + 20)

        val packageLength = getPackageLength(dataBuffer)
        if (packageLength > MAX_PACKAGE_SIZE) return false

        if (encodeData(
                address = address,
                sourceData = dataBuffer,
                sourceLength = packageLength,
                destinationData = encodedBuffer,
                destinationLength = encodedLength
            ).not()
        ) return false

        if (encodedLength[0] > MAX_PACKAGE_SIZE) return false

        if (sendUsbData(dataBuffer = encodedBuffer, length = encodedLength[0]) != 0) return false
        return true
    }

    private fun fillPackage(
        dataBuffer: ByteArray,
        length: Int,
        contentBuffer: ByteArray,
        packageType: Byte = FILL_PACKAGE_COMMAND,
    ): Int {
        if (length < 0 || length > MAX_PACKAGE_SIZE) return 0
        if (packageType !in listOf(
                FILL_PACKAGE_COMMAND,
                DATA_PACKET,
                END_DATA_PACKET
            )
        ) return 0

        var lengthModified = length + 2
        dataBuffer[0] = packageType
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
            (dataBuffer[3] isEqual 0)
        else
            false
    }

    private fun getImageData(
        imageData: ByteArray,
        imageType: ScannedImageType = ScannedImageType.Normal
    ): Boolean {
        val sendData = ByteArray(MAX_PACKAGE_SIZE)
        val content = ByteArray(10).apply { this[0] = imageType.getCommand }
        fillPackage(dataBuffer = sendData, length = 1, contentBuffer = content)

        if (sendPackage(dataBuffer = sendData).not()) return false

        return receiveUsbImage(dataBuffer = imageData, length = imageType.size)
    }

    private fun getImageDataExtra(imageData: ByteArray): Boolean = getImageData(
        imageData = imageData,
        imageType = ScannedImageType.Extra
    )

    override fun verifyPassword(password: ByteArray): Boolean {
        val packageLength = 5
        val content = ByteArray(10)
        val sendData = ByteArray(MAX_PACKAGE_SIZE)
        val receiveData = ByteArray(MAX_PACKAGE_SIZE)

        content[0] = VERIFY_PASSWORD_COMMAND
        for (i in 1..<packageLength)
            content[i] = password[i - 1]

        fillPackage(dataBuffer = sendData, length = packageLength, contentBuffer = content)

        if (!sendPackage(dataBuffer = sendData)) return false
        if (!receivePackage(dataBuffer = receiveData)) throw DeviceFailException()
        return verifyResponsePackage(dataBuffer = receiveData)
    }

    enum class DeviceType {
        HF4000_V1,
        HF4000_V2,
        OtherTypes,
        Unknown
    }

    companion object {
        private const val MIN_CLEAN_REQUIRED_BRIGHTNESS_THRESHOLD = 337_000f
        private const val MAX_CLEAN_REQUIRED_BRIGHTNESS_THRESHOLD = 475_000f

        fun isHfSecurityDevice(vendorId: Int, productId: Int): Boolean = when (vendorId) {
            1107 -> productId == 36869
            8201 -> productId == 30264                // HF4000 V1 (Micro-USB)
            8457 -> productId == 30264
            1155 -> productId in listOf(22304, 22240) // HF4000 V2 (USB-C)
            else -> false
        }
    }
}
