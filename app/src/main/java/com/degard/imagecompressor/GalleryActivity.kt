package com.degard.imagecompressor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.GridLayoutManager
import com.degard.imagecompressor.databinding.ActivityGalleryBinding

class GalleryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGalleryBinding
    private lateinit var adapter: GalleryAdapter
    private lateinit var rootUri: Uri

    private data class PathSegment(val name: String, val uri: Uri)
    private val pathStack = mutableListOf<PathSegment>()

    private val fullScreenLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* return from full screen, gallery will reload on resume */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uriStr = intent.getStringExtra("folder_uri") ?: run { finish(); return }
        rootUri = Uri.parse(uriStr)

        adapter = GalleryAdapter(
            onFolderClick = { folder -> navigateInto(folder) },
            onImageClick = { index -> openFullScreen(index) }
        )

        binding.rvGallery.layoutManager = GridLayoutManager(this, 3)
        binding.rvGallery.adapter = adapter

        binding.toolbar.setNavigationOnClickListener { finish() }

        pathStack.add(PathSegment(getString(R.string.gallery_title), rootUri))
        loadCurrentLevel()

        @Suppress("DEPRECATION")
        onBackPressedDispatcher.addCallback(this) {
            if (pathStack.size > 1) {
                pathStack.removeLast()
                loadCurrentLevel()
            } else {
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadCurrentLevel()
    }

    private fun navigateInto(folder: GalleryAdapter.FolderEntry) {
        pathStack.add(PathSegment(folder.name, folder.uri))
        loadCurrentLevel()
    }

    private fun loadCurrentLevel() {
        val (_, uri) = pathStack.last()
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
        fullScreenLauncher.launch(intent)
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
