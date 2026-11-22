package com.enfoque.camara

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min

private fun View.dp(v: Float): Float = v * resources.displayMetrics.density

/** Cuadrícula de tercios. */
class GridOverlay(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    var showGrid = true
        set(v) { field = v; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(110, 255, 255, 255)
        strokeWidth = dp(1f)
    }

    override fun onDraw(canvas: Canvas) {
        if (!showGrid) return
        val w = width.toFloat()
        val h = height.toFloat()
        for (i in 1..2) {
            canvas.drawLine(w * i / 3f, 0f, w * i / 3f, h, paint)
            canvas.drawLine(0f, h * i / 3f, w, h * i / 3f, paint)
        }
    }
}

/** Nivel de horizonte: la línea se pone amarilla cuando el celu está derecho. */
class LevelOverlay(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    var showLevel = true
        set(v) { field = v; invalidate() }
    private var angle = Float.NaN
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = dp(2f); strokeCap = Paint.Cap.ROUND }
    private val ref = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = dp(2f); color = Color.argb(150, 255, 255, 255); strokeCap = Paint.Cap.ROUND
    }

    /** Ángulo en grados respecto al horizonte, o NaN para ocultarlo (celu apuntando al piso/cielo). */
    fun update(newAngle: Float) {
        angle = if (newAngle.isNaN() || angle.isNaN()) newAngle else angle * 0.8f + newAngle * 0.2f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (!showLevel || angle.isNaN()) return
        val cx = width / 2f
        val cy = height / 2f
        val len = width * 0.16f
        val level = abs(angle) < 1f
        line.color = if (level) Color.rgb(255, 196, 0) else Color.WHITE
        canvas.drawLine(cx - len * 1.35f, cy, cx - len * 1.15f, cy, ref)
        canvas.drawLine(cx + len * 1.15f, cy, cx + len * 1.35f, cy, ref)
        canvas.save()
        canvas.rotate(angle, cx, cy)
        canvas.drawLine(cx - len, cy, cx - len * 0.25f, cy, line)
        canvas.drawLine(cx + len * 0.25f, cy, cx + len, cy, line)
        canvas.restore()
    }
}

/** Anillo de enfoque que aparece donde tocás. */
class FocusRingView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private var fx = -1f
    private var fy = -1f
    private var locked = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(2f) }

    fun show(x: Float, y: Float, lock: Boolean) {
        fx = x; fy = y; locked = lock
        animate().cancel()
        alpha = 1f
        scaleRing = 1.25f
        invalidate()
        if (!lock) animate().alpha(0f).setStartDelay(1400).setDuration(400).start()
    }

    fun hide() { animate().cancel(); alpha = 0f }

    private var scaleRing = 1f

    override fun onDraw(canvas: Canvas) {
        if (fx < 0) return
        if (scaleRing > 1f) { scaleRing -= 0.05f; postInvalidateOnAnimation() }
        paint.color = if (locked) Color.rgb(255, 196, 0) else Color.WHITE
        val r = dp(36f) * scaleRing
        canvas.drawCircle(fx, fy, r, paint)
        canvas.drawCircle(fx, fy, dp(3f), paint)
    }
}

/** Histograma de luminancia en vivo. */
class HistogramView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private var data: IntArray? = null
    private val bg = Paint().apply { color = Color.argb(120, 0, 0, 0) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 255, 255, 255) }
    private val clip = Paint().apply { color = Color.rgb(255, 60, 60) }
    private val path = Path()

    fun setData(bins: IntArray) { data = bins; invalidate() }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), dp(6f), dp(6f), bg)
        val d = data ?: return
        var max = 1
        for (i in 1 until d.size - 1) if (d[i] > max) max = d[i]
        val w = width.toFloat()
        val h = height.toFloat()
        val step = w / (d.size - 1)
        path.reset()
        path.moveTo(0f, h)
        for (i in d.indices) {
            val v = min(1f, d[i].toFloat() / max)
            path.lineTo(i * step, h - v * (h - dp(4f)))
        }
        path.lineTo(w, h)
        path.close()
        canvas.drawPath(path, fill)
        val total = d.sum().coerceAtLeast(1)
        // Aviso de sombras o luces quemadas (>2% de píxeles en los extremos)
        if (d[0] * 50 > total) canvas.drawRect(0f, 0f, dp(3f), h, clip)
        if (d[d.size - 1] * 50 > total) canvas.drawRect(w - dp(3f), 0f, w, h, clip)
    }
}

/** Botón disparador: blanco para foto, rojo para video, cuadrado mientras graba. */
class ShutterButton(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    var videoMode = false
        set(v) { field = v; invalidate() }
    var recording = false
        set(v) { field = v; invalidate() }
    var busy = false
        set(v) { field = v; invalidate() }

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.WHITE; strokeWidth = dp(4f) }
    private val inner = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - dp(3f)
        canvas.drawCircle(cx, cy, r, ring)
        inner.color = when {
            busy -> Color.GRAY
            videoMode -> Color.rgb(235, 40, 40)
            else -> Color.WHITE
        }
        if (recording) {
            val s = r * 0.42f
            canvas.drawRoundRect(cx - s, cy - s, cx + s, cy + s, dp(6f), dp(6f), inner)
        } else {
            canvas.drawCircle(cx, cy, r - dp(7f), inner)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).start()
            MotionEvent.ACTION_UP -> {
                animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}
