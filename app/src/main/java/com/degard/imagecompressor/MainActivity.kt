package com.degard.imagecompressor

import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.degard.imagecompressor.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val pathStack = mutableListOf<PathEntry>()

    data class PathEntry(val uri: Uri, val title: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        FolderCache.init(this)

        binding.toolbar.title = getString(R.string.gallery_title)
        binding.rvGallery.layoutManager = GridLayoutManager(this, 3)
        binding.rvGallery.adapter = GalleryAdapter(emptyList(), emptyList()) { entry ->
            when (entry) {
                is GalleryAdapter.FolderEntry -> {
                    pathStack.add(PathEntry(entry.uri, entry.name))
                    loadCurrentLevel()
                }
                is GalleryAdapter.ImageEntry -> openFullScreen(entry)
            }
        }

        binding.btnUp.setOnClickListener { goUp() }

        val savedUri = Prefs(this).galleryRootUri
        if (savedUri != null) {
            pathStack.add(PathEntry(savedUri, getString(R.string.gallery_title)))
            loadCurrentLevel()
        }
    }

    override fun onResume() {
        super.onResume()
        if (pathStack.isNotEmpty()) {
            FolderCache.invalidate(pathStack.last().uri)
            loadCurrentLevel()
        }
    }

    private fun loadCurrentLevel() {
        val entry = pathStack.last()
        binding.toolbar.title = entry.title
        binding.btnUp.visibility = if (pathStack.size > 1) View.VISIBLE else View.GONE

        val cached = FolderCache.get(entry.uri)
        if (cached != null) {
            displayEntries(cached)
        } else {
            showLoading(true)
        }

        Thread {
            try {
                val children = queryChildren(entry.uri)
                FolderCache.put(entry.uri, children)
                runOnUiThread {
                    showLoading(false)
                    displayEntries(children)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showLoading(false)
                    if (cached == null) {
                        showError(e)
                    }
                }
            }
        }.start()
    }

    private fun displayEntries(entries: List<FolderCache.CachedEntry>) {
        val folders = entries.filter { it.isDirectory && it.imageCount > 0 }
            .map { GalleryAdapter.FolderEntry(it.name, Uri.parse(it.uri), it.imageCount) }
        val images = mutableListOf<GalleryAdapter.ImageEntry>()
        var imgIndex = 0
        entries.filter { !it.isDirectory }.forEach {
            images.add(GalleryAdapter.ImageEntry(Uri.parse(it.uri), it.name, imgIndex++))
        }

        binding.emptyText.visibility = if (folders.isEmpty() && images.isEmpty()) View.VISIBLE else View.GONE

        (binding.rvGallery.adapter as GalleryAdapter).update(folders, images)
    }

    private fun queryChildren(parentUri: Uri): List<FolderCache.CachedEntry> {
        val resolver = contentResolver
        val treeId = DocumentsContract.getTreeDocumentId(parentUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, treeId)

        val entries = mutableListOf<FolderCache.CachedEntry>()
        val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "heic", "heif")

        var cursor: Cursor? = null
        try {
            cursor = resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            ) ?: return entries

            while (cursor.moveToNext()) {
                val docId = cursor.getString(0)
                val name = cursor.getString(1) ?: continue
                val mime = cursor.getString(2) ?: ""
                val isDir = DocumentsContract.Document.MIME_TYPE_DIR == mime

                val childUri = DocumentsContract.buildDocumentUriUsingTree(parentUri, docId)

                val imageCount = if (isDir) {
                    countImagesFast(parentUri, docId)
                } else {
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext in imageExtensions) 1 else 0
                }

                entries.add(
                    FolderCache.CachedEntry(
                        name = name,
                        uri = childUri.toString(),
                        isDirectory = isDir,
                        imageCount = imageCount
                    )
                )
            }
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }

        entries.sortWith(compareByDescending<FolderCache.CachedEntry> { it.isDirectory }.thenBy { it.name })
        return entries
    }

    private fun countImagesFast(treeUri: Uri, documentId: String): Int {
        val resolver = contentResolver
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)

        var count = 0
        var cursor: Cursor? = null
        try {
            cursor = resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            ) ?: return 0

            while (cursor.moveToNext()) {
                val childDocId = cursor.getString(0)
                val name = cursor.getString(1) ?: continue
                val mime = cursor.getString(2) ?: ""
                if (DocumentsContract.Document.MIME_TYPE_DIR == mime) {
                    count += countImagesFast(treeUri, childDocId)
                } else {
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext in setOf("jpg", "jpeg", "png", "webp", "heic", "heif")) count++
                }
            }
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }
        return count
    }

    private fun openFullScreen(entry: GalleryAdapter.ImageEntry) {
        val adapter = binding.rvGallery.adapter as GalleryAdapter
        val imageUris = adapter.images.map { it.uri.toString() }.toTypedArray()
        val intent = android.content.Intent(this, FullScreenImageActivity::class.java).apply {
            putExtra("uris", imageUris)
            putExtra("position", entry.position)
        }
        startActivity(intent)
    }

    private fun goUp() {
        if (pathStack.size > 1) {
            pathStack.removeAt(pathStack.lastIndex)
            loadCurrentLevel()
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showError(e: Exception) {
        binding.emptyText.text = "${getString(R.string.error_access)}\n${e.message}"
        binding.emptyText.visibility = View.VISIBLE
    }
}
