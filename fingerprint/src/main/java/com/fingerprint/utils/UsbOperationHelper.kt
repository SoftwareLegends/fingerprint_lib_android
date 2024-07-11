package com.fingerprint.utils


internal object UsbOperationHelper {
    fun createCommandBlockWrapper(length: Int, isDataOut: Boolean): ByteArray {
        val commandBlockWrapper = ByteArray(CBW_LENGTH).apply {
            this[12] = if (isDataOut) CBW_FLAG_DATA_OUT else CBW_FLAG_DATA_IN
            this[14] = CBW_COMMAND_LENGTH
            this[15] = if (isDataOut) CBW_COMMAND_CODE_OUT else CBW_COMMAND_CODE_IN
            this[8] = (length and 0xff).toByte()
            this[9] = ((length shr 8) and 0xff).toByte()
            this[10] = ((length shr 16) and 0xff).toByte()
            this[11] = ((length shr 24) and 0xff).toByte()
        }
        System.arraycopy(CBW_SIGNATURE, 0, commandBlockWrapper, 0, CBW_SIGNATURE.size)
        System.arraycopy(DEFAULT_TAG, 0, commandBlockWrapper, 4, DEFAULT_TAG.size)
        return commandBlockWrapper
    }

    fun intToByteArray(value: Int): ByteArray = byteArrayOf(
        (value shr 0 and 0xFF).toByte(),
        (value shr 8 and 0xFF).toByte(),
        (value shr 16 and 0xFF).toByte(),
        (value shr 24 and 0xFF).toByte()
    )

    private val CBW_SIGNATURE = "USBC".toByteArray()
    private val DEFAULT_TAG = byteArrayOf(
        0xB0.toByte(),
        0xFA.toByte(),
        0x69.toByte(),
        0x86.toByte()
    )
    private const val CBW_LENGTH = 33
    const val CSW_LENGTH = 16
    private const val CBW_FLAG_DATA_OUT = 0x00.toByte()
    private const val CBW_FLAG_DATA_IN = 0x80.toByte()
    private const val CBW_COMMAND_LENGTH = 0x0A.toByte()
    private const val CBW_COMMAND_CODE_IN = 0x85.toByte()
    private const val CBW_COMMAND_CODE_OUT = 0x86.toByte()
    const val CSW_SIGNATURE_INDEX = 3
    const val CSW_STATUS_INDEX = 12
    const val CSW_SIGNATURE_OK = 0x53.toByte()
    const val EMPTY_BYTE = 0x00.toByte()
    private const val USB_REQUEST_TYPE_CONTROL_IN = 0x80
    private const val USB_REQUEST_TYPE_CONTROL_OUT = 0x00
    private const val USB_REQUEST_TYPE_VENDOR_OUT = 0x40
    const val USB_CONTROL_MESSAGE_TYPE = (
            USB_REQUEST_TYPE_CONTROL_IN
                    or USB_REQUEST_TYPE_VENDOR_OUT
                    or USB_REQUEST_TYPE_CONTROL_OUT
            )
}
