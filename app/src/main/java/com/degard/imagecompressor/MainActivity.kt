package com.degard.imagecompressor

import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.degard.imagecompressor.databinding.ActivityMainBinding
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val pathStack = mutableListOf<PathEntry>()
    @Volatile private var loadGeneration = 0

    data class PathEntry(
        val treeUri: Uri,
        val documentId: String,
        val title: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        FolderCache.init(this)

        binding.toolbar.title = getString(R.string.gallery_title)
        binding.rvGallery.layoutManager = GridLayoutManager(this, 3)

        val adapter = GalleryAdapter(
            onFolderClick = { folder ->
                if ((binding.rvGallery.adapter as? GalleryAdapter)?.isSelectionMode == true) return@GalleryAdapter
                pathStack.add(PathEntry(folder.treeUri, folder.documentId, folder.name))
                loadCurrentLevel()
            },
            onImageClick = { index ->
                if ((binding.rvGallery.adapter as? GalleryAdapter)?.isSelectionMode == true) return@GalleryAdapter
                openFullScreen(index)
            },
            onFolderLongClick = { folder ->
                showFolderTagDialog(folder)
            },
            onSelectionModeChanged = { active, count ->
                if (active) {
                    binding.toolbar.visibility = View.GONE
                    binding.selectionBar.visibility = View.VISIBLE
                    binding.tvSelectionCount.text = getString(R.string.selected_count, count)
                } else {
                    binding.selectionBar.visibility = View.GONE
                    binding.toolbar.visibility = View.VISIBLE
                }
            }
        )
        binding.rvGallery.adapter = adapter

        binding.toolbar.setNavigationOnClickListener { goUp() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_settings) {
                startActivity(android.content.Intent(this, SettingsActivity::class.java))
                true
            } else false
        }

        binding.btnCancelSelection.setOnClickListener {
            adapter.exitSelectionMode()
        }

        binding.btnTagSelected.setOnClickListener {
            showBatchTagDialog()
        }

        binding.btnDeleteSelected.setOnClickListener {
            confirmBatchDelete()
        }

        binding.btnOpenSettings.setOnClickListener {
            startActivity(android.content.Intent(this, SettingsActivity::class.java))
        }

        val savedUri = Prefs(this).galleryRootUri
        if (savedUri != null) {
            val rootDocId = DocumentsContract.getTreeDocumentId(savedUri)
            pathStack.add(PathEntry(savedUri, rootDocId, getString(R.string.gallery_title)))
            loadCurrentLevel()
        } else {
            showEmptyState(true)
        }
    }

    override fun onResume() {
        super.onResume()
        if (pathStack.isNotEmpty()) {
            FolderCache.invalidate(pathStack.last().key())
            loadCurrentLevel()
        } else {
            val savedUri = Prefs(this).galleryRootUri
            if (savedUri != null) {
                val rootDocId = DocumentsContract.getTreeDocumentId(savedUri)
                pathStack.add(PathEntry(savedUri, rootDocId, getString(R.string.gallery_title)))
                loadCurrentLevel()
            }
        }
    }

    private fun loadCurrentLevel() {
        val entry = pathStack.last()
        binding.toolbar.title = entry.title
        binding.toolbar.navigationIcon = if (pathStack.size > 1) {
            androidx.appcompat.content.res.AppCompatResources.getDrawable(this, androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        } else {
            null
        }

        showEmptyState(false)
        binding.progressBar.visibility = View.GONE

        val cacheKey = entry.key()
        val cached = FolderCache.get(cacheKey)
        if (cached != null) {
            displayEntries(cached, entry)
        } else {
            binding.progressBar.visibility = View.VISIBLE
        }

        val gen = ++loadGeneration
        Thread {
            try {
                val children = queryChildren(entry.treeUri, entry.documentId)
                if (gen != loadGeneration) return@Thread
                FolderCache.put(cacheKey, children)
                runOnUiThread {
                    if (gen == loadGeneration) {
                        binding.progressBar.visibility = View.GONE
                        displayEntries(children, entry)
                        if (children.isEmpty()) showEmptyState(true)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (gen == loadGeneration) {
                        binding.progressBar.visibility = View.GONE
                        if (cached == null) showEmptyState(true)
                    }
                }
            }
        }.start()
    }

    private fun displayEntries(entries: List<FolderCache.CachedEntry>, parent: PathEntry) {
        val adapter = binding.rvGallery.adapter as GalleryAdapter

        val folders = entries.filter { it.isDirectory }.map {
            val folderKey = "${parent.key()}|${it.docId}"
            val folderTag = FolderCache.getDb()?.getFolderTag(folderKey)
            GalleryAdapter.FolderEntry(
                name = it.name,
                treeUri = parent.treeUri,
                documentId = it.docId,
                childCount = it.imageCount,
                tag = folderTag
            )
        }

        val images = mutableListOf<GalleryAdapter.ImageEntry>()
        var imgIndex = 0
        entries.filter { !it.isDirectory }.forEach {
            images.add(GalleryAdapter.ImageEntry(Uri.parse(it.uri), it.name, imgIndex++, hasTag = false))
        }

        adapter.submitData(folders, images)

        Thread {
            val tagMap = mutableMapOf<Int, Boolean>()
            for (img in images) {
                tagMap[img.index] = TagManager.hasTag(this, img.uri)
            }
            runOnUiThread {
                for ((idx, hasTag) in tagMap) {
                    adapter.updateHasTag(idx, hasTag)
                }
            }
        }.start()
    }

    private fun queryChildren(treeUri: Uri, parentDocId: String): List<FolderCache.CachedEntry> {
        val resolver = contentResolver
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)

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

                val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

                val imageCount = if (!isDir) {
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext in imageExtensions) 1 else 0
                } else 0

                entries.add(
                    FolderCache.CachedEntry(
                        name = name,
                        uri = childUri.toString(),
                        docId = docId,
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

    private fun openFullScreen(index: Int) {
        val adapter = binding.rvGallery.adapter as GalleryAdapter
        val imageUris = adapter.getImages().map { it.uri.toString() }.toTypedArray()
        val intent = android.content.Intent(this, FullScreenImageActivity::class.java).apply {
            putExtra("uris", imageUris)
            putExtra("position", index)
        }
        startActivity(intent)
    }

    private fun goUp() {
        if (pathStack.size > 1) {
            pathStack.removeAt(pathStack.lastIndex)
            loadCurrentLevel()
        }
    }

    private fun showEmptyState(show: Boolean) {
        binding.emptyState.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun confirmBatchDelete() {
        val adapter = binding.rvGallery.adapter as GalleryAdapter
        val selectedUris = adapter.getSelectedUris()
        if (selectedUris.isEmpty()) return

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.confirm_delete_title)
            .setMessage("Delete ${selectedUris.size} photo(s) permanently?")
            .setPositiveButton(R.string.delete) { _, _ ->
                for (uri in selectedUris) {
                    androidx.documentfile.provider.DocumentFile.fromSingleUri(this, uri)?.delete()
                }
                adapter.exitSelectionMode()
                loadCurrentLevel()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showBatchTagDialog() {
        val adapter = binding.rvGallery.adapter as GalleryAdapter
        val selectedUris = adapter.getSelectedUris()
        if (selectedUris.isEmpty()) return

        val allTags = mutableSetOf<String>()
        for (uri in selectedUris) {
            allTags.addAll(TagManager.getTags(this, uri))
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val dp16 = (16 * resources.displayMetrics.density).toInt()
            setPadding(dp16, dp16, dp16, 0)
        }

        val input = EditText(this).apply {
            hint = getString(R.string.tag_hint)
            if (allTags.isNotEmpty()) {
                setText(allTags.joinToString(", "))
                setSelection(text.length)
            }
        }
        layout.addView(input)

        if (allTags.isNotEmpty()) {
            val label = TextView(this).apply {
                text = "Current tags:"
                textSize = 12f
                val dp8 = (8 * resources.displayMetrics.density).toInt()
                setPadding(0, dp8, 0, dp8)
            }
            layout.addView(label)

            val chipGroup = ChipGroup(this)
            for (tag in allTags.sorted()) {
                val chip = Chip(this).apply {
                    text = tag
                    isClickable = true
                    setOnClickListener {
                        val current = input.text.toString()
                        val parts = current.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        if (tag !in parts) {
                            val newText = if (current.isEmpty()) tag else "$current, $tag"
                            input.setText(newText)
                            input.setSelection(newText.length)
                        }
                    }
                }
                chipGroup.addView(chip)
            }
            layout.addView(chipGroup)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Tag ${selectedUris.size} photo(s)")
            .setView(layout)
            .setPositiveButton(R.string.tag) { _, _ ->
                val raw = input.text.toString()
                val tags = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                for (uri in selectedUris) {
                    TagManager.setTags(this, uri, tags)
                }
                adapter.exitSelectionMode()
                loadCurrentLevel()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showFolderTagDialog(folder: GalleryAdapter.FolderEntry) {
        val folderKey = "${pathStack.last().key()}|${folder.documentId}"
        val currentTag = FolderCache.getDb()?.getFolderTag(folderKey) ?: ""

        val input = EditText(this).apply {
            hint = getString(R.string.tag_hint)
            setText(currentTag)
            setSelection(text.length)
        }

        val dp16 = (16 * resources.displayMetrics.density).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp16, dp16, dp16, 0)
        }
        container.addView(input)

        MaterialAlertDialogBuilder(this)
            .setTitle("Tag folder: ${folder.name}")
            .setView(container)
            .setPositiveButton(R.string.tag) { _, _ ->
                val tag = input.text.toString().trim()
                FolderCache.getDb()?.setFolderTag(folderKey, tag)
                loadCurrentLevel()
            }
            .setNeutralButton("Clear") { _, _ ->
                FolderCache.getDb()?.setFolderTag(folderKey, "")
                loadCurrentLevel()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun PathEntry.key(): String = "${treeUri}|${documentId}"
}
