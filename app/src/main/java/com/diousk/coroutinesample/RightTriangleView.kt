package com.diousk.coroutinesample

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.view.doOnPreDraw

class RightTriangleView @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val paint: Paint
    private val path = Path()
    private var rotate: Float
    private var paintColorStart: Int
    private var paintColorEnd: Int

    init {
        val typeArray = context?.theme?.obtainStyledAttributes(attrs, R.styleable.RightTriangleView, 0, 0)
        val paintColor = typeArray?.getColor(R.styleable.RightTriangleView_color, Color.TRANSPARENT)
        paintColorStart = typeArray?.getColor(R.styleable.RightTriangleView_colorStart, Color.TRANSPARENT)
            ?: Color.TRANSPARENT
        paintColorEnd = typeArray?.getColor(R.styleable.RightTriangleView_colorEnd, Color.TRANSPARENT)
            ?: Color.TRANSPARENT
        rotate = typeArray?.getFloat(R.styleable.RightTriangleView_rotate, 0f) ?: 0f
        typeArray?.recycle()

        paint = Paint().apply {
            paintColor?.let { color = it }
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        rotation = rotate

        doOnPreDraw {
            paint.shader =
                LinearGradient(0f, 0f, 0f, measuredHeight.toFloat(),
                    paintColorStart, paintColorEnd, Shader.TileMode.MIRROR)
        }
    }

    override fun onDraw(canvas: Canvas?) {
        path.moveTo(measuredWidth.toFloat(), 0f)
        path.lineTo(0f, measuredHeight.toFloat())
        path.lineTo(measuredWidth.toFloat(), measuredHeight.toFloat())
        path.close()
        canvas?.drawPath(path, paint)
    }
}