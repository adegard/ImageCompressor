package com.degard.imagecompressor

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

object TagManager {

    private const val SEPARATOR = "; "

    fun getTags(context: Context, uri: Uri): List<String> {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val raw = exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION) ?: return emptyList()
                raw.split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun setTags(context: Context, uri: Uri, tags: List<String>) {
        try {
            val fd = context.contentResolver.openFileDescriptor(uri, "rw") ?: return
            fd.use {
                val exif = ExifInterface(it.fileDescriptor)
                val value = tags.joinToString(SEPARATOR)
                exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, if (value.isEmpty()) null else value)
                exif.saveAttributes()
            }
        } catch (_: Exception) {}
    }

    fun addTag(context: Context, uri: Uri, tag: String) {
        val current = getTags(context, uri).toMutableList()
        if (tag !in current) {
            current.add(tag)
            setTags(context, uri, current)
        }
    }

    fun removeTag(context: Context, uri: Uri, tag: String) {
        val current = getTags(context, uri).toMutableList()
        current.removeAll { it.equals(tag, ignoreCase = true) }
        setTags(context, uri, current)
    }

    fun hasTag(context: Context, uri: Uri): Boolean {
        return getTags(context, uri).isNotEmpty()
    }

    fun getFolderTag(context: Context, folderKey: String): String? {
        return FolderCache.getDb()?.getFolderTag(folderKey)
    }

    fun setFolderTag(context: Context, folderKey: String, tag: String) {
        FolderCache.getDb()?.setFolderTag(folderKey, tag)
    }

    fun getAllImageTags(context: Context, uris: List<Uri>): List<String> {
        val allTags = mutableSetOf<String>()
        for (uri in uris) {
            allTags.addAll(getTags(context, uri))
        }
        return allTags.sorted()
    }
}
