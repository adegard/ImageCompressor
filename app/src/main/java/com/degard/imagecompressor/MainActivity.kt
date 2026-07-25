package com.degard.imagecompressor

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.GridLayoutManager
import com.degard.imagecompressor.databinding.ActivityMainBinding
import com.google.android.material.color.MaterialColors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: GalleryAdapter
    private var rootUri: Uri? = null

    private data class PathSegment(val name: String, val uri: Uri)
    private val pathStack = mutableListOf<PathSegment>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = GalleryAdapter(
            onFolderClick = { folder -> navigateInto(folder) },
            onImageClick = { index -> openFullScreen(index) }
        )

        binding.rvGallery.layoutManager = GridLayoutManager(this, 3)
        binding.rvGallery.adapter = adapter

        binding.toolbar.overflowIcon?.setTint(
            MaterialColors.getColor(this, com.google.android.material.R.attr.colorControlNormal, Color.BLACK)
        )

        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_settings) {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            } else false
        }

        binding.btnOpenSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = Prefs(this)
        val newRoot = prefs.galleryRootUri

        if (newRoot != rootUri) {
            rootUri = newRoot
            pathStack.clear()
            FolderCache.invalidateAll()
        } else if (pathStack.isNotEmpty()) {
            FolderCache.invalidate(pathStack.last().uri)
        }

        if (rootUri != null) {
            binding.emptyState.visibility = View.GONE
            binding.rvGallery.visibility = View.VISIBLE
            binding.breadcrumbScroll.visibility = View.VISIBLE

            if (pathStack.isEmpty()) {
                pathStack.add(PathSegment(getString(R.string.app_name), rootUri!!))
            }
            loadCurrentLevel()
        } else {
            binding.emptyState.visibility = View.VISIBLE
            binding.rvGallery.visibility = View.GONE
            binding.breadcrumbScroll.visibility = View.GONE
        }
    }

    private fun navigateInto(folder: GalleryAdapter.FolderEntry) {
        pathStack.add(PathSegment(folder.name, folder.uri))
        loadCurrentLevel()
    }

    private fun loadCurrentLevel() {
        val uri = pathStack.last().uri
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE

        updateBreadcrumb()
        binding.toolbar.title = pathStack.last().name

        Thread {
            val cached = FolderCache.get(uri)
            val entries: List<FolderCache.CachedEntry>

            if (cached != null) {
                entries = cached
            } else {
                val doc = DocumentFile.fromTreeUri(this, uri)
                val result = mutableListOf<FolderCache.CachedEntry>()

                doc?.listFiles()?.forEach { file ->
                    val name = file.name ?: return@forEach
                    if (file.isDirectory) {
                        val count = countImagesRecursive(file)
                        result.add(FolderCache.CachedEntry(name, file.uri.toString(), true, count))
                    } else {
                        val ext = name.substringAfterLast('.', "").lowercase()
                        if (ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")) {
                            result.add(FolderCache.CachedEntry(name, file.uri.toString(), false))
                        }
                    }
                }

                FolderCache.put(uri, result)
                entries = result
            }

            val folders = entries
                .filter { it.isDirectory && it.imageCount > 0 }
                .map { GalleryAdapter.FolderEntry(it.name, Uri.parse(it.uri), it.imageCount) }

            val images = mutableListOf<GalleryAdapter.ImageEntry>()
            var imgIndex = 0
            entries.filter { !it.isDirectory }.forEach {
                images.add(GalleryAdapter.ImageEntry(Uri.parse(it.uri), it.name, imgIndex++))
            }

            runOnUiThread {
                binding.progressBar.visibility = View.GONE
                if (folders.isEmpty() && images.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.tvEmpty.text = getString(R.string.gallery_empty)
                }
                adapter.submitData(folders, images)
            }
        }.start()
    }

    private fun countImagesRecursive(dir: DocumentFile): Int {
        var count = 0
        dir.listFiles().forEach { file ->
            if (file.isDirectory) {
                count += countImagesRecursive(file)
            } else {
                val name = file.name ?: return@forEach
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")) {
                    count++
                }
            }
        }
        return count
    }

    private fun openFullScreen(index: Int) {
        val images = adapter.getImages()
        if (images.isEmpty()) return

        val uris = images.map { it.uri.toString() }.toTypedArray()
        val intent = Intent(this, FullScreenImageActivity::class.java).apply {
            putExtra("uris", uris)
            putExtra("position", index)
        }
        startActivity(intent)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (pathStack.size > 1) {
            pathStack.removeLast()
            loadCurrentLevel()
        } else {
            super.onBackPressed()
        }
    }

    private fun updateBreadcrumb() {
        binding.breadcrumb.removeAllViews()
        if (pathStack.size <= 1) {
            binding.breadcrumbScroll.visibility = View.GONE
            return
        }
        binding.breadcrumbScroll.visibility = View.VISIBLE

        val activeColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, android.graphics.Color.WHITE)
        val inactiveColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, android.graphics.Color.GRAY)

        for (i in pathStack.indices) {
            if (i > 0) {
                val sep = TextView(this).apply {
                    text = "  >  "
                    textSize = 13f
                    setPadding(4, 0, 4, 0)
                }
                binding.breadcrumb.addView(sep)
            }

            val segment = pathStack[i]
            val tv = TextView(this).apply {
                text = segment.name
                textSize = 13f
                isAllCaps = i == 0
                setPadding(8, 4, 8, 4)
                setTextColor(if (i == pathStack.lastIndex) activeColor else inactiveColor)
                if (i < pathStack.lastIndex) {
                    setOnClickListener {
                        while (pathStack.size > i + 1) pathStack.removeLast()
                        loadCurrentLevel()
                    }
                }
            }
            binding.breadcrumb.addView(tv)
        }

        binding.breadcrumbScroll.post {
            binding.breadcrumbScroll.fullScroll(View.FOCUS_RIGHT)
        }
    }
}
