package com.degard.imagecompressor

import android.content.Context
import android.net.Uri

object FolderCache {

    data class CachedEntry(
        val name: String,
        val uri: String,
        val isDirectory: Boolean,
        val imageCount: Int = 0
    )

    private var db: GalleryDb? = null

    fun init(context: Context) {
        if (db == null) db = GalleryDb(context.applicationContext)
    }

    fun get(uri: Uri): List<CachedEntry>? = db?.getEntries(uri.toString())

    fun put(uri: Uri, entries: List<CachedEntry>) {
        db?.putEntries(uri.toString(), entries)
    }

    fun invalidate(uri: Uri) {
        db?.invalidate(uri.toString())
    }

    fun invalidateAll() {
        db?.invalidateAll()
    }
}
