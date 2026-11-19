package com.exemplo.nuvem

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import kotlin.math.sin

/** Barrinhas que dançam enquanto a música toca e ficam baixas quando pausa. */
class OndaView(ctx: Context, private val barras: Int) : View(ctx) {
    var ativa = false
        set(v) { field = v; invalidate() }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        p.shader = LinearGradient(0f, 0f, w.toFloat(), 0f,
            Color.parseColor("#38E0C0"), Color.parseColor("#8B7BFF"), Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        val t = SystemClock.uptimeMillis() / 1000f
        val w = width.toFloat()
        val h = height.toFloat()
        val larg = w / (barras * 2f - 1f)
        for (i in 0 until barras) {
            val f = if (ativa) {
                val a = 0.5f + 0.5f * sin(t * 7f + i * 1.1f)
                val b = 0.5f + 0.5f * sin(t * 3.1f + i * 2.3f)
                0.25f + 0.75f * (a * 0.6f + b * 0.4f)
            } else 0.2f
            val bh = h * f
            val x = i * larg * 2f
            r.set(x, (h - bh) / 2f, x + larg, (h + bh) / 2f)
            canvas.drawRoundRect(r, larg / 2f, larg / 2f, p)
        }
        if (ativa) postInvalidateOnAnimation()
    }
}
