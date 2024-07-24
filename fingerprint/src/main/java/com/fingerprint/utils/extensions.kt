package com.fingerprint.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.fingerprint.utils.Constants.BMP_DESTINATION_OFFSET
import com.fingerprint.utils.UsbOperationHelper.intToByteArray
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream


@Suppress("UnusedReceiverParameter")
fun Any.returnUnit() = Unit

inline fun <reified T> T.toJson(): String = Json.encodeToString(this)

fun Bitmap.toByteArray(): ByteArray = ByteArrayOutputStream().apply {
    compress(Bitmap.CompressFormat.PNG, 100, this)
}.toByteArray()

fun ByteArray.toBitmap(config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap =
    BitmapFactory.decodeByteArray(this, 0, size, BitmapFactory.Options().apply {
        outConfig = config
    })

fun ByteArray.toImageBitmap(
    config: Bitmap.Config = Bitmap.Config.ARGB_8888,
): ImageBitmap = toBitmap(config).asImageBitmap()

internal fun ByteArray.convertImageDataToBitmapArray(
    height: Int,
    width: Int,
    config: Bitmap.Config = Bitmap.Config.ARGB_8888,
    applyFilters: Bitmap.(Bitmap.Config) -> Bitmap = { this }
): ByteArray {
    val bmpHeader: Byte = 14
    val dibHeader: Byte = 40
    val size = height * width
    val headerSize: Byte = (bmpHeader + dibHeader).toByte()
    val bitmapArray = ByteArray(size).apply {
        this[0] = 'B'.code.toByte()
        this[1] = 'M'.code.toByte()
        this[10] = headerSize
        this[11] = 4
        this[14] = dibHeader
        this[26] = 1
        this[28] = 8
    }

    val imageWidthBytes = intToByteArray(width)
    val imageHeightBytes = intToByteArray(height)

    System.arraycopy(imageWidthBytes, 0, bitmapArray, 18, 4)
    System.arraycopy(imageHeightBytes, 0, bitmapArray, 22, 4)

    for ((j, i) in (headerSize until BMP_DESTINATION_OFFSET step 4).withIndex()) {
        bitmapArray[i] = j.toByte() // BLUE
        bitmapArray[i + 1] = j.toByte() // GREEN
        bitmapArray[i + 2] = j.toByte() // RED
    }

    // Copying buffer
    for (i in 0 until (size - BMP_DESTINATION_OFFSET))
        bitmapArray[BMP_DESTINATION_OFFSET + i] = this[i]
    return bitmapArray.toBitmap(config).applyFilters(config).toByteArray()
}

fun Bitmap.applyFilters(config: Bitmap.Config): Bitmap {
    val contrast = 1.75f
    val contrastMatrix = ColorMatrix().apply {
        setScale(contrast, contrast, contrast, 1f)
        setSaturation(0f)
    }

    val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(contrastMatrix) }
    val enhancedBitmap = Bitmap.createBitmap(width, height, config)
    val canvas = Canvas(enhancedBitmap)
    canvas.drawBitmap(this, 0f, 0f, paint)
    return enhancedBitmap
}
