package com.degard.imagecompressor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface

object ImageCompressor {

    private const val TAG = "ImageCompressor"

    data class Result(val compressed: Int, val errors: Int, val skipped: Int)

    fun compress(
        context: Context,
        sourceUri: Uri,
        tmpUri: Uri,
        finalUri: Uri,
        quality: Int,
        maxRes: Int,
        onProgress: (String) -> Unit = {}
    ): Result {
        val srcDoc = DocumentFile.fromTreeUri(context, sourceUri)
            ?: return Result(0, 0, 0)
        val tmpDoc = DocumentFile.fromTreeUri(context, tmpUri)
            ?: return Result(0, 0, 0)
        val finalDoc = DocumentFile.fromTreeUri(context, finalUri)
            ?: return Result(0, 0, 0)

        var compressed = 0
        var errors = 0
        var skipped = 0

        fun walkDir(dir: DocumentFile) {
            dir.listFiles().forEach { file ->
                if (file.isDirectory) {
                    walkDir(file)
                    return@forEach
                }

                val name = file.name ?: return@forEach
                val ext = name.substringAfterLast('.', "").lowercase()

                if (ext !in setOf("jpg", "jpeg", "png")) return@forEach

                val outName = "${name.substringBeforeLast('.')}.webp"

                // Skip if already compressed in tmp
                if (tmpDoc.findFile(outName) != null) {
                    skipped++
                    return@forEach
                }

                onProgress("Compressing: $name")

                try {
                    val bitmap = decodeSampled(context, file.uri, maxRes) ?: run {
                        errors++
                        return@forEach
                    }

                    val outFile = tmpDoc.createFile("image/webp", outName)
                        ?: run {
                            errors++
                            return@forEach
                        }

                    context.contentResolver.openOutputStream(outFile.uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, out)
                    }
                    bitmap.recycle()

                    // Delete original on success
                    file.delete()
                    compressed++
                } catch (e: Exception) {
                    Log.e(TAG, "Error compressing $name", e)
                    errors++
                }
            }
        }

        walkDir(srcDoc)

        // Move from tmp to final
        onProgress("Moving to final folder…")
        moveFiles(context, tmpDoc, finalDoc)

        return Result(compressed, errors, skipped)
    }

    private fun moveFiles(context: Context, src: DocumentFile, dst: DocumentFile) {
        src.listFiles().forEach { file ->
            if (file.isDirectory) {
                val subDir = dst.createDirectory(file.name ?: "sub") ?: return@forEach
                moveFiles(context, file, subDir)
                return@forEach
            }

            val name = file.name ?: return@forEach
            val newFile = dst.createFile("image/webp", name.substringBeforeLast('.')) ?: return@forEach

            context.contentResolver.openInputStream(file.uri)?.use { input ->
                context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                    input.copyTo(output)
                }
            }
            file.delete()
        }
    }

    private fun decodeSampled(context: Context, uri: Uri, maxRes: Int): Bitmap? {
        val resolver = context.contentResolver

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }

        val width = options.outWidth
        val height = options.outHeight
        if (width <= 0 || height <= 0) return null

        var inSampleSize = 1
        while (width / inSampleSize > maxRes || height / inSampleSize > maxRes) {
            inSampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: return null

        // Handle EXIF rotation
        return try {
            resolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                val rotation = when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
                if (rotation != 0f) {
                    val matrix = Matrix().apply { postRotate(rotation) }
                    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    if (rotated !== bitmap) bitmap.recycle()
                    rotated
                } else {
                    bitmap
                }
            } ?: bitmap
        } catch (e: Exception) {
            bitmap
        }
    }
}
