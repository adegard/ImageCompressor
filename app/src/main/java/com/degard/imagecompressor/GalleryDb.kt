package com.degard.imagecompressor

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class GalleryDb(context: Context) : SQLiteOpenHelper(context, "gallery_cache.db", null, 3) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS folder_entries (
                folder_key TEXT NOT NULL,
                child_name TEXT NOT NULL,
                child_uri TEXT NOT NULL,
                child_doc_id TEXT NOT NULL DEFAULT '',
                is_directory INTEGER NOT NULL,
                image_count INTEGER DEFAULT 0,
                PRIMARY KEY (folder_key, child_uri)
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS folder_annotations (
                folder_key TEXT PRIMARY KEY,
                tag TEXT NOT NULL
            )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            db.execSQL("DROP TABLE IF EXISTS folder_annotations")
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS folder_annotations (
                    folder_key TEXT PRIMARY KEY,
                    tag TEXT NOT NULL
                )"""
            )
        }
    }

    fun getEntries(folderKey: String): List<FolderCache.CachedEntry>? {
        val entries = mutableListOf<FolderCache.CachedEntry>()
        readableDatabase.query(
            "folder_entries",
            arrayOf("child_name", "child_uri", "child_doc_id", "is_directory", "image_count"),
            "folder_key = ?",
            arrayOf(folderKey),
            null, null, null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                entries.add(
                    FolderCache.CachedEntry(
                        name = cursor.getString(0),
                        uri = cursor.getString(1),
                        docId = cursor.getString(2),
                        isDirectory = cursor.getInt(3) == 1,
                        imageCount = cursor.getInt(4)
                    )
                )
            }
        }
        return if (entries.isEmpty()) null else entries
    }

    fun putEntries(folderKey: String, entries: List<FolderCache.CachedEntry>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("folder_entries", "folder_key = ?", arrayOf(folderKey))
            val stmt = db.compileStatement(
                "INSERT INTO folder_entries (folder_key, child_name, child_uri, child_doc_id, is_directory, image_count) VALUES (?, ?, ?, ?, ?, ?)"
            )
            for (e in entries) {
                stmt.clearBindings()
                stmt.bindString(1, folderKey)
                stmt.bindString(2, e.name)
                stmt.bindString(3, e.uri)
                stmt.bindString(4, e.docId)
                stmt.bindLong(5, if (e.isDirectory) 1 else 0)
                stmt.bindLong(6, e.imageCount.toLong())
                stmt.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun invalidate(folderKey: String) {
        writableDatabase.delete("folder_entries", "folder_key = ?", arrayOf(folderKey))
    }

    fun invalidateAll() {
        writableDatabase.delete("folder_entries", null, null)
    }

    fun getFolderTag(folderKey: String): String? {
        readableDatabase.query(
            "folder_annotations",
            arrayOf("tag"),
            "folder_key = ?",
            arrayOf(folderKey),
            null, null, null
        ).use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return null
    }

    fun setFolderTag(folderKey: String, tag: String) {
        if (tag.isEmpty()) {
            writableDatabase.delete("folder_annotations", "folder_key = ?", arrayOf(folderKey))
        } else {
            writableDatabase.execSQL(
                "INSERT OR REPLACE INTO folder_annotations (folder_key, tag) VALUES (?, ?)",
                arrayOf(folderKey, tag)
            )
        }
    }

    fun getAllTags(): List<String> {
        val tags = mutableListOf<String>()
        readableDatabase.query(
            "folder_annotations",
            arrayOf("tag"),
            null, null, null, null, null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                tags.add(cursor.getString(0))
            }
        }
        return tags.distinct().sorted()
    }
}
