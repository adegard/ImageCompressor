package com.degard.imagecompressor

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.GridLayoutManager
import com.degard.imagecompressor.databinding.ActivityGalleryBinding

class GalleryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGalleryBinding
    private val adapter = GalleryAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.rvGallery.layoutManager = GridLayoutManager(this, 3)
        binding.rvGallery.adapter = adapter

        val uriStr = intent.getStringExtra("folder_uri") ?: run {
            finish()
            return
        }

        val folderUri = Uri.parse(uriStr)
        loadImages(folderUri)
    }

    private fun loadImages(folderUri: Uri) {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvGalleryStatus.visibility = View.GONE

        Thread {
            val entries = mutableListOf<GalleryAdapter.ImageEntry>()
            val rootDoc = DocumentFile.fromTreeUri(this, folderUri)

            fun walkDir(dir: DocumentFile, relativePath: String) {
                dir.listFiles().forEach { file ->
                    if (file.isDirectory) {
                        val subName = file.name ?: "sub"
                        val subPath = if (relativePath.isEmpty()) subName else "$relativePath/$subName"
                        walkDir(file, subPath)
                        return@forEach
                    }

                    val name = file.name ?: return@forEach
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")) {
                        entries.add(GalleryAdapter.ImageEntry(file.uri, name, relativePath))
                    }
                }
            }

            if (rootDoc != null) {
                walkDir(rootDoc, "")
            }

            runOnUiThread {
                binding.progressBar.visibility = View.GONE
                if (entries.isEmpty()) {
                    binding.tvGalleryStatus.visibility = View.VISIBLE
                    binding.tvGalleryStatus.text = getString(R.string.gallery_empty)
                }
                adapter.submitList(entries.toList())
            }
        }.start()
    }
}
