package com.degard.imagecompressor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.degard.imagecompressor.databinding.ActivityFullscreenBinding
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class FullScreenImageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFullscreenBinding
    private val uris = mutableListOf<Uri>()
    private var currentPosition = 0
    private val rotations = mutableMapOf<Int, Int>()
    private val baseExif = mutableMapOf<Int, Int>()
    private val bitmapCache = mutableMapOf<Int, Bitmap>()
    private val displayedBitmap = mutableMapOf<Int, Bitmap>()
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
                updateTagBadge()
            }
        })

        binding.toolbar.setNavigationOnClickListener { finish() }
        updateTitle()
        updateTagBadge()

        binding.btnDelete.setOnClickListener { confirmDelete() }
        binding.btnRotate.setOnClickListener { rotateCurrent() }
        binding.btnShare.setOnClickListener { shareCurrent() }
        binding.btnTag.setOnClickListener { showTagDialog() }

        setupTapToToggle()
    }

    override fun onDestroy() {
        val raws: MutableSet<Bitmap> = mutableSetOf()
        raws.addAll(bitmapCache.values)
        val toRecycle = mutableSetOf<Bitmap>()
        toRecycle.addAll(raws)
        for (b in displayedBitmap.values) {
            if (b !in raws) toRecycle.add(b)
        }
        toRecycle.forEach { it.recycle() }
        super.onDestroy()
    }

    private fun setupTapToToggle() {
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                toggleBars()
                return true
            }
        })

        binding.viewPager.post {
            val child = binding.viewPager.getChildAt(0) ?: return@post
            if (child is RecyclerView) {
                child.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
                    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                        gestureDetector.onTouchEvent(e)
                        return false
                    }
                })
            }
        }
    }

    private fun updateTitle() {
        val name = DocumentFile.fromSingleUri(this, uris[currentPosition])?.name ?: ""
        binding.toolbar.title = "${currentPosition + 1}/${uris.size}  -  $name"
    }

    private fun updateTagBadge() {
        val tags = TagManager.getTags(this, uris[currentPosition])
        if (tags.isNotEmpty()) {
            binding.tvTagBadge.text = tags.joinToString(", ")
            binding.tvTagBadge.visibility = View.VISIBLE
        } else {
            binding.tvTagBadge.visibility = View.GONE
        }
    }

    private fun showTagDialog() {
        val uri = uris[currentPosition]
        val currentTags = TagManager.getTags(this, uri)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val dp16 = (16 * resources.displayMetrics.density).toInt()
            setPadding(dp16, dp16, dp16, 0)
        }

        val input = EditText(this).apply {
            hint = getString(R.string.tag_hint)
            setText(currentTags.joinToString(", "))
            setSelection(text.length)
        }
        layout.addView(input)

        val allExistingTags = mutableSetOf<String>()
        for (u in uris) {
            allExistingTags.addAll(TagManager.getTags(this, u))
        }
        allExistingTags.addAll(currentTags)
        val suggestions = allExistingTags.sorted()

        if (suggestions.isNotEmpty()) {
            val label = TextView(this).apply {
                text = "Existing tags:"
                textSize = 12f
                val dp8 = (8 * resources.displayMetrics.density).toInt()
                setPadding(0, dp8, 0, dp8)
            }
            layout.addView(label)

            val chipGroup = ChipGroup(this).apply {
                isSingleLine = false
            }
            for (tag in suggestions) {
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
            .setTitle(R.string.add_tag)
            .setView(layout)
            .setPositiveButton(R.string.tag) { _, _ ->
                val raw = input.text.toString()
                val tags = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                TagManager.setTags(this, uri, tags)
                updateTagBadge()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun toggleBars() {
        barsVisible = !barsVisible
        val alpha = if (barsVisible) 1f else 0f
        binding.toolbar.animate().alpha(alpha).setDuration(200).start()
        binding.bottomBar.animate().alpha(alpha).setDuration(200).start()
        binding.tvTagBadge.animate().alpha(alpha).setDuration(200).start()
        binding.toolbar.visibility = if (barsVisible) View.VISIBLE else View.GONE
        binding.bottomBar.visibility = if (barsVisible) View.VISIBLE else View.GONE
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.confirm_delete_title)
            .setMessage(R.string.confirm_delete_msg)
            .setPositiveButton(R.string.delete) { _, _ -> deleteCurrent() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteCurrent() {
        val uri = uris[currentPosition]
        DocumentFile.fromSingleUri(this, uri)?.delete()
        FolderCache.getDb()?.deleteImageTag(uri.toString())
        uris.removeAt(currentPosition)
        rotations.remove(currentPosition)
        baseExif.remove(currentPosition)
        bitmapCache.clear()
        displayedBitmap.values.forEach { it.recycle() }
        displayedBitmap.clear()

        if (uris.isEmpty()) {
            finish()
            return
        }

        if (currentPosition >= uris.size) currentPosition = uris.size - 1

        binding.viewPager.adapter?.notifyDataSetChanged()
        binding.viewPager.setCurrentItem(currentPosition, false)
        updateTitle()
        updateTagBadge()
    }

    private fun shareCurrent() {
        val uri = uris[currentPosition]
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(android.content.Intent.createChooser(shareIntent, getString(R.string.share)))
    }

    private fun rotateCurrent() {
        val current = rotations[currentPosition] ?: 0
        rotations[currentPosition] = (current + 90) % 360
        val iv = pageImageView(currentPosition) ?: return
        applyDisplayBitmap(iv, currentPosition)
    }

    private fun pageImageView(position: Int): ImageView? {
        val rv = binding.viewPager.getChildAt(0) as? RecyclerView ?: return null
        for (i in 0 until rv.childCount) {
            val child = rv.getChildAt(i)
            if (rv.getChildAdapterPosition(child) == position) {
                return child.findViewById(R.id.ivFull)
            }
        }
        return null
    }

    private fun applyDisplayBitmap(iv: ImageView, position: Int) {
        val raw = bitmapCache[position] ?: return
        val total = ((baseExif[position] ?: 0) + (rotations[position] ?: 0)) % 360
        val shown = if (total == 0) raw else ImageUtil.rotate(raw, total, recycleSource = false)

        val prev = displayedBitmap[position]
        if (prev != null && prev !== shown && prev !== raw) prev.recycle()
        displayedBitmap[position] = shown
        iv.setImageBitmap(shown)
    }

    private fun persistRotation(uri: Uri, degrees: Int) {
        val doc = DocumentFile.fromSingleUri(this, uri) ?: return
        val name = doc.name ?: return
        val deg = ((degrees % 360) + 360) % 360

        if (ImageUtil.isJpeg(name)) {
            if (writeExifOrientation(uri, deg)) return
            if (deg == 0) return
        } else if (deg == 0) {
            return
        }
        reencodeRotated(doc, name, deg)
    }

    private fun writeExifOrientation(uri: Uri, degrees: Int): Boolean {
        return try {
            val fd = contentResolver.openFileDescriptor(uri, "rw") ?: return false
            fd.use {
                val exif = ExifInterface(it.fileDescriptor)
                exif.setAttribute(
                    ExifInterface.TAG_ORIENTATION,
                    ImageUtil.orientationForDegrees(degrees).toString()
                )
                exif.saveAttributes()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun reencodeRotated(doc: DocumentFile, name: String, degrees: Int) {
        Thread {
            try {
                val bitmap = ImageUtil.decodeRaw(this, doc.uri, 1) ?: return@Thread
                val rotated = ImageUtil.rotate(bitmap, degrees)
                val format = when {
                    ImageUtil.isJpeg(name) -> Bitmap.CompressFormat.JPEG
                    name.endsWith("png", ignoreCase = true) -> Bitmap.CompressFormat.PNG
                    else -> Bitmap.CompressFormat.WEBP_LOSSY
                }
                val quality = if (format == Bitmap.CompressFormat.WEBP_LOSSY) 90 else 95
                contentResolver.openOutputStream(doc.uri, "w")?.use { out ->
                    rotated.compress(format, quality, out)
                }
                if (format == Bitmap.CompressFormat.JPEG) {
                    writeExifOrientation(doc.uri, 0)
                }
                rotated.recycle()
            } catch (_: Exception) {
            }
            runOnUiThread { refreshAfterPersist() }
        }.start()
    }

    private fun refreshAfterPersist() {
        if (isDestroyed || isFinishing) return
        rotations.clear()
        baseExif.clear()
        bitmapCache.clear()
        displayedBitmap.values.forEach { it.recycle() }
        displayedBitmap.clear()
        binding.viewPager.adapter?.notifyDataSetChanged()
        binding.viewPager.setCurrentItem(currentPosition, false)
    }

    override fun onPause() {
        super.onPause()
        val pending = rotations.keys.filter { pos ->
            pos < uris.size && (rotations[pos] ?: 0) % 360 != 0
        }
        if (pending.isEmpty()) return
        for (pos in pending) {
            val total = ((baseExif[pos] ?: ImageUtil.readExifDegrees(this, uris[pos])) + (rotations[pos] ?: 0)) % 360
            rotations[pos] = 0
            if (total % 360 != 0) {
                persistRotation(uris[pos], total)
            }
        }
        refreshAfterPersist()
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
            iv.setImageBitmap(null)
            iv.tag = uris[position]
            val uri = uris[position]

            Thread {
                val opts = BitmapFactory.Options().apply {
                    val dm = resources.displayMetrics
                    inSampleSize = calculateSampleSize(dm.widthPixels, dm.heightPixels)
                }
                val raw = contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
                val exifDeg = ImageUtil.readExifDegrees(this@FullScreenImageActivity, uri)
                holder.itemView.post {
                    if (iv.tag == uri) {
                        bitmapCache[position] = raw ?: return@post
                        baseExif[position] = exifDeg
                        applyDisplayBitmap(iv, position)
                    } else {
                        raw?.recycle()
                    }
                }
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