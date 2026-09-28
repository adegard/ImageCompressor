package com.degard.imagecompressor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
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
    private val pendingSave = mutableMapOf<Uri, Int>()
    private val writing = mutableMapOf<Uri, Int>()
    private var shareAfterSave: Uri? = null
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

    override fun onStop() {
        super.onStop()
        for (uri in pendingSave.keys.toList()) {
            if (!writing.containsKey(uri)) startSave(uri)
        }
    }

    override fun onDestroy() {
        clearBitmaps()
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
        val removed = currentPosition
        uris.removeAt(removed)
        pendingSave.remove(uri)
        writing.remove(uri)
        shiftPositions(removed)
        clearBitmaps()

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

    private fun shiftPositions(removed: Int) {
        for (map in listOf(rotations, baseExif)) {
            val stale = map.keys.filter { it > removed }
            stale.forEach { map.remove(it) }
            val moved = map.entries.map { (k, v) -> (k - 1) to v }
            map.clear()
            map.putAll(moved)
        }
    }

    private fun shareCurrent() {
        val uri = uris.getOrNull(currentPosition) ?: return
        if (writing.containsKey(uri)) {
            shareAfterSave = uri
            return
        }
        launchShare(uri)
    }

    private fun launchShare(uri: Uri) {
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(android.content.Intent.createChooser(shareIntent, getString(R.string.share)))
    }

    private fun effectiveRotation(position: Int): Int =
        ((((baseExif[position] ?: 0) + (rotations[position] ?: 0)) % 360) + 360) % 360

    private fun rotateCurrent() {
        val pos = currentPosition
        val uri = uris.getOrNull(pos) ?: return
        if (!canWrite(uri)) {
            Toast.makeText(this, R.string.rotate_no_permission, Toast.LENGTH_LONG).show()
            return
        }
        rotations[pos] = ((rotations[pos] ?: 0) + 90) % 360
        pageImageView(pos)?.let { applyDisplayBitmap(it, pos) }
        requestSave(uri, effectiveRotation(pos))
    }

    private fun canWrite(uri: Uri): Boolean = try {
        contentResolver.openFileDescriptor(uri, "rw")?.use { true } ?: false
    } catch (_: Throwable) {
        false
    }

    private fun requestSave(uri: Uri, degrees: Int) {
        pendingSave[uri] = ((degrees % 360) + 360) % 360
        if (writing.containsKey(uri)) return
        startSave(uri)
    }

    private fun startSave(uri: Uri) {
        val deg = pendingSave[uri] ?: return
        writing[uri] = deg
        Thread {
            val result = try {
                val name = DocumentFile.fromSingleUri(this@FullScreenImageActivity, uri)?.name
                if (name == null) {
                    ImageUtil.RotationResult.FAILED
                } else {
                    ImageUtil.persistRotation(this@FullScreenImageActivity, uri, name, deg)
                }
            } catch (_: Throwable) {
                ImageUtil.RotationResult.FAILED
            }
            runOnUiThread { onSaveFinished(uri, deg, result) }
        }.start()
    }

    private fun onSaveFinished(uri: Uri, savedDegrees: Int, result: ImageUtil.RotationResult) {
        writing.remove(uri)
        if (isDestroyed) return

        val pos = uris.indexOf(uri)
        if (pos >= 0) {
            when (result) {
                ImageUtil.RotationResult.FAILED -> {
                    rotations[pos] = 0
                    pageImageView(pos)?.let { applyDisplayBitmap(it, pos) }
                    Toast.makeText(this, R.string.rotate_failed, Toast.LENGTH_SHORT).show()
                }
                ImageUtil.RotationResult.EXIF_ONLY -> {
                    baseExif[pos] = ImageUtil.readExifDegrees(this, uri)
                    rotations[pos] = 0
                    pageImageView(pos)?.let { applyDisplayBitmap(it, pos) }
                    FolderCache.invalidateAll()
                }
                ImageUtil.RotationResult.REENCODED -> {
                    baseExif[pos] = ImageUtil.readExifDegrees(this, uri)
                    rotations[pos] = 0
                    reloadPosition(pos)
                    FolderCache.invalidateAll()
                }
            }
        }

        val queued = pendingSave[uri]
        if (queued != null && queued != savedDegrees) {
            startSave(uri)
        } else {
            pendingSave.remove(uri)
        }

        if (writing.isEmpty()) {
            shareAfterSave?.let {
                shareAfterSave = null
                launchShare(it)
            }
        }
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
        val total = effectiveRotation(position)
        val shown = if (total == 0) raw else ImageUtil.rotate(raw, total, recycleSource = false)

        val prev = displayedBitmap[position]
        displayedBitmap[position] = shown
        iv.setImageBitmap(shown)

        if (prev != null && prev !== shown && prev !== raw && !prev.isRecycled) {
            iv.post {
                val current = iv.drawable
                if (current !is BitmapDrawable || current.bitmap !== prev) prev.recycle()
            }
        }
    }

    private fun reloadPosition(position: Int) {
        val raw = bitmapCache.remove(position)
        val shown = displayedBitmap.remove(position)
        val iv = pageImageView(position)
        iv?.setImageBitmap(null)
        if (shown != null && shown !== raw && !shown.isRecycled) shown.recycle()
        if (raw != null && !raw.isRecycled) raw.recycle()
        if (iv != null) loadBitmap(position, iv)
    }

    private fun clearBitmaps() {
        if (!::binding.isInitialized) {
            bitmapCache.clear()
            displayedBitmap.clear()
            return
        }
        val rv = binding.viewPager.getChildAt(0) as? RecyclerView
        if (rv != null) {
            for (i in 0 until rv.childCount) {
                rv.getChildAt(i).findViewById<ImageView>(R.id.ivFull)?.setImageDrawable(null)
            }
        }
        val all = bitmapCache.values.toSet() + displayedBitmap.values
        bitmapCache.clear()
        displayedBitmap.clear()
        for (b in all) if (!b.isRecycled) b.recycle()
    }

    private fun loadBitmap(position: Int, iv: ImageView) {
        val uri = uris.getOrNull(position) ?: return
        iv.setImageBitmap(null)
        iv.tag = uri
        Thread {
            val dm = resources.displayMetrics
            val opts = BitmapFactory.Options().apply {
                inSampleSize = calculateSampleSize(dm.widthPixels, dm.heightPixels)
            }
            val raw = try {
                contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
            } catch (_: Throwable) {
                null
            }
            val exifDeg = ImageUtil.readExifDegrees(this@FullScreenImageActivity, uri)
            iv.post {
                if (raw == null) return@post
                if (iv.tag != uri || isDestroyed) {
                    raw.recycle()
                    return@post
                }
                val stale = bitmapCache.put(position, raw)
                if (stale != null && stale !== displayedBitmap[position] && !stale.isRecycled) {
                    stale.recycle()
                }
                baseExif[position] = exifDeg
                applyDisplayBitmap(iv, position)
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

    inner class FullScreenPagerAdapter : RecyclerView.Adapter<FullScreenPagerAdapter.PageVH>() {

        override fun getItemCount() = uris.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageVH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.fullscreen_page, parent, false)
            return PageVH(view)
        }

        override fun onBindViewHolder(holder: PageVH, position: Int) {
            val iv = holder.itemView.findViewById<ImageView>(R.id.ivFull)
            loadBitmap(position, iv)
        }

        inner class PageVH(view: View) : RecyclerView.ViewHolder(view)
    }
}
