package com.degard.imagecompressor

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri

class GalleryDb(context: Context) : SQLiteOpenHelper(context, "gallery_cache.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS folder_entries (
                folder_uri TEXT NOT NULL,
                child_name TEXT NOT NULL,
                child_uri TEXT NOT NULL,
                is_directory INTEGER NOT NULL,
                image_count INTEGER DEFAULT 0,
                PRIMARY KEY (folder_uri, child_uri)
            )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS folder_entries")
        onCreate(db)
    }

    fun getEntries(folderUri: String): List<FolderCache.CachedEntry>? {
        val entries = mutableListOf<FolderCache.CachedEntry>()
        readableDatabase.query(
            "folder_entries",
            arrayOf("child_name", "child_uri", "is_directory", "image_count"),
            "folder_uri = ?",
            arrayOf(folderUri),
            null, null, null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                entries.add(
                    FolderCache.CachedEntry(
                        name = cursor.getString(0),
                        uri = cursor.getString(1),
                        isDirectory = cursor.getInt(2) == 1,
                        imageCount = cursor.getInt(3)
                    )
                )
            }
        }
        return if (entries.isEmpty()) null else entries
    }

    fun putEntries(folderUri: String, entries: List<FolderCache.CachedEntry>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("folder_entries", "folder_uri = ?", arrayOf(folderUri))
            val stmt = db.compileStatement(
                "INSERT INTO folder_entries (folder_uri, child_name, child_uri, is_directory, image_count) VALUES (?, ?, ?, ?, ?)"
            )
            for (e in entries) {
                stmt.clearBindings()
                stmt.bindString(1, folderUri)
                stmt.bindString(2, e.name)
                stmt.bindString(3, e.uri)
                stmt.bindLong(4, if (e.isDirectory) 1 else 0)
                stmt.bindLong(5, e.imageCount.toLong())
                stmt.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun invalidate(folderUri: String) {
        writableDatabase.delete("folder_entries", "folder_uri = ?", arrayOf(folderUri))
    }

    fun invalidateAll() {
        writableDatabase.delete("folder_entries", null, null)
    }
}
