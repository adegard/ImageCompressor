package com.degard.imagecompressor

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import java.io.File

object FileUtils {

    fun getDocumentFile(context: Context, uri: Uri): File? {
        // Handle both tree URIs and file URIs
        val docFile = DocumentFile.fromTreeUri(context, uri) ?: return null
        return getFileFromDocumentFile(context, docFile)
    }

    private fun getFileFromDocumentFile(context: Context, docFile: DocumentFile): File? {
        val path = docFile.uri.path ?: return null

        // Try to find the actual file on disk
        // DocumentFile paths look like /tree/primary:DCIM/Camera or /document/primary:DCIM/Camera
        val storagePath = when {
            path.contains("/tree/primary:") -> {
                val subPath = path.substringAfter("/tree/primary:")
                File(Environment.getExternalStorageDirectory(), subPath)
            }
            path.contains("/document/primary:") -> {
                val subPath = path.substringAfter("/document/primary:")
                File(Environment.getExternalStorageDirectory(), subPath)
            }
            path.contains(":") -> {
                val subPath = path.substringAfter(":")
                File(Environment.getExternalStorageDirectory(), subPath)
            }
            else -> null
        }

        return storagePath?.takeIf { it.exists() }
    }

    fun getDirFromUri(context: Context, uri: Uri): File? {
        return getDocumentFile(context, uri)
    }
}
