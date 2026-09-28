package com.degard.imagecompressor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

object ImageUtil {

    enum class RotationResult { FAILED, EXIF_ONLY, REENCODED }

    private const val REENCODE_MAX_DIMENSION = 4096

    fun readExifDegrees(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                degreesForOrientation(
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                )
            } ?: 0
        } catch (_: Throwable) {
            0
        }
    }

    fun degreesForOrientation(orientation: Int): Int = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }

    fun orientationForDegrees(degrees: Int): Int = when (((degrees % 360) + 360) % 360) {
        90 -> ExifInterface.ORIENTATION_ROTATE_90
        180 -> ExifInterface.ORIENTATION_ROTATE_180
        270 -> ExifInterface.ORIENTATION_ROTATE_270
        else -> ExifInterface.ORIENTATION_NORMAL
    }

    fun decodeRaw(context: Context, uri: Uri, sampleSize: Int = 1): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun decodeBounded(context: Context, uri: Uri, maxDimension: Int = REENCODE_MAX_DIMENSION): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
        } catch (_: Throwable) {
            return null
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDimension) {
            sample *= 2
        }
        return decodeRaw(context, uri, sample)
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
        } catch (_: Throwable) {
            bitmap
        }
    }

    fun isJpeg(name: String): Boolean = extensionOf(name) == "jpg" || extensionOf(name) == "jpeg"

    fun formatFor(name: String): Bitmap.CompressFormat = when (extensionOf(name)) {
        "jpg", "jpeg" -> Bitmap.CompressFormat.JPEG
        "png" -> Bitmap.CompressFormat.PNG
        else -> Bitmap.CompressFormat.WEBP_LOSSY
    }

    fun persistRotation(context: Context, uri: Uri, name: String, degrees: Int): RotationResult {
        val deg = ((degrees % 360) + 360) % 360
        if (deg == 0) return RotationResult.EXIF_ONLY
        if (isJpeg(name) && writeExifOrientation(context, uri, deg)) {
            return RotationResult.EXIF_ONLY
        }
        return if (reencodeRotated(context, uri, name, deg)) {
            RotationResult.REENCODED
        } else {
            RotationResult.FAILED
        }
    }

    fun writeExifOrientation(context: Context, uri: Uri, degrees: Int): Boolean {
        val deg = ((degrees % 360) + 360) % 360
        val saved = try {
            context.contentResolver.openFileDescriptor(uri, "rw")?.use { afd ->
                val exif = ExifInterface(afd.fileDescriptor)
                exif.setAttribute(
                    ExifInterface.TAG_ORIENTATION,
                    orientationForDegrees(deg).toString()
                )
                exif.saveAttributes()
                true
            } ?: false
        } catch (_: Throwable) {
            false
        }
        return saved && readExifDegrees(context, uri) == deg
    }

    private fun reencodeRotated(context: Context, uri: Uri, name: String, degrees: Int): Boolean {
        val source = decodeBounded(context, uri) ?: return false
        var written = false
        try {
            val rotated = rotate(source, degrees, recycleSource = true)
            val format = formatFor(name)
            val quality = if (format == Bitmap.CompressFormat.WEBP_LOSSY) 90 else 95
            written = try {
                context.contentResolver.openOutputStream(uri, "rwt")?.use { out ->
                    rotated.compress(format, quality, out)
                } != null
            } catch (_: Throwable) {
                false
            }
            if (written && format == Bitmap.CompressFormat.JPEG) {
                writeExifOrientation(context, uri, 0)
            }
        } catch (_: Throwable) {
            written = false
        } finally {
            if (!source.isRecycled) source.recycle()
        }
        return written
    }

    private fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase()
}
