package com.degard.imagecompressor

import android.graphics.BitmapFactory
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.degard.imagecompressor.databinding.GalleryItemBinding

class GalleryAdapter : ListAdapter<GalleryAdapter.ImageEntry, GalleryAdapter.ViewHolder>(DIFF) {

    data class ImageEntry(val uri: Uri, val name: String, val relativePath: String)

    class ViewHolder(val binding: GalleryItemBinding) : RecyclerView.ViewHolder(binding.root)

    private object DIFF : DiffUtil.ItemCallback<ImageEntry>() {
        override fun areItemsTheSame(a: ImageEntry, b: ImageEntry) = a.uri == b.uri
        override fun areContentsTheSame(a: ImageEntry, b: ImageEntry) = a == b
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = GalleryItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = getItem(position)
        holder.binding.tvFileName.text = entry.name
        if (entry.relativePath.isNotEmpty()) {
            holder.binding.tvFolderPath.text = entry.relativePath
            holder.binding.tvFolderPath.visibility = android.view.View.VISIBLE
        } else {
            holder.binding.tvFolderPath.visibility = android.view.View.GONE
        }

        holder.binding.ivThumb.setImageBitmap(null)
        holder.binding.ivThumb.tag = entry.uri

        val ctx = holder.itemView.context
        Thread {
            try {
                val resolver = ctx.contentResolver
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                val bitmap = resolver.openInputStream(entry.uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
                holder.itemView.post {
                    if (holder.binding.ivThumb.tag == entry.uri) {
                        holder.binding.ivThumb.setImageBitmap(bitmap)
                    } else {
                        bitmap?.recycle()
                    }
                }
            } catch (_: Exception) {}
        }.start()
    }
}
