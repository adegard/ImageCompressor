package com.degard.imagecompressor

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

class ZoomableImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : AppCompatImageView(context, attrs, defStyle) {

    private val imgMatrix = Matrix()
    private var baseScale = 1f
    private var currentScale = 1f
    private val maxScale = 5f
    private var isScaling = false

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isScaling = true
            parent.requestDisallowInterceptTouchEvent(true)
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val factor = detector.scaleFactor
            if (factor.isNaN() || factor <= 0f) return true
            val newScale = (currentScale * factor).coerceIn(baseScale, maxScale)
            val realFactor = newScale / currentScale
            if (realFactor == 1f) return true
            currentScale = newScale
            imgMatrix.postScale(realFactor, realFactor, detector.focusX, detector.focusY)
            constrain()
            imageMatrix = imgMatrix
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isScaling = false
            if (currentScale <= baseScale * 1.05f) {
                animateTo(baseScale)
            }
            parent.requestDisallowInterceptTouchEvent(false)
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (currentScale > baseScale * 1.5f) {
                animateTo(baseScale)
            } else {
                animateTo(baseScale * 3f, e.x, e.y)
            }
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (currentScale > baseScale && !isScaling) {
                imgMatrix.postTranslate(-dx, -dy)
                constrain()
                imageMatrix = imgMatrix
                parent.requestDisallowInterceptTouchEvent(true)
                return true
            }
            return false
        }
    })

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        post { fitCenter() }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isScaling && currentScale <= baseScale) {
                    parent.requestDisallowInterceptTouchEvent(false)
                }
            }
        }
        return true
    }

    private fun fitCenter() {
        val d = drawable ?: return
        val vw = width.toFloat()
        val vh = height.toFloat()
        if (vw == 0f || vh == 0f) return
        val iw = d.intrinsicWidth.toFloat()
        val ih = d.intrinsicHeight.toFloat()
        if (iw <= 0f || ih <= 0f) return

        val scale = minOf(vw / iw, vh / ih)
        baseScale = scale
        currentScale = scale

        val dx = (vw - iw * scale) / 2f
        val dy = (vh - ih * scale) / 2f
        imgMatrix.reset()
        imgMatrix.setScale(scale, scale)
        imgMatrix.postTranslate(dx, dy)
        imageMatrix = imgMatrix
    }

    private fun animateTo(targetScale: Float, focusX: Float = width / 2f, focusY: Float = height / 2f) {
        val startScale = currentScale
        val startMatrix = Matrix(imgMatrix)
        val startTime = System.currentTimeMillis()
        val duration = 300L

        val anim = object : Runnable {
            override fun run() {
                val t = ((System.currentTimeMillis() - startTime).toFloat() / duration).coerceAtMost(1f)
                val eased = 1f - (1f - t) * (1f - t)
                val s = startScale + (targetScale - startScale) * eased
                val factor = if (currentScale > 0f) s / currentScale else 1f
                currentScale = s
                imgMatrix.set(startMatrix)
                imgMatrix.postScale(factor, factor, focusX, focusY)
                constrain()
                imageMatrix = imgMatrix
                if (t < 1f) postOnAnimation(this)
            }
        }
        postOnAnimation(anim)
    }

    private fun constrain() {
        val d = drawable ?: return
        val values = FloatArray(9)
        imgMatrix.getValues(values)
        val sx = values[Matrix.MSCALE_X]
        val imgW = d.intrinsicWidth * sx
        val imgH = d.intrinsicHeight * sx
        val vw = width.toFloat()
        val vh = height.toFloat()

        var tx = values[Matrix.MTRANS_X]
        var ty = values[Matrix.MTRANS_Y]
        tx = if (imgW <= vw) (vw - imgW) / 2f else tx.coerceIn(vw - imgW, 0f)
        ty = if (imgH <= vh) (vh - imgH) / 2f else ty.coerceIn(vh - imgH, 0f)
        imgMatrix.postTranslate(tx - values[Matrix.MTRANS_X], ty - values[Matrix.MTRANS_Y])
    }
}
