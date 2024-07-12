package com.fingerprint.utils

internal object Constants {
    const val IMAGE_WIDTH = 256
    const val IMAGE_HEIGHT = 288
    private const val EXTRA_IMAGE_WIDTH: Int = 256
    private const val EXTRA_IMAGE_HEIGHT: Int = 360
    const val BMP_DESTINATION_OFFSET = 1078
    const val STD_BMP_SIZE: Int = (IMAGE_WIDTH * IMAGE_HEIGHT) + BMP_DESTINATION_OFFSET
    const val EXTRA_STD_BMP_SIZE: Int =
        (EXTRA_IMAGE_WIDTH * EXTRA_IMAGE_HEIGHT) + BMP_DESTINATION_OFFSET

    const val TIMEOUT_LONG: Int = 0x7FFFFFFF

    const val RETURN_OK: Int = 0
    const val RETURN_FAIL: Int = -1
    const val DEVICE_FAIL: Int = -2
    const val FILL_PACKAGE_COMMAND: Byte = 0x01
    const val DATA_PACKET: Byte = 0x02
    const val END_DATA_PACKET: Byte = 0x08
    const val CAPTURE_IMAGE_COMMAND: Byte = 0x01
    const val CAPTURE_IMAGE_EXTRA_COMMAND: Byte = 0x30
    const val MAX_PACKAGE_SIZE = 350
    const val RESPONSE_PACKET: Byte = 0x07
    const val TIMEOUT = 10000
    const val GET_IMAGE_COMMAND: Byte = 0x0a
    const val GET_IMAGE_EXTRA_COMMAND: Byte = 0x31
    const val VERIFY_PASSWORD_COMMAND: Byte = 0x13
    const val GENERAL_SEND_PACKAGE_ADDRESS: Int = -1
}
