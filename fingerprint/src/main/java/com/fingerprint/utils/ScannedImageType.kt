package com.fingerprint.utils


enum class ScannedImageType(
    val uploadCommand: Byte,
    val fetchCommand: Byte,
    val size: Int,
    val imageWidth: Int,
    val imageHeight: Int
) {
    Normal(
        uploadCommand = Constants.UPLOAD_IMAGE_COMMAND,
        fetchCommand = Constants.GET_IMAGE_COMMAND,
        size = Constants.STD_BMP_SIZE,
        imageWidth = Constants.IMAGE_WIDTH,
        imageHeight = Constants.IMAGE_HEIGHT
    ),
    Extra(
        uploadCommand = Constants.UPLOAD_IMAGE_EX_COMMAND,
        fetchCommand = Constants.GET_IMAGE_EXTRA_COMMAND,
        size = Constants.EXTRA_STD_BMP_SIZE,
        imageWidth = Constants.IMAGE_WIDTH,
        imageHeight = Constants.IMAGE_HEIGHT
    )
}
