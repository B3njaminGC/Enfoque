package com.enfoque.camara

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max

private fun View.px(v: Float): Float = v * resources.displayMetrics.density

/**
 * Gira el lienzo para dibujar "derecho" según cómo está agarrado el celu.
 * Devuelve el ancho y alto lógicos.
 */
private fun rotateCanvas(c: Canvas, deg: Float, w: Float, h: Float): Pair<Float, Float> {
    val swap = abs(deg % 180f) == 90f
    val lw = if (swap) h else w
    val lh = if (swap) w else h
    c.translate(w / 2f, h / 2f)
    c.rotate(deg)
    c.translate(-lw / 2f, -lh / 2f)
    return lw to lh
}

/** Zonas seguras de Shorts, guías 16:9 de YouTube y barra de duración. */
class GuideOverlay(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    enum class Kind { NONE, SHORTS, YOUTUBE }

    var kind = Kind.NONE
        set(v) { field = v; invalidate() }
    var rotationDeg = 0f
        set(v) { field = v; invalidate() }
    var showZones = true
        set(v) { field = v; invalidate() }
    /** 0..1 o negativo para ocultar. */
    var progress = -1f
        set(v) { field = v; invalidate() }

    private val shade = Paint().apply { color = Color.argb(80, 0, 0, 0) }
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(180, 255, 255, 255)
        strokeWidth = px(1.5f)
        pathEffect = DashPathEffect(floatArrayOf(px(7f), px(5f)), 0f)
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 255, 255, 255); textSize = px(11f)
        setShadowLayer(4f, 0f, 0f, Color.BLACK)
    }
    private val bar = Paint().apply { color = Color.rgb(255, 45, 45) }
    private val barBg = Paint().apply { color = Color.argb(90, 255, 255, 255) }
    private val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = px(17f); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(10f, 0f, 0f, Color.BLACK)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        when (kind) {
            Kind.SHORTS -> {
                if (showZones) {
                    val zTop = h * 0.08f
                    val zBottom = h * 0.78f
                    val rightX = w * 0.84f
                    canvas.drawRect(0f, 0f, w, zTop, shade)
                    canvas.drawRect(0f, zBottom, w, h, shade)
                    canvas.drawRect(rightX, h * 0.42f, w, zBottom, shade)
                    canvas.drawRect(px(2f), zTop, rightX, zBottom, dash)
                    canvas.drawText("Zona segura de Shorts", px(8f), zTop + px(16f), label)
                    canvas.drawText("Acá van el título y la descripción", px(8f), zBottom + px(18f), label)
                    canvas.save()
                    canvas.rotate(90f, rightX + px(6f), h * 0.46f)
                    canvas.drawText("Botones de YouTube", rightX + px(6f), h * 0.46f, label)
                    canvas.restore()
                }
                if (progress >= 0f) {
                    canvas.drawRect(0f, 0f, w, px(4f), barBg)
                    canvas.drawRect(0f, 0f, w * progress.coerceAtMost(1f), px(4f), bar)
                }
            }
            Kind.YOUTUBE -> {
                if (showZones) {
                    canvas.save()
                    val (lw, lh) = rotateCanvas(canvas, rotationDeg, w, h)
                    val mx = lw * 0.05f
                    val my = lh * 0.05f
                    canvas.drawRect(mx, my, lw - mx, lh - my, dash)
                    val cx = lw / 2f
                    val cy = lh / 2f
                    val s = px(10f)
                    canvas.drawLine(cx - s, cy, cx + s, cy, dash)
                    canvas.drawLine(cx, cy - s, cx, cy + s, dash)
                    canvas.drawText("Zona segura 16:9", mx + px(6f), my + px(16f), label)
                    canvas.restore()
                }
                if (rotationDeg == 0f) {
                    canvas.drawText("Girá el celu de costado", w / 2f, h / 2f - px(48f), hint)
                }
            }
            Kind.NONE -> {}
        }
    }
}

/** Teleprompter: texto que sube solo cerca de la cámara. Tocalo para pausar; arrastralo para moverlo. */
class PrompterView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    var text: String = ""
        set(v) { field = v; textLayout = null; offset = 0f; invalidate() }
    var speedLevel = 1
    var rotationDeg = 0f
        set(v) { field = v; textLayout = null; invalidate() }
    var textSizeSp = 24f
        set(v) { field = v; tp.textSize = px(v); textLayout = null; invalidate() }

    var running = false
        private set
    private var offset = 0f
    private var lastFrame = 0L
    private var textLayout: StaticLayout? = null
    private var layoutWidth = 0
    private val box = RectF()
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = px(24f); typeface = Typeface.DEFAULT_BOLD
    }
    private val bg = Paint().apply { color = Color.argb(160, 0, 0, 0) }
    private val marker = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 196, 0); strokeWidth = px(3f) }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 255, 255, 255); textSize = px(11f) }

    fun start() { running = true; lastFrame = 0L; invalidate() }
    fun pause() { running = false; invalidate() }
    fun reset() { running = false; offset = 0f; invalidate() }
    fun toggle() { if (running) pause() else start() }

    private fun speedPx(): Float = px(listOf(16f, 28f, 42f, 60f)[speedLevel.coerceIn(0, 3)])

    private fun logicalSize(): Pair<Float, Float> {
        val swap = abs(rotationDeg % 180f) == 90f
        return if (swap) height.toFloat() to width.toFloat() else width.toFloat() to height.toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        if (text.isBlank()) return
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.save()
        val (lw, lh) = rotateCanvas(canvas, rotationDeg, w, h)
        val boxH = lh * 0.34f
        val pad = px(16f)
        box.set(0f, 0f, lw, boxH)
        canvas.drawRect(box, bg)
        val tw = (lw - pad * 2).toInt().coerceAtLeast(10)
        if (textLayout == null || layoutWidth != tw) {
            textLayout = StaticLayout.Builder.obtain(text, 0, text.length, tp, tw)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.15f)
                .build()
            layoutWidth = tw
        }
        val l = textLayout ?: return
        if (running) {
            val now = SystemClock.uptimeMillis()
            if (lastFrame > 0) offset += (now - lastFrame) / 1000f * speedPx()
            lastFrame = now
            if (offset > l.height) running = false else postInvalidateOnAnimation()
        }
        val readLine = boxH * 0.3f
        canvas.save()
        canvas.clipRect(box)
        canvas.translate(pad, readLine - offset)
        l.draw(canvas)
        canvas.restore()
        canvas.drawLine(0f, readLine, pad * 0.7f, readLine, marker)
        canvas.drawLine(lw - pad * 0.7f, readLine, lw, readLine, marker)
        if (!running) canvas.drawText(if (offset == 0f) "Guion listo · arranca al grabar" else "Pausado · tocá para seguir", pad, boxH - px(8f), small)
        canvas.restore()
    }

    // --- Toques: solo dentro del recuadro del guion ---
    private var touching = false
    private var moved = false
    private var lastY = 0f
    private var downY = 0f

    private fun toLogical(x: Float, y: Float): FloatArray {
        val (lw, lh) = logicalSize()
        val m = Matrix()
        m.setTranslate(-lw / 2f, -lh / 2f)
        m.postRotate(rotationDeg)
        m.postTranslate(width / 2f, height / 2f)
        val inv = Matrix()
        m.invert(inv)
        val pts = floatArrayOf(x, y)
        inv.mapPoints(pts)
        return pts
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (text.isBlank() || visibility != VISIBLE) return false
        val p = toLogical(event.x, event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!box.contains(p[0], p[1])) return false
                touching = true; moved = false; lastY = p[1]; downY = p[1]
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!touching) return false
                if (abs(p[1] - downY) > px(8f)) moved = true
                if (moved) { offset = max(0f, offset - (p[1] - lastY)); lastY = p[1]; invalidate() }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!touching) return false
                touching = false
                if (!moved) toggle() else performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> { touching = false; return true }
        }
        return touching
    }

    override fun performClick(): Boolean = super.performClick()
}

/** Vúmetro: verde = bien, amarillo = fuerte, rojo = se satura. */
class AudioMeterView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private var level = 0f
    private var peak = 0f
    private var peakTime = 0L
    private var db = -90f
    private val bg = Paint().apply { color = Color.argb(150, 0, 0, 0) }
    private val fill = Paint()
    private val peakPaint = Paint().apply { color = Color.WHITE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = px(10f); typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(3f, 0f, 0f, Color.BLACK)
    }

    fun setAmplitude(amp: Int) {
        db = if (amp <= 0) -90f else 20f * log10(amp / 32767f)
        val l = ((db + 60f) / 60f).coerceIn(0f, 1f)
        level = max(l, level * 0.8f)
        val now = SystemClock.uptimeMillis()
        if (l >= peak || now - peakTime > 1500) { peak = l; peakTime = now }
        invalidate()
    }

    fun reset() { level = 0f; peak = 0f; db = -90f; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2f
        canvas.drawRoundRect(0f, 0f, w, h, r, r, bg)
        fill.color = when {
            level > 0.95f -> Color.rgb(255, 50, 50)
            level > 0.8f -> Color.rgb(255, 196, 0)
            else -> Color.rgb(60, 220, 110)
        }
        canvas.drawRoundRect(0f, 0f, w * level, h, r, r, fill)
        val peakX = w * peak
        canvas.drawRect(peakX - 2f, 0f, peakX + 2f, h, peakPaint)
        val label = when {
            db < -59f -> "sin señal"
            level > 0.95f -> "¡satura! alejá el mic"
            level < 0.45f -> "bajo · acercá el mic"
            else -> "${db.toInt()} dB"
        }
        canvas.drawText(label, px(8f), h / 2f + text.textSize / 3f, text)
    }
}
