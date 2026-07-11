package com.degard.imagecompressor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File

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
        val srcDoc = FileUtils.getDocumentFile(context, sourceUri) ?: return Result(0, 0, 0)
        val tmpDir = FileUtils.getDirFromUri(context, tmpUri) ?: return Result(0, 0, 0)
        val finalDir = FileUtils.getDirFromUri(context, finalUri) ?: return Result(0, 0, 0)

        var compressed = 0
        var errors = 0
        var skipped = 0

        val imageExtensions = setOf("jpg", "jpeg", "png", "webp")

        fun walkDir(file: File) {
            if (!file.exists()) return
            if (file.isDirectory) {
                file.listFiles()?.forEach { walkDir(it) }
                return
            }

            val ext = file.extension.lowercase()
            if (ext !in imageExtensions) return

            val relPath = file.absolutePath.removePrefix(srcDoc.absolutePath).trimStart('/')
            val outName = "$relPath.webp"

            // Skip WebP originals
            if (ext == "webp") {
                skipped++
                return
            }

            // Skip if already compressed
            val outFile = File(tmpDir, outName)
            if (outFile.exists()) {
                skipped++
                return
            }

            onProgress("Compressing: $relPath")

            try {
                outFile.parentFile?.mkdirs()

                val bitmap = decodeSampled(file, maxRes) ?: run {
                    errors++
                    return
                }

                outFile.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, out)
                }
                bitmap.recycle()

                if (outFile.exists() && outFile.length() > 0) {
                    file.delete()
                    compressed++
                } else {
                    errors++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error compressing $relPath", e)
                errors++
            }
        }

        walkDir(srcDoc)

        // Move from tmp to final
        onProgress("Moving to final folder…")
        moveFiles(tmpDir, finalDir)

        return Result(compressed, errors, skipped)
    }

    private fun moveFiles(src: File, dst: File) {
        if (!src.exists()) return
        if (src.isDirectory) {
            src.listFiles()?.forEach { moveFiles(it, File(dst, it.name)) }
            return
        }
        dst.parentFile?.mkdirs()
        src.renameTo(dst)
    }

    private fun decodeSampled(file: File, maxRes: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)

        val width = options.outWidth
        val height = options.outHeight
        var inSampleSize = 1

        while (width / inSampleSize > maxRes || height / inSampleSize > maxRes) {
            inSampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null

        // Handle EXIF rotation
        return try {
            val exif = ExifInterface(file.absolutePath)
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
        } catch (e: Exception) {
            bitmap
        }
    }
}
