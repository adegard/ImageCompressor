package com.degard.imagecompressor

import android.content.Context
import android.net.Uri

object FolderCache {

    data class CachedEntry(
        val name: String,
        val uri: String,
        val docId: String = "",
        val isDirectory: Boolean,
        val imageCount: Int = 0
    )

    private var db: GalleryDb? = null

    fun init(context: Context) {
        if (db == null) db = GalleryDb(context.applicationContext)
    }

    fun get(key: String): List<CachedEntry>? = db?.getEntries(key)

    fun put(key: String, entries: List<CachedEntry>) {
        db?.putEntries(key, entries)
    }

    fun invalidate(key: String) {
        db?.invalidate(key)
    }

    fun invalidateAll() {
        db?.invalidateAll()
    }
}
