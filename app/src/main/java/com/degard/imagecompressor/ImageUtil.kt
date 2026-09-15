package com.degard.imagecompressor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

object ImageUtil {

    fun readExifDegrees(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (_: Exception) {
            0
        }
    }

    fun decodeRaw(context: Context, uri: Uri, sampleSize: Int = 1): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun decodeRotated(context: Context, uri: Uri, sampleSize: Int = 1): Bitmap? {
        val bitmap = decodeRaw(context, uri, sampleSize) ?: return null
        return rotate(bitmap, readExifDegrees(context, uri))
    }

    fun rotate(bitmap: Bitmap, degrees: Int, recycleSource: Boolean = true): Bitmap {
        val deg = ((degrees % 360) + 360) % 360
        if (deg == 0) return bitmap
        return try {
            val matrix = Matrix().apply { postRotate(deg.toFloat()) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap && recycleSource) bitmap.recycle()
            rotated
        } catch (_: Exception) {
            bitmap
        }
    }

    fun isJpeg(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext == "jpg" || ext == "jpeg"
    }

    fun orientationForDegrees(degrees: Int): Int = when (((degrees % 360) + 360) % 360) {
        0 -> ExifInterface.ORIENTATION_NORMAL
        90 -> ExifInterface.ORIENTATION_ROTATE_90
        180 -> ExifInterface.ORIENTATION_ROTATE_180
        270 -> ExifInterface.ORIENTATION_ROTATE_270
        else -> ExifInterface.ORIENTATION_NORMAL
    }
}