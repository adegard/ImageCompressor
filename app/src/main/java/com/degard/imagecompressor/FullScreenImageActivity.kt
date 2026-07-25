package com.degard.imagecompressor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.degard.imagecompressor.databinding.ActivityFullscreenBinding

class FullScreenImageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFullscreenBinding
    private val uris = mutableListOf<Uri>()
    private var currentPosition = 0
    private val rotations = mutableMapOf<Int, Float>()
    private var barsVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFullscreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        intent.getStringArrayExtra("uris")?.forEach { uris.add(Uri.parse(it)) }
        currentPosition = intent.getIntExtra("position", 0)

        if (uris.isEmpty()) { finish(); return }

        val adapter = FullScreenPagerAdapter()
        binding.viewPager.adapter = adapter
        binding.viewPager.setCurrentItem(currentPosition, false)

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPosition = position
                updateTitle()
            }
        })

        binding.toolbar.setNavigationOnClickListener { finish() }
        updateTitle()

        binding.viewPager.setOnClickListener { toggleBars() }

        binding.btnDelete.setOnClickListener { confirmDelete() }
        binding.btnRotate.setOnClickListener { rotateCurrent() }

        toggleBars()
    }

    private fun updateTitle() {
        val name = DocumentFile.fromSingleUri(this, uris[currentPosition])?.name ?: ""
        binding.toolbar.title = "${currentPosition + 1}/${uris.size}  -  $name"
    }

    private fun toggleBars() {
        barsVisible = !barsVisible
        val alpha = if (barsVisible) 1f else 0f
        binding.toolbar.animate().alpha(alpha).setDuration(200).start()
        binding.bottomBar.animate().alpha(alpha).setDuration(200).start()
        binding.toolbar.visibility = if (barsVisible) View.VISIBLE else View.GONE
        binding.bottomBar.visibility = if (barsVisible) View.VISIBLE else View.GONE
    }

    private fun confirmDelete() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.confirm_delete_title)
            .setMessage(R.string.confirm_delete_msg)
            .setPositiveButton(R.string.delete) { _, _ -> deleteCurrent() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteCurrent() {
        val uri = uris[currentPosition]
        DocumentFile.fromSingleUri(this, uri)?.delete()
        uris.removeAt(currentPosition)
        rotations.remove(currentPosition)

        if (uris.isEmpty()) {
            finish()
            return
        }

        if (currentPosition >= uris.size) currentPosition = uris.size - 1

        binding.viewPager.adapter?.notifyDataSetChanged()
        binding.viewPager.setCurrentItem(currentPosition, false)
        updateTitle()
    }

    private fun rotateCurrent() {
        val current = rotations[currentPosition] ?: 0f
        rotations[currentPosition] = current + 90f

        for (i in 0 until binding.viewPager.childCount) {
            val child = binding.viewPager.getChildAt(i)
            val iv = child.findViewById<ImageView>(R.id.ivFull)
            if (iv != null) {
                iv.rotation = current + 90f
                break
            }
        }
    }

    private fun saveRotation(uri: Uri, degrees: Float) {
        if (degrees % 360f == 0f) return
        try {
            val resolver = contentResolver
            val bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return

            val matrix = Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()

            resolver.openOutputStream(uri, "w")?.use { out ->
                rotated.compress(Bitmap.CompressFormat.WEBP_LOSSY, Prefs(this).quality, out)
            }
            rotated.recycle()
        } catch (_: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        for ((pos, deg) in rotations) {
            if (deg % 360f != 0f && pos < uris.size) {
                saveRotation(uris[pos], deg)
            }
        }
    }

    inner class FullScreenPagerAdapter : RecyclerView.Adapter<FullScreenPagerAdapter.PageVH>() {

        override fun getItemCount() = uris.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageVH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.fullscreen_page, parent, false)
            return PageVH(view)
        }

        override fun onBindViewHolder(holder: PageVH, position: Int) {
            val iv = holder.itemView.findViewById<ImageView>(R.id.ivFull)
            iv.rotation = rotations[position] ?: 0f
            iv.setImageBitmap(null)
            iv.tag = uris[position]

            Thread {
                try {
                    val opts = BitmapFactory.Options().apply {
                        val dm = resources.displayMetrics
                        inSampleSize = calculateSampleSize(dm.widthPixels, dm.heightPixels)
                    }
                    val bitmap = contentResolver.openInputStream(uris[position])?.use {
                        BitmapFactory.decodeStream(it, null, opts)
                    }
                    holder.itemView.post {
                        if (iv.tag == uris[position]) {
                            iv.setImageBitmap(bitmap)
                        } else {
                            bitmap?.recycle()
                        }
                    }
                } catch (_: Exception) {}
            }.start()
        }

        private fun calculateSampleSize(screenW: Int, screenH: Int): Int {
            var sample = 1
            var w = screenW * 2
            var h = screenH * 2
            while (w > screenW * 3 || h > screenH * 3) {
                w /= 2
                h /= 2
                sample *= 2
            }
            return sample
        }

        inner class PageVH(view: View) : RecyclerView.ViewHolder(view)
    }
}
