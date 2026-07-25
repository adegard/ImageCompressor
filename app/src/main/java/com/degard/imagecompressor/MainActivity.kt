package com.degard.imagecompressor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.GridLayoutManager
import com.degard.imagecompressor.databinding.ActivityMainBinding

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
            val doc = DocumentFile.fromTreeUri(this, uri)
            val folders = mutableListOf<GalleryAdapter.FolderEntry>()
            val images = mutableListOf<GalleryAdapter.ImageEntry>()
            var imgIndex = 0

            doc?.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    val name = file.name ?: return@forEach
                    val count = countImagesRecursive(file)
                    if (count > 0) {
                        folders.add(GalleryAdapter.FolderEntry(name, file.uri, count))
                    }
                } else {
                    val name = file.name ?: return@forEach
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")) {
                        images.add(GalleryAdapter.ImageEntry(file.uri, name, imgIndex++))
                    }
                }
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
                setTextColor(getColor(if (i == pathStack.lastIndex) android.R.color.white else android.R.color.darker_gray))
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
