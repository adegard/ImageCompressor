package com.degard.imagecompressor

import android.net.Uri

object FolderCache {

    data class CachedEntry(
        val name: String,
        val uri: String,
        val isDirectory: Boolean,
        val imageCount: Int = 0
    )

    private val cache = mutableMapOf<String, List<CachedEntry>>()

    fun get(uri: Uri): List<CachedEntry>? = cache[uri.toString()]

    fun put(uri: Uri, entries: List<CachedEntry>) {
        cache[uri.toString()] = entries
    }

    fun invalidate(uri: Uri) {
        cache.remove(uri.toString())
    }

    fun invalidateAll() {
        cache.clear()
    }
}
