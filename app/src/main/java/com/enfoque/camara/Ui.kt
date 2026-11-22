package com.enfoque.camara

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.media.AudioDeviceInfo
import android.media.MediaRecorder
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.materialswitch.MaterialSwitch
import kotlin.math.roundToInt

/** Estilo visual compartido: colores, tarjetas, filas, selectores y hojas inferiores. */
object Ui {
    val BG = Color.rgb(11, 11, 13)
    val SHEET = Color.rgb(20, 20, 23)
    val CARD = Color.rgb(23, 23, 27)
    val CARD2 = Color.rgb(34, 34, 40)
    val STROKE = Color.rgb(40, 40, 47)
    val TEXT = Color.rgb(242, 242, 245)
    val TEXT2 = Color.rgb(156, 156, 166)
    val ACCENT = Color.rgb(255, 196, 0)
    val ACCENT_SOFT = Color.argb(46, 255, 196, 0)

    fun argbAccent(alpha: Int) = Color.argb(alpha, 255, 196, 0)

    fun dp(ctx: Context, v: Float): Int = (v * ctx.resources.displayMetrics.density).roundToInt()

    fun rounded(color: Int, radiusPx: Float, strokePx: Int = 0, strokeColor: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(color)
            if (strokePx > 0) setStroke(strokePx, strokeColor)
        }

    fun ripple(content: Drawable?, radiusPx: Float): Drawable =
        RippleDrawable(ColorStateList.valueOf(Color.argb(40, 255, 255, 255)), content, rounded(Color.WHITE, radiusPx))

    fun text(ctx: Context, s: CharSequence, sizeSp: Float, color: Int, bold: Boolean = false): TextView =
        TextView(ctx).apply {
            text = s
            textSize = sizeSp
            setTextColor(color)
            if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

    fun icon(ctx: Context, res: Int, color: Int, sizeDp: Float = 22f): ImageView =
        ImageView(ctx).apply {
            setImageResource(res)
            imageTintList = ColorStateList.valueOf(color)
            layoutParams = LinearLayout.LayoutParams(dp(ctx, sizeDp), dp(ctx, sizeDp))
        }

    /** Ícono dentro de un cuadradito redondeado, como en los ajustes de los celulares buenos. */
    fun iconBox(ctx: Context, res: Int, tint: Int = ACCENT): FrameLayout {
        val box = FrameLayout(ctx)
        box.background = rounded(CARD2, dp(ctx, 11f).toFloat())
        val iv = ImageView(ctx).apply {
            setImageResource(res)
            imageTintList = ColorStateList.valueOf(tint)
        }
        val s = dp(ctx, 20f)
        box.addView(iv, FrameLayout.LayoutParams(s, s, Gravity.CENTER))
        box.layoutParams = LinearLayout.LayoutParams(dp(ctx, 38f), dp(ctx, 38f))
        return box
    }

    fun sectionTitle(ctx: Context, s: String, iconRes: Int? = null): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 6f), dp(ctx, 26f), dp(ctx, 6f), dp(ctx, 10f))
        }
        if (iconRes != null) {
            row.addView(icon(ctx, iconRes, ACCENT, 16f).apply {
                (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(ctx, 8f)
            })
        }
        row.addView(text(ctx, s.uppercase(), 12f, ACCENT, true).apply { letterSpacing = 0.12f })
        return row
    }

    fun card(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(CARD, dp(ctx, 22f).toFloat())
        setPadding(0, dp(ctx, 4f), 0, dp(ctx, 4f))
        clipToOutline = true
    }

    fun divider(ctx: Context): View = View(ctx).apply {
        setBackgroundColor(STROKE)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
            marginStart = dp(ctx, 68f)
            marginEnd = dp(ctx, 16f)
        }
    }

    fun pillButton(ctx: Context, label: String, primary: Boolean, iconRes: Int? = null, onClick: () -> Unit): LinearLayout {
        val fg = if (primary) Color.BLACK else TEXT
        val r = dp(ctx, 22f).toFloat()
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 18f), 0, dp(ctx, 18f), 0)
            background = ripple(rounded(if (primary) ACCENT else CARD2, r), r)
            minimumHeight = dp(ctx, 44f)
            if (iconRes != null) addView(icon(ctx, iconRes, fg, 18f).apply {
                (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(ctx, 8f)
            })
            addView(text(ctx, label, 14f, fg, true))
            isClickable = true
            setOnClickListener { onClick() }
        }
    }

    /** Fila base: ícono + título + subtítulo + espacio a la derecha. */
    class Row(ctx: Context, iconRes: Int, title: String, subtitle: String?) : LinearLayout(ctx) {
        val titleView: TextView
        val subtitleView: TextView
        val trailing: FrameLayout

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 16f), dp(ctx, 13f), dp(ctx, 16f), dp(ctx, 13f))
            addView(iconBox(ctx, iconRes))
            val texts = LinearLayout(ctx).apply { orientation = VERTICAL }
            titleView = text(ctx, title, 15.5f, TEXT, false)
            subtitleView = text(ctx, subtitle ?: "", 12.5f, TEXT2).apply {
                visibility = if (subtitle.isNullOrBlank()) GONE else VISIBLE
                setPadding(0, dp(ctx, 2f), 0, 0)
            }
            texts.addView(titleView)
            texts.addView(subtitleView)
            addView(texts, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(ctx, 14f); marginEnd = dp(ctx, 10f)
            })
            trailing = FrameLayout(ctx)
            addView(trailing, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    fun toggleRow(ctx: Context, iconRes: Int, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val row = Row(ctx, iconRes, title, subtitle)
        val sw = MaterialSwitch(ctx).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, c -> onChange(c) }
        }
        row.trailing.addView(sw)
        row.background = ripple(null, 0f)
        row.setOnClickListener { sw.toggle() }
        return row
    }

    fun segmentedRow(ctx: Context, iconRes: Int, title: String, subtitle: String?, options: List<String>, selected: Int, onSelect: (Int) -> Unit): View {
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        box.addView(Row(ctx, iconRes, title, subtitle))
        val seg = SegmentedView(ctx, options, selected, onSelect)
        box.addView(seg, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(ctx, 68f); marginEnd = dp(ctx, 16f); bottomMargin = dp(ctx, 14f)
        })
        return box
    }

    /** Fila que abre algo: muestra el valor actual y una flechita. Devuelve la fila y el texto del valor. */
    fun actionRow(ctx: Context, iconRes: Int, title: String, subtitle: String?, value: String?, onClick: () -> Unit): Pair<View, TextView> {
        val row = Row(ctx, iconRes, title, subtitle)
        val right = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val valueView = text(ctx, value ?: "", 13f, TEXT2).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = dp(ctx, 130f)
            visibility = if (value.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        right.addView(valueView)
        right.addView(icon(ctx, R.drawable.ic_chevron_right, TEXT2, 18f).apply {
            (layoutParams as LinearLayout.LayoutParams).marginStart = dp(ctx, 4f)
        })
        row.trailing.addView(right)
        row.background = ripple(null, 0f)
        row.setOnClickListener { onClick() }
        return row to valueView
    }

    /** Selector tipo "pastillas": la opción elegida queda amarilla. */
    class SegmentedView(ctx: Context, options: List<String>, selected: Int, private val onSelect: (Int) -> Unit) : LinearLayout(ctx) {
        private val items = mutableListOf<TextView>()
        var selectedIndex = selected
            private set

        init {
            orientation = HORIZONTAL
            val p = dp(ctx, 3f)
            setPadding(p, p, p, p)
            background = rounded(CARD2, dp(ctx, 13f).toFloat())
            options.forEachIndexed { i, o ->
                val tv = text(ctx, o, 13f, TEXT2).apply {
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(dp(ctx, 4f), 0, dp(ctx, 4f), 0)
                    setOnClickListener { select(i, true) }
                }
                addView(tv, LayoutParams(0, dp(ctx, 36f), 1f))
                items += tv
            }
            render()
        }

        fun select(i: Int, notify: Boolean) {
            if (i == selectedIndex && notify) return
            selectedIndex = i
            render()
            if (notify) onSelect(i)
        }

        private fun render() {
            items.forEachIndexed { i, tv ->
                val sel = i == selectedIndex
                tv.background = if (sel) rounded(ACCENT, dp(context, 10f).toFloat()) else null
                tv.setTextColor(if (sel) Color.BLACK else TEXT2)
                tv.typeface = if (sel) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
            }
        }
    }

    /** Opción elegible para las hojas inferiores (tarjeta con borde amarillo cuando está elegida). */
    class OptionRow(ctx: Context, iconRes: Int, title: String, subtitle: String?) : LinearLayout(ctx) {
        private val check: ImageView
        private val iconBoxView: FrameLayout

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 14f), dp(ctx, 12f), dp(ctx, 14f), dp(ctx, 12f))
            iconBoxView = iconBox(ctx, iconRes)
            addView(iconBoxView)
            val texts = LinearLayout(ctx).apply { orientation = VERTICAL }
            texts.addView(text(ctx, title, 15f, TEXT))
            if (!subtitle.isNullOrBlank()) texts.addView(text(ctx, subtitle, 12.5f, TEXT2).apply { setPadding(0, dp(ctx, 2f), 0, 0) })
            addView(texts, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(ctx, 14f); marginEnd = dp(ctx, 8f) })
            check = icon(ctx, R.drawable.ic_check, ACCENT, 22f)
            addView(check)
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(ctx, 8f) }
            setChecked(false)
        }

        fun setChecked(c: Boolean) {
            val r = dp(context, 16f).toFloat()
            background = ripple(
                if (c) rounded(ACCENT_SOFT, r, dp(context, 1.5f), ACCENT) else rounded(CARD, r, 1, STROKE), r
            )
            check.visibility = if (c) VISIBLE else INVISIBLE
        }
    }

    /** Hoja que sube desde abajo con el estilo de la app. */
    fun sheet(activity: Activity, title: String, subtitle: String? = null, build: (LinearLayout, BottomSheetDialog) -> Unit): BottomSheetDialog {
        val d = BottomSheetDialog(activity)
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 18f), dp(activity, 10f), dp(activity, 18f), dp(activity, 28f))
        }
        val handle = View(activity).apply { background = rounded(Color.rgb(70, 70, 78), dp(activity, 2f).toFloat()) }
        content.addView(handle, LinearLayout.LayoutParams(dp(activity, 40f), dp(activity, 4f)).apply {
            gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(activity, 16f)
        })
        content.addView(text(activity, title, 21f, TEXT, true))
        if (subtitle != null) content.addView(text(activity, subtitle, 13.5f, TEXT2).apply { setPadding(0, dp(activity, 4f), 0, 0) })
        content.addView(View(activity), LinearLayout.LayoutParams(1, dp(activity, 14f)))
        build(content, d)
        val scroll = NestedScrollView(activity).apply {
            val r = dp(activity, 28f).toFloat()
            background = GradientDrawable().apply {
                setColor(SHEET)
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            addView(content)
        }
        d.setContentView(scroll)
        d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        d.setOnShowListener {
            d.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)
            d.behavior.skipCollapsed = true
            d.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        d.show()
        return d
    }

    fun micIcon(dev: AudioDeviceInfo?): Int = when (dev?.type) {
        null -> R.drawable.ic_auto
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> R.drawable.ic_mic
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> R.drawable.ic_headset
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY -> R.drawable.ic_usb
        else -> R.drawable.ic_bluetooth
    }

    fun sourceIcon(src: Int): Int = when (src) {
        MediaRecorder.AudioSource.CAMCORDER -> R.drawable.ic_video
        MediaRecorder.AudioSource.VOICE_RECOGNITION -> R.drawable.ic_voice
        MediaRecorder.AudioSource.VOICE_COMMUNICATION -> R.drawable.ic_noise
        MediaRecorder.AudioSource.UNPROCESSED -> R.drawable.ic_wave
        else -> R.drawable.ic_mic
    }

    /** Hoja para elegir micrófono y tipo de sonido. */
    fun showMicSheet(activity: Activity, prefs: Prefs, onChanged: () -> Unit) {
        sheet(activity, "Micrófono", "Elegí con qué grabás y cómo querés que suene tu voz.") { c, _ ->
            c.addView(sectionTitle(activity, "De dónde graba", R.drawable.ic_mic))
            val mics = AudioSetup.mics(activity)
            val currentMic = mics.indexOfFirst { it.key == prefs.micKey }.let { if (it < 0) 0 else it }
            val micRows = mutableListOf<OptionRow>()
            mics.forEachIndexed { i, m ->
                val sub = when {
                    m.device == null -> "El celu decide según lo que esté conectado"
                    AudioSetup.isBluetooth(m.device) -> "Calidad de llamada: mejor USB-C o cable"
                    else -> null
                }
                val row = OptionRow(activity, micIcon(m.device), m.label, sub)
                row.setChecked(i == currentMic)
                row.setOnClickListener {
                    prefs.micKey = m.key
                    micRows.forEachIndexed { j, r -> r.setChecked(j == i) }
                    onChanged()
                }
                micRows += row
                c.addView(row)
            }
            c.addView(text(activity, "¿No aparece el tuyo? Conectalo y volvé a abrir esto. Los receptores inalámbricos de solapa aparecen como USB-C.", 12.5f, TEXT2).apply {
                setPadding(dp(activity, 4f), dp(activity, 2f), dp(activity, 4f), 0)
            })

            c.addView(sectionTitle(activity, "Tipo de sonido", R.drawable.ic_wave))
            val unproc = AudioSetup.unprocessedSupported(activity)
            val sources = AudioSetup.SOURCES.filter { it.source != MediaRecorder.AudioSource.UNPROCESSED || unproc }
            val srcRows = mutableListOf<OptionRow>()
            sources.forEachIndexed { i, s ->
                val row = OptionRow(activity, sourceIcon(s.source), s.label, s.help)
                row.setChecked(s.source == prefs.audioSource)
                row.setOnClickListener {
                    prefs.audioSource = s.source
                    srcRows.forEachIndexed { j, r -> r.setChecked(j == i) }
                    onChanged()
                }
                srcRows += row
                c.addView(row)
            }
        }
    }

    val PROMPTER_SIZES = listOf(20, 24, 30, 36)

    /** Hoja para escribir o pegar el guion del teleprompter. */
    fun showPrompterSheet(activity: Activity, prefs: Prefs, onSaved: () -> Unit) {
        sheet(activity, "Teleprompter", "Pegá tu guion. Arranca solo cuando grabás; tocalo para pausar y arrastralo para moverlo.") { c, d ->
            val input = EditText(activity).apply {
                setText(prefs.prompterText)
                hint = "Escribí o pegá acá el guion de tu video o Short…"
                setHintTextColor(TEXT2)
                setTextColor(TEXT)
                textSize = 15f
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                minLines = 7
                maxLines = 14
                gravity = Gravity.TOP or Gravity.START
                val p = dp(activity, 14f)
                setPadding(p, p, p, p)
                background = rounded(CARD, dp(activity, 16f).toFloat(), 1, STROKE)
            }
            c.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            c.addView(text(activity, "Velocidad", 13.5f, TEXT2).apply { setPadding(dp(activity, 4f), dp(activity, 18f), 0, dp(activity, 8f)) })
            c.addView(SegmentedView(activity, listOf("Lenta", "Normal", "Rápida", "Muy rápida"), prefs.prompterSpeed.coerceIn(0, 3)) { prefs.prompterSpeed = it })
            c.addView(text(activity, "Tamaño de letra", 13.5f, TEXT2).apply { setPadding(dp(activity, 4f), dp(activity, 16f), 0, dp(activity, 8f)) })
            val sizeIdx = PROMPTER_SIZES.indexOf(prefs.prompterSize).let { if (it < 0) 1 else it }
            c.addView(SegmentedView(activity, listOf("Chica", "Media", "Grande", "Enorme"), sizeIdx) { prefs.prompterSize = PROMPTER_SIZES[it] })

            val buttons = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
            buttons.addView(pillButton(activity, "Borrar", false, R.drawable.ic_close) { input.setText("") })
            buttons.addView(View(activity), LinearLayout.LayoutParams(dp(activity, 10f), 1))
            buttons.addView(pillButton(activity, "Guardar", true, R.drawable.ic_check) {
                prefs.prompterText = input.text.toString()
                prefs.prompterOn = prefs.prompterText.isNotBlank()
                onSaved()
                d.dismiss()
            })
            c.addView(buttons, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(activity, 20f) })
        }
    }
}
