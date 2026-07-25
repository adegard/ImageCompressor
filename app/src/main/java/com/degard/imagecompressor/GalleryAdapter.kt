package com.degard.imagecompressor

import android.graphics.BitmapFactory
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.degard.imagecompressor.databinding.GalleryFolderItemBinding
import com.degard.imagecompressor.databinding.GalleryItemBinding

class GalleryAdapter(
    private val onFolderClick: (FolderEntry) -> Unit,
    private val onImageClick: (Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_FOLDER = 0
        const val TYPE_IMAGE = 1
    }

    data class FolderEntry(val name: String, val treeUri: Uri, val documentId: String, val childCount: Int)
    data class ImageEntry(val uri: Uri, val name: String, val index: Int)

    private val items = mutableListOf<Any>()

    fun submitData(folders: List<FolderEntry>, images: List<ImageEntry>) {
        items.clear()
        items.addAll(folders)
        items.addAll(images)
        notifyDataSetChanged()
    }

    fun getImages(): List<ImageEntry> = items.filterIsInstance<ImageEntry>()

    override fun getItemViewType(position: Int): Int =
        if (items[position] is FolderEntry) TYPE_FOLDER else TYPE_IMAGE

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_FOLDER) {
            FolderVH(GalleryFolderItemBinding.inflate(inflater, parent, false))
        } else {
            ImageVH(GalleryItemBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is FolderEntry -> (holder as FolderVH).bind(item)
            is ImageEntry -> (holder as ImageVH).bind(item)
        }
    }

    inner class FolderVH(private val b: GalleryFolderItemBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(folder: FolderEntry) {
            b.tvFolderName.text = folder.name
            b.tvFolderCount.text = itemView.context.getString(R.string.gallery_folder)
            b.root.setOnClickListener { onFolderClick(folder) }
        }
    }

    inner class ImageVH(private val b: GalleryItemBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(entry: ImageEntry) {
            b.tvFileName.text = entry.name
            b.ivThumb.setImageBitmap(null)
            b.ivThumb.tag = entry.uri

            val ctx = itemView.context
            Thread {
                try {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                    val bitmap = ctx.contentResolver.openInputStream(entry.uri)?.use {
                        BitmapFactory.decodeStream(it, null, opts)
                    }
                    itemView.post {
                        if (b.ivThumb.tag == entry.uri) {
                            b.ivThumb.setImageBitmap(bitmap)
                        } else {
                            bitmap?.recycle()
                        }
                    }
                } catch (_: Exception) {}
            }.start()

            b.root.setOnClickListener { onImageClick(entry.index) }
        }
    }
}
