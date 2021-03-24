package com.hyc.dd_monitor.utils

import android.graphics.*
import com.squareup.picasso.Transformation

class RoundImageTransform : Transformation {
    override fun transform(source: Bitmap?): Bitmap {
        val output = Bitmap.createBitmap(source!!.width, source.height, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(output)
        val paint = Paint()
        paint.flags = Paint.ANTI_ALIAS_FLAG

        val rectF = RectF(Rect(0,0, output.width, output.height))
        canvas.drawRoundRect(rectF, (output.width/2).toFloat(),
            (output.height/2).toFloat(), paint)

        val paintImg = Paint()
        paintImg.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        canvas.drawBitmap(source, 0f, 0f, paintImg)
        source.recycle()

        return output
    }

    override fun key(): String {
        return "round"
    }
}