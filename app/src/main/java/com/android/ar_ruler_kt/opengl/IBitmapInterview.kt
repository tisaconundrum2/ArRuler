package com.android.ar_ruler_kt.opengl

import android.graphics.*

/**
 * @author：TianLong
 * @date：2022/7/9 0:33
 * @detail：
 */
interface IBitmapInterview {
    val paint: Paint
        get() {
            val tempPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            tempPaint.color = Color.WHITE
            tempPaint.textSize = 250f
            tempPaint.style = Paint.Style.FILL
            return tempPaint
        }

    val paintText: Paint
        get() {
            val tempPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            tempPaint.color = Color.BLACK
            tempPaint.textSize = 50f
            tempPaint.style = Paint.Style.FILL
            return tempPaint
        }
    val paintCircle: Paint
        get() {
            val tempPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            tempPaint.color = Color.BLUE
            tempPaint.textSize = 50f
            tempPaint.style = Paint.Style.FILL
            return tempPaint
        }
    val canvas: Canvas
        get() = Canvas()


    fun drawBitmap(width:Int,height:Int,content:String):Bitmap{
        val bitmap =Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        // Ensure the bitmap is mutable
//        val bitmap = data.copy(Bitmap.Config.ARGB_8888, true)

        val rectF = RectF(0f, 0f, (width).toFloat(), height.toFloat())

        // Create Canvas
        val canvas = Canvas(bitmap)

        canvas.drawRoundRect(rectF, 25f, 25f, paint)
        // Get the text width and height
        val rect = Rect()
        paintText.getTextBounds(content,0, content.length,rect)
        val textWidth = rect.width()
        val textHeight = rect.height()
        // Draw text: in view, down is positive Y, right is positive X, (0,0) is top-left of screen
        // When position is (0,0), the bottom-left corner of the text is drawn at (0,0).
        // To center the text, offset left by textWidth/2 (negative X) and down by textHeight/2 (positive Y).
        // Reason: imagine the text with its bottom-left at the screen's top-left (0,0)
        canvas.drawText( content,(width.toFloat()-textWidth)/2, (height.toFloat()+textHeight)/2,paintText)

//        canvas.drawCircle( width.toFloat()/2, height.toFloat()/2,60.0f,paintCircle)
        return bitmap
    }

    /**
     * Change bitmap
     * Example method: modify bitmap RGB channels, flip horizontally and vertically
     * @param bitmap
     * @return the modified bitmap
     */
    fun changeBitmap(bitmap: Bitmap):Bitmap{
        val newbitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val width = newbitmap.width
        val height = newbitmap.height

        val data = IntArray(width * height)
        val newData = IntArray(width * height)
        newbitmap.getPixels(data,0,width,0,0,width,height)
        //int color = (A & 0xff) << 24 | (B & 0xff) << 16 | (G & 0xff) << 8 | (R & 0xff);
        for (i in 0 until height){
            for (j in 0 until width){
                val temp  = data[i * width + j]
                // One pixel has 4 channels, each channel is 1 byte
                // int occupies 4 bytes.
                val a = temp shr 24 and 0xff
                val b = temp shr 16 and 0xff
                val g = temp shr 8 and 0xff
                val r = temp shr 0 and 0xff

                val color = ((a and 0xff) shl 24) or ((b and 0xff) shl 16) or((g and 0xff) shl 8 )or (r and 0xff)
                newData[(height -i-1) * width + (width-j-1)] = color
            }
        }

        newbitmap.setPixels(newData,0,width,0,0,width,height)
        return newbitmap
    }
}