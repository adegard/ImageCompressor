package com.degard.imagecompressor

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import androidx.appcompat.widget.AppCompatImageView

class ZoomableImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : AppCompatImageView(context, attrs, defStyle) {

    private val matrix = Matrix()
    private val savedMatrix = Matrix()
    private val matrixValues = FloatArray(9)

    private var mode = NONE
    private val startPoint = PointF()
    private val midPoint = PointF()
    private var oldDist = 1f

    private var minScale = 1f
    private var maxScale = 5f
    private var currentScale = 1f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            currentScale *= scaleFactor
            currentScale = currentScale.coerceIn(minScale, maxScale)
            matrix.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
            constrainMatrix()
            imageMatrix = matrix
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (currentScale > 1.5f) {
                resetZoom()
            } else {
                zoomTo(3f, e.x, e.y)
            }
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (currentScale > 1f) {
                matrix.postTranslate(-dx, -dy)
                constrainMatrix()
                imageMatrix = matrix
            }
            return true
        }
    })

    private val scroller = OverScroller(context)
    private var scrollAction: Runnable? = null

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        post { resetZoom() }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mode = DRAG
                startPoint.set(event.x, event.y)
                savedMatrix.set(matrix)
                stopScroll()
                parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                mode = ZOOM
                oldDist = spacing(event)
                midPoint = mid(event)
                savedMatrix.set(matrix)
                parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == ZOOM && event.pointerCount >= 2) {
                    val newDist = spacing(event)
                    if (newDist > 10f) {
                        val scale = newDist / oldDist
                        matrix.set(savedMatrix)
                        currentScale *= scale
                        currentScale = currentScale.coerceIn(minScale, maxScale)
                        matrix.postScale(scale, scale, midPoint.x, midPoint.y)
                        constrainMatrix()
                        imageMatrix = matrix
                        oldDist = newDist
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                mode = NONE
                if (currentScale < minScale) {
                    resetZoom()
                }
                if (currentScale <= 1f) {
                    parent.requestDisallowInterceptTouchEvent(false)
                }
            }
        }
        return true
    }

    private fun resetZoom() {
        currentScale = minScale
        matrix.reset()
        centerImage()
        imageMatrix = matrix
    }

    private fun zoomTo(targetScale: Float, focusX: Float, focusY: Float) {
        val startScale = currentScale
        val startTime = System.currentTimeMillis()
        val duration = 300L

        scrollAction?.let { removeCallbacks(it) }
        scrollAction = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - startTime
                val t = (elapsed.toFloat() / duration).coerceAtMost(1f)
                val eased = 1f - (1f - t) * (1f - t)
                val newScale = startScale + (targetScale - startScale) * eased
                val factor = newScale / currentScale
                currentScale = newScale
                matrix.postScale(factor, factor, focusX, focusY)
                constrainMatrix()
                imageMatrix = matrix
                if (t < 1f) postOnAnimation(this)
            }
        }
        postOnAnimation(scrollAction!!)
    }

    private fun centerImage() {
        val d = drawable ?: return
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val imgWidth = d.intrinsicWidth.toFloat()
        val imgHeight = d.intrinsicHeight.toFloat()

        val scale = minOf(viewWidth / imgWidth, viewHeight / imgHeight, 1f)
        currentScale = scale
        minScale = scale

        val dx = (viewWidth - imgWidth * scale) / 2f
        val dy = (viewHeight - imgHeight * scale) / 2f
        matrix.setScale(scale, scale)
        matrix.postTranslate(dx, dy)
    }

    private fun constrainMatrix() {
        val d = drawable ?: return
        val values = FloatArray(9)
        matrix.getValues(values)
        val scaleX = values[Matrix.MSCALE_X]
        val imgWidth = d.intrinsicWidth * scaleX
        val imgHeight = d.intrinsicHeight * scaleX

        var transX = values[Matrix.MTRANS_X]
        var transY = values[Matrix.MTRANS_Y]

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        if (imgWidth <= viewWidth) {
            transX = (viewWidth - imgWidth) / 2f
        } else {
            transX = transX.coerceIn(viewWidth - imgWidth, 0f)
        }

        if (imgHeight <= viewHeight) {
            transY = (viewHeight - imgHeight) / 2f
        } else {
            transY = transY.coerceIn(viewHeight - imgHeight, 0f)
        }

        matrix.postTranslate(transX - values[Matrix.MTRANS_X], transY - values[Matrix.MTRANS_Y])
    }

    private fun spacing(event: MotionEvent): Float {
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return Math.sqrt((x * x + y * y).toDouble()).toFloat()
    }

    private fun mid(event: MotionEvent): PointF {
        val x = event.getX(0) + event.getX(1)
        val y = event.getY(0) + event.getY(1)
        return PointF(x / 2f, y / 2f)
    }

    private fun stopScroll() {
        scrollAction?.let { removeCallbacks(it) }
        scroller.forceFinished(true)
    }

    companion object {
        private const val NONE = 0
        private const val DRAG = 1
        private const val ZOOM = 2
    }
}
