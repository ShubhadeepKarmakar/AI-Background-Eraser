package com.example.ai_background_eraser

import android.content.Context
import android.graphics.*


import android.util.AttributeSet
import android.util.Log
import android.util.Pair
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.math.abs

class DrawView(c: Context, attrs: AttributeSet) : View(c, attrs) {
    private var livePath = Path()
    private var pathPaint = Paint()

    private var imageBitmap: Bitmap? = null
    private var savedImageBitmap: Bitmap? = null
    private var cuts = Stack<Pair<Pair<Path, Paint>, Bitmap?>>()
    private var undoneCuts = Stack<Pair<Pair<Path, Paint>, Bitmap?>>()

    private var pathX: Float? = null
    private var pathY: Float? = null

    private var TOUCH_TOLERANCE = 4f
    private var COLOR_TOLERANCE = 20f

    private var undoButton: Button? = null
    private var redoButton: Button? = null

    private var loadingModal: View? = null

    private var currentAction: DrawViewAction? = null

    enum class DrawViewAction {
       AUTO_SELFIE, AUTO_CLEAR, MANUAL_CLEAR, ZOOM
    }

    init {

        pathPaint.isAntiAlias=true
        pathPaint.isDither = true
        pathPaint.alpha = 0
        pathPaint.strokeWidth=20f
        pathPaint.color = Color.TRANSPARENT
        pathPaint.style = Paint.Style.STROKE
        pathPaint.strokeJoin = Paint.Join.ROUND
        pathPaint.strokeCap = Paint.Cap.ROUND
        pathPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    fun setButtons(undoButton: Button?, redoButton: Button?) {
        this.undoButton = undoButton
        this.redoButton = redoButton
    }

    override fun onSizeChanged(newWidth: Int, newHeight: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(newWidth, newHeight, oldWidth, oldHeight)
        resizeBitmap(newWidth, newHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        if (imageBitmap != null) {
            canvas.drawBitmap(imageBitmap!!, 0f, 0f, null)
            for (action in cuts) {
                if (action.first != null) {
                    canvas.drawPath(action.first.first!!, action.first.second!!)
                }
            }
            if (currentAction == DrawViewAction.MANUAL_CLEAR) {
                canvas.drawPath(livePath, pathPaint)
            }
        }
        canvas.restore()
    }

    private fun touchStart(x: Float, y: Float) {
        pathX = x
        pathY = y
        undoneCuts.clear()
        redoButton!!.isEnabled = false
        if (currentAction == DrawViewAction.AUTO_CLEAR) {
            AutoColor(this).execute(x.toInt(), y.toInt())
        } else {
            livePath.moveTo(x, y)
        }
        invalidate()
    }

    private fun touchMove(x: Float, y: Float) {
        if (currentAction == DrawViewAction.MANUAL_CLEAR) {
            val dx = abs(x - pathX!!)
            val dy = abs(y - pathY!!)
            if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
                livePath.quadTo(pathX!!, pathY!!, (x + pathX!!) / 2, (y + pathY!!) / 2)
                pathX = x
                pathY = y
            }
        }
    }

    private fun touchUp() {
        if (currentAction == DrawViewAction.MANUAL_CLEAR) {
            livePath.lineTo(pathX!!, pathY!!)
            cuts.push(Pair(Pair(livePath, pathPaint), null))
            livePath = Path()
            undoButton!!.isEnabled = true
        }
    }

    fun undo() {
        if (cuts.size > 0) {
            val cut: Pair<Pair<Path, Paint>, Bitmap?> = cuts.pop()
            if (cut.second != null) {
                undoneCuts.push(Pair(null, imageBitmap))
                imageBitmap = cut.second
            } else {
                undoneCuts.push(cut)
            }
            if (cuts.isEmpty()) {
                undoButton!!.isEnabled = false
            }
            redoButton!!.isEnabled = true
            invalidate()
        }
        //toast the user
    }

    fun redo() {
        if (undoneCuts.size > 0) {
            val cut = undoneCuts.pop()
            if (cut.second != null) {
                cuts.push(Pair(null, imageBitmap))
                imageBitmap = cut.second
            } else {
                cuts.push(cut)
            }
            if (undoneCuts.isEmpty()) {
                redoButton!!.isEnabled = false
            }
            undoButton!!.isEnabled = true
            invalidate()
        }
        //toast the user
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (imageBitmap != null && currentAction != DrawViewAction.ZOOM) {
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStart(ev.x, ev.y)
                    Log.d(VIEW_LOG_TAG, "touchStart")
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    touchMove(ev.x, ev.y)
                    Log.d(VIEW_LOG_TAG, "touchMove")
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    touchUp()
                    Log.d(VIEW_LOG_TAG, "touchUp")
                    invalidate()
                    return true
                }
            }
        }
        return super.onTouchEvent(ev)
    }

    private fun resizeBitmap(width: Int, height: Int) {
        if (width > 0 && height > 0 && imageBitmap != null) {
            imageBitmap = BitmapUtility.getResizedBitmap(imageBitmap, width, height)
            imageBitmap!!.setHasAlpha(true)
            invalidate()
        }
    }

    fun setBitmap(bitmap: Bitmap?) {
        imageBitmap = bitmap
        savedImageBitmap=bitmap
        resizeBitmap(width, height)
    }

    fun getBitmap(): Bitmap? {
        return savedImageBitmap
    }

    fun setAction(newAction: DrawViewAction?) {
        currentAction = newAction
    }

    fun setStrokeWidth(strokeWidth: Int) {
        pathPaint = Paint(pathPaint)
        pathPaint.strokeWidth = strokeWidth.toFloat()
    }

    fun setLoadingModal(loadingModal: View?) {
        this.loadingModal = loadingModal
    }


    inner class AutoColor(var drawView: DrawView) {
        init {
            drawView.loadingModal!!.setVisibility(VISIBLE)
            drawView.cuts.push(Pair(null, drawView.imageBitmap))
        }
        fun execute(vararg points: Int?) {

            GlobalScope.launch(Dispatchers.Default) {
                val result = doInBackground(points[0]!!,points[1]!!)
                withContext(Dispatchers.Main) {
                    onPostExecute(result)
                }
            }
        }

        private fun doInBackground(vararg points: Int): Bitmap{
            val oldBitmap: Bitmap = drawView.imageBitmap!!
            val colorToReplace = oldBitmap.getPixel(points[0]!!, points[1]!!)
            val width = oldBitmap.width
            val height = oldBitmap.height
            val pixels = IntArray(width * height)
            oldBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val rA = Color.alpha(colorToReplace)
            val rR = Color.red(colorToReplace)
            val rG = Color.green(colorToReplace)
            val rB = Color.blue(colorToReplace)
            var pixel: Int

            // iteration through pixels
            for (y in 0 until height) {
                for (x in 0 until width) {
                    // get current index in 2D-matrix
                    val index = y * width + x
                    pixel = pixels[index]
                    val rrA = Color.alpha(pixel)
                    val rrR = Color.red(pixel)
                    val rrG = Color.green(pixel)
                    val rrB = Color.blue(pixel)
                    if (rA - COLOR_TOLERANCE < rrA && rrA < rA + COLOR_TOLERANCE && rR - COLOR_TOLERANCE < rrR && rrR < rR +COLOR_TOLERANCE && rG - COLOR_TOLERANCE < rrG && rrG < rG + COLOR_TOLERANCE && rB - COLOR_TOLERANCE < rrB && rrB < rB + COLOR_TOLERANCE) {
                        pixels[index] = Color.TRANSPARENT
                    }
                }
            }
            val newBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            newBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            return newBitmap
        }
        private fun onPostExecute(result: Bitmap) {
            drawView.imageBitmap = result
            drawView.undoButton?.isEnabled = true
            drawView.loadingModal?.visibility = INVISIBLE
            drawView.invalidate()
        }
    }
    fun beforeAutoSelfie(){
        loadingModal!!.visibility = VISIBLE

        cuts.push(Pair(null,imageBitmap))

    }
    fun afterAutoSelfie(){
        undoButton?.isEnabled = true
        loadingModal?.visibility = INVISIBLE
        invalidate()
    }

}
