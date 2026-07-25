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
    private val savedMatrix = Matrix()

    private var mode = NONE
    private val startPoint = PointF()
    private var midPoint = PointF()
    private var oldDist = 1f

    private var baseScale = 1f
    private var currentScale = 1f
    private val maxScale = 5f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val factor = detector.scaleFactor
            val newScale = (currentScale * factor).coerceIn(baseScale, maxScale)
            val realFactor = newScale / currentScale
            currentScale = newScale
            imgMatrix.postScale(realFactor, realFactor, detector.focusX, detector.focusY)
            constrain()
            imageMatrix = imgMatrix
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            if (currentScale < baseScale) {
                animateTo(baseScale)
            }
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
            if (currentScale > baseScale) {
                imgMatrix.postTranslate(-dx, -dy)
                constrain()
                imageMatrix = imgMatrix
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
        var handled = scaleDetector.onTouchEvent(event)
        handled = gestureDetector.onTouchEvent(event) || handled

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mode = DRAG
                startPoint.set(event.x, event.y)
                savedMatrix.set(imgMatrix)
                if (currentScale > baseScale) {
                    parent.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                mode = ZOOM
                oldDist = spacing(event)
                midPoint = mid(event)
                savedMatrix.set(imgMatrix)
                parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == ZOOM && event.pointerCount >= 2) {
                    val newDist = spacing(event)
                    if (newDist > 10f) {
                        val factor = (newDist / oldDist).coerceIn(0.5f, 2.0f)
                        val newScale = (currentScale * factor).coerceIn(baseScale, maxScale)
                        val realFactor = newScale / currentScale
                        currentScale = newScale
                        imgMatrix.set(savedMatrix)
                        imgMatrix.postScale(realFactor, realFactor, midPoint.x, midPoint.y)
                        constrain()
                        imageMatrix = imgMatrix
                        oldDist = newDist
                        handled = true
                    }
                } else if (mode == DRAG && currentScale > baseScale) {
                    val dx = event.x - startPoint.x
                    val dy = event.y - startPoint.y
                    imgMatrix.set(savedMatrix)
                    imgMatrix.postTranslate(dx, dy)
                    constrain()
                    imageMatrix = imgMatrix
                    handled = true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val wasZooming = mode == ZOOM
                mode = NONE
                if (currentScale <= baseScale) {
                    parent.requestDisallowInterceptTouchEvent(false)
                }
            }
        }
        return handled || super.onTouchEvent(event)
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

        val animRunnable = object : Runnable {
            override fun run() {
                val t = ((System.currentTimeMillis() - startTime).toFloat() / duration).coerceAtMost(1f)
                val eased = 1f - (1f - t) * (1f - t)
                val s = startScale + (targetScale - startScale) * eased
                val factor = s / currentScale
                currentScale = s
                imgMatrix.set(startMatrix)
                imgMatrix.postScale(factor, factor, focusX, focusY)
                constrain()
                imageMatrix = imgMatrix
                if (t < 1f) postOnAnimation(this)
            }
        }
        removeCallbacks(null)
        postOnAnimation(animRunnable)
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

    private fun spacing(e: MotionEvent): Float {
        val dx = e.getX(0) - e.getX(1)
        val dy = e.getY(0) - e.getY(1)
        return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    private fun mid(e: MotionEvent): PointF =
        PointF((e.getX(0) + e.getX(1)) / 2f, (e.getY(0) + e.getY(1)) / 2f)

    companion object {
        private const val NONE = 0
        private const val DRAG = 1
        private const val ZOOM = 2
    }
}
