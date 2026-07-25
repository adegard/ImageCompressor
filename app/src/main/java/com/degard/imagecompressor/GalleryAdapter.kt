package com.degard.imagecompressor

import android.graphics.BitmapFactory
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.degard.imagecompressor.databinding.GalleryFolderItemBinding
import com.degard.imagecompressor.databinding.GalleryItemBinding

class GalleryAdapter(
    private val onFolderClick: (FolderEntry) -> Unit,
    private val onImageClick: (Int) -> Unit,
    private val onFolderLongClick: (FolderEntry) -> Unit = {},
    private val onSelectionModeChanged: (Boolean, Int) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_FOLDER = 0
        const val TYPE_IMAGE = 1
    }

    data class FolderEntry(val name: String, val treeUri: Uri, val documentId: String, val childCount: Int, val tag: String? = null)
    data class ImageEntry(val uri: Uri, val name: String, val index: Int, val hasTag: Boolean = false)

    private val items = mutableListOf<Any>()
    val selectedPositions = mutableSetOf<Int>()
    var isSelectionMode = false
        private set

    fun submitData(folders: List<FolderEntry>, images: List<ImageEntry>) {
        items.clear()
        items.addAll(folders)
        items.addAll(images)
        notifyDataSetChanged()
    }

    fun getImages(): List<ImageEntry> = items.filterIsInstance<ImageEntry>()

    fun getSelectedUris(): List<Uri> {
        return selectedPositions.mapNotNull { pos ->
            items.getOrNull(pos)?.let { if (it is ImageEntry) it.uri else null }
        }
    }

    fun toggleSelection(position: Int) {
        if (position < 0 || position >= items.size) return
        if (items[position] !is ImageEntry) return

        if (selectedPositions.contains(position)) {
            selectedPositions.remove(position)
        } else {
            selectedPositions.add(position)
        }
        notifyItemChanged(position)
        checkSelectionState()
    }

    fun enterSelectionMode(position: Int) {
        isSelectionMode = true
        selectedPositions.clear()
        selectedPositions.add(position)
        notifyDataSetChanged()
        onSelectionModeChanged(true, selectedPositions.size)
    }

    fun exitSelectionMode() {
        isSelectionMode = false
        val prev = selectedPositions.toSet()
        selectedPositions.clear()
        for (pos in prev) notifyItemChanged(pos)
        onSelectionModeChanged(false, 0)
    }

    fun updateHasTag(position: Int, hasTag: Boolean) {
        val item = items.getOrNull(position) ?: return
        if (item is ImageEntry) {
            items[position] = item.copy(hasTag = hasTag)
            notifyItemChanged(position)
        }
    }

    private fun checkSelectionState() {
        if (selectedPositions.isEmpty()) {
            exitSelectionMode()
        } else {
            onSelectionModeChanged(true, selectedPositions.size)
        }
    }

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
            if (!folder.tag.isNullOrEmpty()) {
                b.tvFolderTag.text = folder.tag
                b.tvFolderTag.visibility = View.VISIBLE
            } else {
                b.tvFolderTag.visibility = View.GONE
            }
            b.root.setOnClickListener { onFolderClick(folder) }
            b.root.setOnLongClickListener {
                onFolderLongClick(folder)
                true
            }
        }
    }

    inner class ImageVH(private val b: GalleryItemBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(entry: ImageEntry) {
            b.tvFileName.text = entry.name
            b.ivThumb.setImageBitmap(null)
            b.ivThumb.tag = entry.uri

            b.ivTagIcon.visibility = if (entry.hasTag) View.VISIBLE else View.GONE

            b.cbSelect.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            b.cbSelect.isChecked = selectedPositions.contains(adapterPosition)

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

            if (isSelectionMode) {
                b.root.setOnClickListener { toggleSelection(adapterPosition) }
                b.root.setOnLongClickListener(null)
            } else {
                b.root.setOnClickListener { onImageClick(entry.index) }
                b.root.setOnLongClickListener {
                    enterSelectionMode(adapterPosition)
                    true
                }
            }
        }
    }
}
