package com.fingerprint.utils


enum class ScannedImageType(
    val getCommand: Byte,
    val captureCommand: Byte,
    val size: Int,
    val imageWidth: Int,
    val imageHeight: Int
) {
    Normal(
        getCommand = Constants.GET_IMAGE_COMMAND,
        captureCommand = Constants.CAPTURE_IMAGE_COMMAND,
        size = Constants.STD_BMP_SIZE,
        imageWidth = Constants.IMAGE_WIDTH,
        imageHeight = Constants.IMAGE_HEIGHT
    ),
    Extra(
        getCommand = Constants.GET_IMAGE_EXTRA_COMMAND,
        captureCommand = Constants.CAPTURE_IMAGE_EXTRA_COMMAND,
        size = Constants.EXTRA_STD_BMP_SIZE,
        imageWidth = Constants.IMAGE_WIDTH,
        imageHeight = Constants.IMAGE_HEIGHT
    )
}
