package com.degard.imagecompressor

import android.content.Context
import android.net.Uri

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("compressor", Context.MODE_PRIVATE)

    var sourceUri: Uri?
        get() = prefs.getString("src", null)?.let { Uri.parse(it) }
        set(value) = prefs.edit().putString("src", value?.toString()).apply()

    var tmpUri: Uri?
        get() = prefs.getString("tmp", null)?.let { Uri.parse(it) }
        set(value) = prefs.edit().putString("tmp", value?.toString()).apply()

    var finalUri: Uri?
        get() = prefs.getString("final", null)?.let { Uri.parse(it) }
        set(value) = prefs.edit().putString("final", value?.toString()).apply()

    var quality: Int
        get() = prefs.getInt("quality", 65)
        set(value) = prefs.edit().putInt("quality", value).apply()

    var maxRes: Int
        get() = prefs.getInt("maxres", 1280)
        set(value) = prefs.edit().putInt("maxres", value).apply()
}
