package com.enfoque.camara

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var column: LinearLayout
    private lateinit var summary: TextView
    private var micValue: TextView? = null
    private var prompterValue: TextView? = null
    private var meter: AudioMeterView? = null
    private var testButton: View? = null
    private var testing = false
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val scroll = NestedScrollView(this).apply { setBackgroundColor(Ui.BG); isFillViewport = true }
        column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14f), dp(8f), dp(14f), dp(40f))
        }
        scroll.addView(column)
        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        buildHeader()
        buildVideo()
        buildShorts()
        buildAudio()
        buildPrompter()
        buildPhoto()
        buildScreen()
        buildAbout()
        refreshSummary()
    }

    override fun onPause() {
        super.onPause()
        main.removeCallbacksAndMessages(null)
    }

    private fun dp(v: Float) = Ui.dp(this, v)

    private fun addCard(vararg rows: View) {
        val card = Ui.card(this)
        rows.forEachIndexed { i, r ->
            if (i > 0) card.addView(Ui.divider(this))
            card.addView(r)
        }
        column.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    // ---------------------------------------------------------------- Encabezado

    private fun buildHeader() {
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val back = android.widget.FrameLayout(this).apply {
            background = Ui.ripple(Ui.rounded(Ui.CARD, dp(21f).toFloat()), dp(21f).toFloat())
            addView(Ui.icon(this@SettingsActivity, R.drawable.ic_back, Ui.TEXT, 22f).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(dp(22f), dp(22f), Gravity.CENTER)
            })
            setOnClickListener { finish() }
        }
        top.addView(back, LinearLayout.LayoutParams(dp(42f), dp(42f)))
        column.addView(top, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8f) })

        column.addView(Ui.text(this, "Ajustes", 32f, Ui.TEXT, true).apply { setPadding(dp(6f), dp(18f), 0, 0) })
        column.addView(Ui.text(this, "Enfoque · hecha para tu Infinix Zero 40", 14f, Ui.TEXT2).apply { setPadding(dp(6f), dp(2f), 0, 0) })

        // Tarjeta resumen
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val r = dp(22f).toFloat()
            background = Ui.rounded(Ui.ACCENT_SOFT, r, dp(1f), Ui.argbAccent(90))
            setPadding(dp(16f), dp(16f), dp(16f), dp(16f))
        }
        hero.addView(Ui.iconBox(this, R.drawable.ic_video))
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(Ui.text(this, "Así grabás ahora", 12.5f, Ui.ACCENT, true))
        summary = Ui.text(this, "", 15f, Ui.TEXT, true).apply { setPadding(0, dp(3f), 0, 0) }
        texts.addView(summary)
        hero.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(14f) })
        column.addView(hero, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(20f) })
    }

    private fun refreshSummary() {
        val res = when (prefs.videoRes) { 0 -> "2K"; 1 -> "1080p"; 3 -> "4K"; else -> "720p" }
        val fps = if (prefs.fps60 && prefs.videoRes != 0) 60 else 30
        val mic = if (!prefs.audioOn) "sin audio" else AudioSetup.build(this, prefs, force = true)?.label ?: "micrófono"
        summary.text = "$res · $fps fps · $mic"
        micValue?.text = AudioSetup.build(this, prefs, force = true)?.label ?: ""
        val firstLine = prefs.prompterText.trim().lineSequence().firstOrNull()?.take(24)
        prompterValue?.text = if (firstLine.isNullOrBlank()) "Vacío" else "$firstLine…"
    }

    // ---------------------------------------------------------------- Video

    private fun backCameraSizes(): List<android.util.Size> = try {
        val cm = getSystemService(CameraManager::class.java)
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
        if (id == null) emptyList() else {
            cm.getCameraCharacteristics(id).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(MediaRecorder::class.java)?.toList() ?: emptyList()
        }
    } catch (e: Exception) { emptyList() }

    private fun buildVideo() {
        column.addView(Ui.sectionTitle(this, "Video", R.drawable.ic_video))
        val sizes = backCameraSizes()
        val resOpts = mutableListOf<Pair<Int, String>>()
        if (sizes.any { it.width >= 3840 }) resOpts += 3 to "4K"
        if (sizes.isEmpty() || sizes.any { it.width == 2560 }) resOpts += 0 to "2K"
        resOpts += 1 to "1080p"
        resOpts += 2 to "720p"
        val resIdx = resOpts.indexOfFirst { it.first == prefs.videoRes }.coerceAtLeast(0)

        addCard(
            Ui.segmentedRow(this, R.drawable.ic_resolution, "Resolución", "La máxima de tu Zero 40 es 2K (1440p)",
                resOpts.map { it.second }, resIdx) { prefs.videoRes = resOpts[it].first; refreshSummary() },
            Ui.segmentedRow(this, R.drawable.ic_speed, "Cuadros por segundo", "60 fps funciona en 1080p o menos",
                listOf("30 fps", "60 fps"), if (prefs.fps60) 1 else 0) { prefs.fps60 = it == 1; refreshSummary() },
            Ui.segmentedRow(this, R.drawable.ic_quality, "Calidad de imagen", "Más calidad = archivos más pesados",
                listOf("Liviana", "Alta", "Máxima"), prefs.videoBitrateLevel.coerceIn(0, 2)) { prefs.videoBitrateLevel = it },
            Ui.segmentedRow(this, R.drawable.ic_film, "Formato del archivo", "H.264 anda en todos lados · HEVC pesa la mitad",
                listOf("H.264", "HEVC"), if (prefs.hevc) 1 else 0) { prefs.hevc = it == 1 },
            Ui.toggleRow(this, R.drawable.ic_stabilize, "Estabilización", "Electrónica + óptica, para grabar caminando",
                prefs.stabilization) { prefs.stabilization = it },
            Ui.toggleRow(this, R.drawable.ic_flicker, "Antiparpadeo 50 Hz", "Saca las rayas que hacen las luces de casa",
                prefs.antibanding50) { prefs.antibanding50 = it }
        )
    }

    private fun buildShorts() {
        column.addView(Ui.sectionTitle(this, "Shorts", R.drawable.ic_shorts))
        addCard(
            Ui.segmentedRow(this, R.drawable.ic_hourglass, "Duración máxima", "Corta sola y te avisa al llegar",
                listOf("1 min", "3 min", "Sin límite"), prefs.shortsLimit.coerceIn(0, 2)) { prefs.shortsLimit = it },
            Ui.toggleRow(this, R.drawable.ic_guides, "Zonas seguras", "Marca lo que tapa YouTube (título y botones)",
                prefs.guides) { prefs.guides = it }
        )
    }

    // ---------------------------------------------------------------- Audio

    private fun buildAudio() {
        column.addView(Ui.sectionTitle(this, "Audio", R.drawable.ic_mic))
        val (micRow, micVal) = Ui.actionRow(this, R.drawable.ic_mic, "Micrófono y sonido", "Interno, USB-C, cable o Bluetooth",
            AudioSetup.build(this, prefs, force = true)?.label) {
            Ui.showMicSheet(this, prefs) { refreshSummary() }
        }
        micValue = micVal
        val bitrates = listOf(128_000, 192_000, 256_000, 320_000)

        // Fila de prueba con vúmetro
        val testBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val testRow = Ui.Row(this, R.drawable.ic_play, "Probar micrófono", "Graba 5 segundos y te los hace escuchar")
        val btn = Ui.pillButton(this, "Probar", true) { testMic() }
        testButton = btn
        testRow.trailing.addView(btn)
        testBox.addView(testRow)
        val m = AudioMeterView(this, null).apply { visibility = View.GONE }
        meter = m
        testBox.addView(m, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24f)).apply {
            marginStart = dp(68f); marginEnd = dp(16f); bottomMargin = dp(14f)
        })

        addCard(
            Ui.toggleRow(this, R.drawable.ic_sound, "Grabar audio en los videos", "Apagalo si la voz la grabás aparte",
                prefs.audioOn) { prefs.audioOn = it; refreshSummary() },
            micRow,
            Ui.segmentedRow(this, R.drawable.ic_stereo, "Canales", "Estéreo si tu micrófono lo soporta",
                listOf("Mono", "Estéreo"), if (prefs.stereo) 1 else 0) { prefs.stereo = it == 1 },
            Ui.segmentedRow(this, R.drawable.ic_wave, "Calidad de audio", "256 kbps ya suena muy bien",
                listOf("128", "192", "256", "320"), bitrates.indexOf(prefs.audioBitrate).let { if (it < 0) 2 else it }) { prefs.audioBitrate = bitrates[it] },
            testBox
        )
    }

    private fun testMic() {
        if (testing) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Falta el permiso de micrófono (abrí la cámara y aceptalo)", Toast.LENGTH_LONG).show()
            return
        }
        val cfg = AudioSetup.build(this, prefs, force = true) ?: return
        val file = File(cacheDir, "prueba_mic.m4a")
        val rec = try {
            AudioSetup.prepareAudioRecorder(this, cfg) { it.setOutputFile(file.absolutePath) }
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo usar ese micrófono", Toast.LENGTH_LONG).show(); return
        }
        val comm = if (cfg.needsComm) AudioSetup.setComm(this, cfg.device) else false
        try { rec.start() } catch (e: Exception) {
            rec.release(); if (comm) AudioSetup.clearComm(this)
            Toast.makeText(this, "El micrófono está ocupado", Toast.LENGTH_SHORT).show(); return
        }
        testing = true
        testButton?.alpha = 0.4f
        val mv = meter
        mv?.visibility = View.VISIBLE
        val start = SystemClock.elapsedRealtime()
        val tick = object : Runnable {
            override fun run() {
                val t = SystemClock.elapsedRealtime() - start
                if (t < 5000) {
                    mv?.setAmplitude(try { rec.maxAmplitude } catch (e: Exception) { 0 })
                    main.postDelayed(this, 70)
                    return
                }
                try { rec.stop() } catch (_: Exception) { }
                rec.release()
                if (comm) AudioSetup.clearComm(this@SettingsActivity)
                mv?.reset()
                try {
                    MediaPlayer().apply {
                        setDataSource(file.absolutePath)
                        setOnCompletionListener {
                            it.release()
                            testing = false
                            testButton?.alpha = 1f
                            mv?.visibility = View.GONE
                        }
                        prepare()
                        start()
                    }
                    Toast.makeText(this@SettingsActivity, "Escuchando la prueba…", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    testing = false
                    testButton?.alpha = 1f
                    mv?.visibility = View.GONE
                }
            }
        }
        main.post(tick)
    }

    // ---------------------------------------------------------------- Guion

    private fun buildPrompter() {
        column.addView(Ui.sectionTitle(this, "Teleprompter", R.drawable.ic_script))
        val (row, value) = Ui.actionRow(this, R.drawable.ic_script, "Guion", "Se muestra arriba, cerca de la cámara", "") {
            Ui.showPrompterSheet(this, prefs) { refreshSummary() }
        }
        prompterValue = value
        value.visibility = View.VISIBLE
        val sizeIdx = Ui.PROMPTER_SIZES.indexOf(prefs.prompterSize).let { if (it < 0) 1 else it }
        addCard(
            row,
            Ui.toggleRow(this, R.drawable.ic_guides, "Mostrar al grabar", "En YouTube, Shorts y Voz", prefs.prompterOn) { prefs.prompterOn = it },
            Ui.segmentedRow(this, R.drawable.ic_speed, "Velocidad", null,
                listOf("Lenta", "Normal", "Rápida", "Muy rápida"), prefs.prompterSpeed.coerceIn(0, 3)) { prefs.prompterSpeed = it },
            Ui.segmentedRow(this, R.drawable.ic_textsize, "Tamaño de letra", null,
                listOf("Chica", "Media", "Grande", "Enorme"), sizeIdx) { prefs.prompterSize = Ui.PROMPTER_SIZES[it] }
        )
    }

    // ---------------------------------------------------------------- Foto

    private fun buildPhoto() {
        column.addView(Ui.sectionTitle(this, "Foto", R.drawable.ic_camera))
        val jpg = listOf(85, 95, 100)
        addCard(
            Ui.segmentedRow(this, R.drawable.ic_resolution, "Resolución", "12MP junta píxeles: mejor de noche. 108MP: más detalle con sol",
                listOf("12MP", "108MP"), if (prefs.maxRes) 1 else 0) { prefs.maxRes = it == 1 },
            Ui.segmentedRow(this, R.drawable.ic_auto, "Prioridad", null,
                listOf("Calidad", "Velocidad"), if (prefs.fastShutter) 1 else 0) { prefs.fastShutter = it == 1 },
            Ui.segmentedRow(this, R.drawable.ic_quality, "Calidad JPG", null,
                listOf("85%", "95%", "100%"), jpg.indexOf(prefs.jpegQuality).let { if (it < 0) 1 else it }) { prefs.jpegQuality = jpg[it] },
            Ui.toggleRow(this, R.drawable.ic_mirror, "Espejar selfies", "Que la foto salga como te ves en pantalla",
                prefs.mirrorFront) { prefs.mirrorFront = it }
        )
    }

    // ---------------------------------------------------------------- Pantalla

    private fun buildScreen() {
        column.addView(Ui.sectionTitle(this, "Pantalla y controles", R.drawable.ic_grid))
        addCard(
            Ui.toggleRow(this, R.drawable.ic_grid, "Cuadrícula", "Regla de los tercios", prefs.grid) { prefs.grid = it },
            Ui.toggleRow(this, R.drawable.ic_level, "Nivel", "Se pone amarillo cuando el celu está derecho", prefs.level) { prefs.level = it },
            Ui.toggleRow(this, R.drawable.ic_histogram, "Histograma", "En fotos: avisa si se quema la luz", prefs.histogram) { prefs.histogram = it },
            Ui.toggleRow(this, R.drawable.ic_sound, "Sonidos", "Obturador, inicio y fin de grabación", prefs.sound) { prefs.sound = it },
            Ui.toggleRow(this, R.drawable.ic_phone_buttons, "Botones de volumen", "Sacan foto o empiezan a grabar", prefs.volumeShutter) { prefs.volumeShutter = it }
        )
    }

    // ---------------------------------------------------------------- Acerca de

    private fun buildAbout() {
        column.addView(Ui.sectionTitle(this, "Tu celular", R.drawable.ic_info))
        val (report, _) = Ui.actionRow(this, R.drawable.ic_camera, "Qué permite tu cámara", "Lentes, resoluciones, RAW, ISO…", null) { showReport() }
        val version = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "?" }
        val about = Ui.Row(this, R.drawable.ic_info, "Enfoque $version", "Hecha a medida con Claude")
        addCard(report, about)
    }

    private fun showReport() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = try { future.get() } catch (e: Exception) { return@addListener }
            val lenses = try { LensFinder.find(this, provider) } catch (e: Exception) { emptyList() }
            val ext = ExtensionsManager.getInstanceAsync(this, provider)
            ext.addListener({
                val em = try { ext.get() } catch (e: Exception) { null }
                val report = try { CameraReport.build(this, provider, em, lenses) } catch (e: Exception) { "Error: ${e.message}" }
                Ui.sheet(this, "Tu cámara", "Lo que el Zero 40 le deja usar a una app") { c, _ ->
                    c.addView(Ui.text(this, report, 12f, Ui.TEXT).apply {
                        typeface = Typeface.MONOSPACE
                        setTextIsSelectable(true)
                        val p = dp(14f)
                        setPadding(p, p, p, p)
                        background = Ui.rounded(Ui.CARD, dp(16f).toFloat())
                    })
                    c.addView(Ui.pillButton(this, "Copiar todo", true, R.drawable.ic_copy) {
                        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Cámaras", report))
                        Toast.makeText(this, "Copiado", Toast.LENGTH_SHORT).show()
                    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = dp(16f); gravity = Gravity.END
                    })
                }
            }, ContextCompat.getMainExecutor(this))
        }, ContextCompat.getMainExecutor(this))
    }
}
