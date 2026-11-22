package com.enfoque.camara

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.MediaActionSound
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.os.SystemClock
import android.provider.BaseColumns
import android.provider.MediaStore
import android.text.InputType
import android.util.Log
import android.util.Size
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.ZoomState
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.slider.Slider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener, SurfaceHolder.Callback {

    enum class Mode(val label: String) { PHOTO("FOTO"), PRO("PRO"), YOUTUBE("YOUTUBE"), SHORTS("SHORTS"), VOICE("VOZ") }
    enum class ProParam { ISO, SHUTTER, FOCUS, WB, EV }

    private data class BindCfg(val ext: Int?, val analysis: Boolean, val format: Int, val maxRes: Boolean)

    companion object {
        private const val TAG = "Enfoque"
        private const val ALBUM = "Enfoque"
        private val ISO_STEPS = listOf(50, 64, 80, 100, 125, 160, 200, 250, 320, 400, 500, 640, 800, 1000, 1250, 1600, 2000, 2500, 3200, 4000, 5000, 6400, 8000, 12800)
        private val SHUTTER_STEPS: List<Long> = listOf(
            8000, 4000, 2000, 1000, 500, 250, 125, 60, 30, 15, 8, 4, 2
        ).map { 1_000_000_000L / it } + listOf(500_000_000L, 1_000_000_000L, 2_000_000_000L, 4_000_000_000L, 8_000_000_000L, 15_000_000_000L, 30_000_000_000L)
        private val WB_MODES = listOf(
            CameraMetadata.CONTROL_AWB_MODE_AUTO to "Auto",
            CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT to "Sol",
            CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT to "Nublado",
            CameraMetadata.CONTROL_AWB_MODE_SHADE to "Sombra",
            CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT to "Lámpara",
            CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT to "Tubo"
        )
    }

    // ---------- Vistas ----------
    private lateinit var root: ConstraintLayout
    private lateinit var previewView: PreviewView
    private lateinit var videoSurface: SurfaceView
    private lateinit var voicePanel: View
    private lateinit var voiceTime: TextView
    private lateinit var gridOverlay: GridOverlay
    private lateinit var levelOverlay: LevelOverlay
    private lateinit var focusRing: FocusRingView
    private lateinit var guideOverlay: GuideOverlay
    private lateinit var prompter: PrompterView
    private lateinit var recBadge: TextView
    private lateinit var audioMeter: AudioMeterView
    private lateinit var histogram: HistogramView
    private lateinit var flashOverlay: View
    private lateinit var infoText: TextView
    private lateinit var countdownText: TextView
    private lateinit var btnFlash: ImageView
    private lateinit var statusPill: View
    private lateinit var statusText: TextView
    private lateinit var statusChevron: ImageView
    private lateinit var btnSettings: ImageView
    private lateinit var quickPanel: LinearLayout
    private lateinit var evRow: View
    private lateinit var evLabel: TextView
    private lateinit var evSlider: Slider
    private lateinit var proPanel: View
    private lateinit var btnProAuto: TextView
    private lateinit var proSlider: Slider
    private lateinit var proValue: TextView
    private lateinit var chipIso: TextView
    private lateinit var chipShutter: TextView
    private lateinit var chipFocus: TextView
    private lateinit var chipWb: TextView
    private lateinit var chipEv: TextView
    private lateinit var zoomRow: LinearLayout
    private lateinit var modeRow: LinearLayout
    private lateinit var thumbnail: ImageView
    private lateinit var shutterButton: ShutterButton
    private lateinit var flipButton: ImageView
    private lateinit var pauseButton: ImageView
    private val modeViews = mutableMapOf<Mode, TextView>()

    // ---------- Estado foto (CameraX) ----------
    private lateinit var prefs: Prefs
    private var cameraProvider: ProcessCameraProvider? = null
    private var extensionsManager: ExtensionsManager? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null

    private var lenses: List<Lens> = emptyList()
    private var currentLens: Lens? = null
    private var useFront = false
    private var mode = Mode.PHOTO
    private var activeExtension: Int? = null
    private var boundFormat = ImageCapture.OUTPUT_FORMAT_JPEG
    private var supportedFormats: List<Int> = listOf(ImageCapture.OUTPUT_FORMAT_JPEG)
    private var hdrOn = false
    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private var torchOn = false
    private var timerSec = 0
    private var surfaceRotation = Surface.ROTATION_0
    private var deviceDeg = 0
    private var uiRotation = 0f
    private var pendingZoom: Float? = null
    private var logicalMinZoom = 1f

    // ---------- Estado video / voz ----------
    private var engine: VideoEngine? = null
    private var videoCfg: VideoConfig? = null
    private var surfaceReady = false
    private var pendingOpen: Runnable? = null
    private var engineMinZoom = 1f
    private var engineMaxZoom = 1f
    private var engineZoom = 1f
    private var lookLocked = false
    private var voice: VoiceRecorder? = null
    private var recStartMs = 0L
    private var pausedAtMs = 0L
    private var pausedTotalMs = 0L
    private var recPaused = false
    private var micTesting = false
    private var settingsSnapshot: String? = null
    private var mpLabel = "12MP"

    // ---------- Modo PRO ----------
    private var proIso: Int? = null
    private var proShutterNs: Long? = null
    private var proFocus: Float? = null
    private var proWbIndex = 0
    private var proEvIndex = 0
    private var selectedPro = ProParam.ISO
    private var isoValues: List<Int> = emptyList()
    private var shutterValues: List<Long> = emptyList()
    @Volatile private var liveIso = 0
    @Volatile private var liveExposure = 0L
    @Volatile private var liveFocus = 0f

    // ---------- Otros ----------
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val sound = MediaActionSound()
    private var orientationListener: OrientationEventListener? = null
    private var sensorManager: SensorManager? = null
    private var lastUri: Uri? = null
    private var lastIsVideo = false
    private var lastHistogramMs = 0L
    private var countdownRunnable: Runnable? = null
    private var busy = false

    private val hideInfo = Runnable { infoText.visibility = View.GONE }
    private val hideEv = Runnable { if (mode != Mode.PRO) evRow.visibility = View.GONE }
    private val proTicker = object : Runnable {
        override fun run() {
            if (mode == Mode.PRO) { updateProChips(); main.postDelayed(this, 400) }
        }
    }
    private val recTicker = object : Runnable {
        override fun run() {
            if (!isRecording()) return
            updateRecHud()
            main.postDelayed(this, 80)
        }
    }

    private val liveCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            result.get(CaptureResult.SENSOR_SENSITIVITY)?.let { liveIso = it }
            result.get(CaptureResult.SENSOR_EXPOSURE_TIME)?.let { liveExposure = it }
            result.get(CaptureResult.LENS_FOCUS_DISTANCE)?.let { liveFocus = it }
        }
    }

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasPerm(Manifest.permission.CAMERA)) startCamera()
        else showInfo("Necesito permiso de cámara para funcionar", 8000)
        if (!hasPerm(Manifest.permission.RECORD_AUDIO)) showInfo("Sin permiso de micrófono los videos salen sin sonido", 6000)
    }

    // =====================================================================================
    // Ciclo de vida
    // =====================================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        prefs = Prefs(this)
        bindViews()
        setupFullscreen()
        setupListeners()
        videoSurface.holder.addCallback(this)
        mode = Mode.values().getOrElse(prefs.lastMode) { Mode.PHOTO }
        prompter.text = prefs.prompterText
        prompter.speedLevel = prefs.prompterSpeed
        prompter.textSizeSp = prefs.prompterSize.toFloat()
        refreshModeUi()
        refreshTopChips()
        sensorManager = getSystemService(SENSOR_SERVICE) as? SensorManager
        sound.load(MediaActionSound.SHUTTER_CLICK)

        if (hasPerm(Manifest.permission.CAMERA)) startCamera()
        val needed = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO).filter { !hasPerm(it) }
        if (needed.isNotEmpty()) permLauncher.launch(needed.toTypedArray())
        loadLastThumbnail()
    }

    override fun onResume() {
        super.onResume()
        orientationListener?.enable()
        sensorManager?.let { sm ->
            sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        }
        if (mode == Mode.PRO) main.post(proTicker)
        val snap = settingsSnapshot
        if (snap != null) {
            settingsSnapshot = null
            if (snap != prefs.snapshot()) applySettingsChanges()
        }
    }

    override fun onPause() {
        super.onPause()
        if (engine?.isRecording == true) engine?.stopRecording()
        voice?.let { if (it.isRecording) stopVoice() }
        cancelCountdown()
        orientationListener?.disable()
        sensorManager?.unregisterListener(this)
        main.removeCallbacks(proTicker)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopEngine()
        cameraExecutor.shutdown()
        analysisExecutor.shutdown()
        sound.release()
    }

    private fun hasPerm(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun isVideoMode(m: Mode = mode) = m == Mode.YOUTUBE || m == Mode.SHORTS
    private fun isPhotoMode(m: Mode = mode) = m == Mode.PHOTO || m == Mode.PRO
    private fun isRecording() = engine?.isRecording == true || voice?.isRecording == true

    private fun bindViews() {
        root = findViewById(R.id.root)
        previewView = findViewById(R.id.previewView)
        videoSurface = findViewById(R.id.videoSurface)
        voicePanel = findViewById(R.id.voicePanel)
        voiceTime = findViewById(R.id.voiceTime)
        gridOverlay = findViewById(R.id.gridOverlay)
        levelOverlay = findViewById(R.id.levelOverlay)
        focusRing = findViewById(R.id.focusRing)
        guideOverlay = findViewById(R.id.guideOverlay)
        prompter = findViewById(R.id.prompter)
        recBadge = findViewById(R.id.recBadge)
        audioMeter = findViewById(R.id.audioMeter)
        histogram = findViewById(R.id.histogram)
        flashOverlay = findViewById(R.id.flashOverlay)
        infoText = findViewById(R.id.infoText)
        countdownText = findViewById(R.id.countdownText)
        btnFlash = findViewById(R.id.btnFlash)
        statusPill = findViewById(R.id.statusPill)
        statusText = findViewById(R.id.statusText)
        statusChevron = findViewById(R.id.statusChevron)
        btnSettings = findViewById(R.id.btnSettings)
        quickPanel = findViewById(R.id.quickPanel)
        evRow = findViewById(R.id.evRow)
        evLabel = findViewById(R.id.evLabel)
        evSlider = findViewById(R.id.evSlider)
        proPanel = findViewById(R.id.proPanel)
        btnProAuto = findViewById(R.id.btnProAuto)
        proSlider = findViewById(R.id.proSlider)
        proValue = findViewById(R.id.proValue)
        chipIso = findViewById(R.id.chipIso)
        chipShutter = findViewById(R.id.chipShutter)
        chipFocus = findViewById(R.id.chipFocus)
        chipWb = findViewById(R.id.chipWb)
        chipEv = findViewById(R.id.chipEv)
        zoomRow = findViewById(R.id.zoomRow)
        modeRow = findViewById(R.id.modeRow)
        thumbnail = findViewById(R.id.thumbnail)
        shutterButton = findViewById(R.id.shutterButton)
        flipButton = findViewById(R.id.flipButton)
        pauseButton = findViewById(R.id.pauseButton)
        modeViews[Mode.PHOTO] = findViewById(R.id.modePhoto)
        modeViews[Mode.PRO] = findViewById(R.id.modePro)
        modeViews[Mode.YOUTUBE] = findViewById(R.id.modeYoutube)
        modeViews[Mode.SHORTS] = findViewById(R.id.modeShorts)
        modeViews[Mode.VOICE] = findViewById(R.id.modeVoice)
        thumbnail.clipToOutline = true
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
    }

    private fun setupFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val ctl = WindowInsetsControllerCompat(window, window.decorView)
        ctl.hide(WindowInsetsCompat.Type.statusBars())
        ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
    }

    // =====================================================================================
    // Controles
    // =====================================================================================

    @SuppressLint("ClickableViewAccessibility")
    private fun setupListeners() {
        shutterButton.setOnClickListener { onShutter() }
        flipButton.setOnClickListener { flipCamera() }
        thumbnail.setOnClickListener { openLast() }
        pauseButton.setOnClickListener { togglePause() }

        for ((m, v) in modeViews) v.setOnClickListener { setMode(m) }

        btnFlash.setOnClickListener { cycleFlash() }
        statusPill.setOnClickListener { toggleQuickPanel() }
        btnSettings.setOnClickListener { openSettings() }

        evSlider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val idx = value.roundToInt()
            camera?.cameraControl?.setExposureCompensationIndex(idx)
            updateEvLabel(idx)
            main.removeCallbacks(hideEv); main.postDelayed(hideEv, 5000)
        }

        proSlider.addOnChangeListener { _, value, fromUser -> if (fromUser) onProSlider(value.roundToInt()) }
        btnProAuto.setOnClickListener { setProAuto() }
        chipIso.setOnClickListener { selectPro(ProParam.ISO) }
        chipShutter.setOnClickListener { selectPro(ProParam.SHUTTER) }
        chipFocus.setOnClickListener { selectPro(ProParam.FOCUS) }
        chipWb.setOnClickListener { selectPro(ProParam.WB) }
        chipEv.setOnClickListener { selectPro(ProParam.EV) }

        val scale = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                setZoom(currentZoom() * detector.scaleFactor)
                return true
            }
        })
        val gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onSingleTapUp(e: MotionEvent): Boolean { focusAt(e.x, e.y, false); return true }
            override fun onLongPress(e: MotionEvent) { focusAt(e.x, e.y, true) }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (abs(velocityX) > abs(velocityY) * 1.5f && abs(velocityX) > 1200f) {
                    shiftMode(if (velocityX < 0) 1 else -1); return true
                }
                return false
            }
        })
        val touch = View.OnTouchListener { _, ev ->
            if (quickPanel.visibility == View.VISIBLE) {
                if (ev.actionMasked == MotionEvent.ACTION_DOWN) hideQuickPanel()
                return@OnTouchListener true
            }
            scale.onTouchEvent(ev)
            if (!scale.isInProgress && ev.pointerCount == 1) gestures.onTouchEvent(ev)
            true
        }
        previewView.setOnTouchListener(touch)
        videoSurface.setOnTouchListener(touch)
        voicePanel.setOnTouchListener(touch)

        orientationListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) return
                deviceDeg = ((orientation + 45) / 90 * 90) % 360
                val r = when (orientation) {
                    in 45 until 135 -> Surface.ROTATION_270
                    in 135 until 225 -> Surface.ROTATION_180
                    in 225 until 315 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                if (r != surfaceRotation) {
                    surfaceRotation = r
                    imageCapture?.targetRotation = r
                    if (!isRecording()) rotateUi(r)
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (prefs.volumeShutter && (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            if (event?.repeatCount == 0) onShutter()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_CAMERA) { onShutter(); return true }
        return super.onKeyDown(keyCode, event)
    }

    private fun rotationFor(r: Int) = when (r) { Surface.ROTATION_90 -> 90f; Surface.ROTATION_180 -> 180f; Surface.ROTATION_270 -> -90f; else -> 0f }

    private fun rotateUi(r: Int) {
        val deg = rotationFor(r)
        uiRotation = deg
        val views = mutableListOf<View>(thumbnail, flipButton, pauseButton, countdownText)
        for (i in 0 until zoomRow.childCount) views += zoomRow.getChildAt(i)
        for (v in views) v.animate().rotation(deg).setDuration(250).start()
        // En YouTube las guías y el guion se acomodan a cómo tenés el celu.
        val overlayDeg = if (mode == Mode.YOUTUBE) (if (deg == 0f || deg == 180f) 0f else deg) else 0f
        guideOverlay.rotationDeg = overlayDeg
        prompter.rotationDeg = overlayDeg
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!prefs.level) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val g = sqrt(x * x + y * y + z * z)
        if (g < 1f || abs(z) > g * 0.85f) { levelOverlay.update(Float.NaN); return }
        val a = Math.toDegrees(atan2(x.toDouble(), y.toDouble())).toFloat()
        val snapped = round(a / 90f) * 90f
        levelOverlay.update(a - snapped)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // =====================================================================================
    // Arranque y cambio de "escenario" (foto con CameraX / video propio / voz)
    // =====================================================================================

    private fun startCamera() {
        if (cameraProvider != null) return
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = try { future.get() } catch (e: Exception) {
                showInfo("No pude iniciar la cámara: ${e.message}", 8000); return@addListener
            }
            cameraProvider = provider
            lenses = try { LensFinder.find(this, provider) } catch (e: Exception) { Log.e(TAG, "lentes", e); emptyList() }
            val extFuture = ExtensionsManager.getInstanceAsync(this, provider)
            extFuture.addListener({
                extensionsManager = try { extFuture.get() } catch (e: Exception) { null }
                selectDefaultLens()
                applyStage()
            }, ContextCompat.getMainExecutor(this))
        }, ContextCompat.getMainExecutor(this))
    }

    private fun applyStage() {
        applyRatio()
        when {
            isVideoMode() -> {
                cameraProvider?.unbindAll()
                camera = null; imageCapture = null
                previewView.visibility = View.INVISIBLE
                voicePanel.visibility = View.GONE
                if (videoSurface.visibility != View.VISIBLE) {
                    videoSurface.visibility = View.VISIBLE // surfaceCreated arranca el motor
                } else if (engine == null) {
                    startEngine()
                }
            }
            mode == Mode.VOICE -> {
                stopEngine()
                cameraProvider?.unbindAll()
                camera = null; imageCapture = null
                previewView.visibility = View.INVISIBLE
                videoSurface.visibility = View.GONE
                voicePanel.visibility = View.VISIBLE
                voiceTime.text = "00:00"
                zoomRow.removeAllViews()
            }
            else -> {
                stopEngine()
                videoSurface.visibility = View.GONE
                voicePanel.visibility = View.GONE
                previewView.visibility = View.VISIBLE
                bindCamera()
            }
        }
        applyPrompterVisibility()
        refreshTopChips()
    }

    private fun backLenses() = lenses.filter { it.facing == CameraCharacteristics.LENS_FACING_BACK }
    private fun frontLenses() = lenses.filter { it.facing == CameraCharacteristics.LENS_FACING_FRONT }

    private fun selectDefaultLens() {
        val group = if (useFront) frontLenses() else backLenses()
        currentLens = group.firstOrNull { it.isMain } ?: group.firstOrNull() ?: lenses.firstOrNull()
    }

    private fun applyRatio() {
        val ratio = if (isVideoMode() || mode == Mode.VOICE || prefs.ratio169) "9:16" else "3:4"
        for (v in listOf<View>(previewView, videoSurface)) {
            val lp = v.layoutParams as ConstraintLayout.LayoutParams
            if (lp.dimensionRatio != ratio) { lp.dimensionRatio = ratio; v.layoutParams = lp }
        }
    }

    // =====================================================================================
    // Video (motor propio)
    // =====================================================================================

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        if (isVideoMode()) startEngine()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        val cfg = videoCfg ?: return
        val p = pendingOpen ?: return
        if (width == cfg.previewSize.width && height == cfg.previewSize.height) {
            main.removeCallbacks(p)
            p.run()
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        stopEngine()
    }

    private fun startEngine() {
        if (!isVideoMode() || !surfaceReady || engine != null) return
        if (!hasPerm(Manifest.permission.CAMERA)) return
        val lens = currentLens ?: return
        val cfg = buildVideoConfig(lens) ?: run { showInfo("Este lente no permite grabar video", 5000); return }
        videoCfg = cfg
        val e = VideoEngine(this, engineListener)
        engine = e
        lookLocked = false
        val open = object : Runnable {
            override fun run() {
                if (pendingOpen !== this) return
                pendingOpen = null
                if (engine === e && surfaceReady) e.open(lens, videoSurface.holder.surface, cfg)
            }
        }
        pendingOpen = open
        val frame = videoSurface.holder.surfaceFrame
        if (frame.width() == cfg.previewSize.width && frame.height() == cfg.previewSize.height) {
            main.post(open)
        } else {
            videoSurface.holder.setFixedSize(cfg.previewSize.width, cfg.previewSize.height)
            main.postDelayed(open, 700) // por si surfaceChanged no llega
        }
        refreshTopChips()
    }

    private fun stopEngine() {
        pendingOpen?.let { main.removeCallbacks(it) }
        pendingOpen = null
        val e = engine ?: return
        engine = null
        e.close()
    }

    private fun restartEngine() {
        if (isRecording()) return
        stopEngine()
        startEngine()
    }

    private fun buildVideoConfig(lens: Lens): VideoConfig? {
        val map = lens.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return null
        fun is169(s: Size) = abs(s.width.toFloat() / s.height - 16f / 9f) < 0.02f
        val recSizes = (map.getOutputSizes(MediaRecorder::class.java) ?: emptyArray()).filter { is169(it) }
        if (recSizes.isEmpty()) return null
        val wantW = when (prefs.videoRes) { 3 -> 3840; 0 -> 2560; 1 -> 1920; else -> 1280 }
        val size = recSizes.filter { it.width <= wantW }.maxByOrNull { it.width } ?: recSizes.minByOrNull { it.width } ?: return null
        val prevSizes = (map.getOutputSizes(SurfaceHolder::class.java) ?: emptyArray()).filter { is169(it) && it.width <= 1920 }
        val prev = prevSizes.maxByOrNull { it.width } ?: size
        val fps = if (prefs.fps60 && size.width <= 1920 && lens.aeFpsRanges.any { it.upper >= 60 }) 60 else 30
        val base = when {
            size.width >= 3840 -> 40
            size.width >= 2560 -> 24
            size.width >= 1920 -> 14
            else -> 8
        } * 1_000_000
        val mult = listOf(0.7, 1.0, 1.5)[prefs.videoBitrateLevel.coerceIn(0, 2)] * (if (fps == 60) 1.5 else 1.0)
        val audio = if (hasPerm(Manifest.permission.RECORD_AUDIO)) AudioSetup.build(this, prefs) else null
        return VideoConfig(size, prev, fps, (base * mult).toInt(), prefs.hevc, prefs.stabilization, prefs.antibanding50, audio)
    }

    /** Hacia dónde queda "arriba" en el archivo: Shorts siempre vertical, YouTube siempre horizontal. */
    private fun videoHint(lens: Lens): Int {
        val sensor = lens.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
        val dev = when (mode) {
            Mode.SHORTS -> 0
            Mode.YOUTUBE -> if (deviceDeg == 270) 270 else 90
            else -> deviceDeg
        }
        return if (lens.facing == CameraCharacteristics.LENS_FACING_FRONT) (sensor - dev + 360) % 360 else (sensor + dev) % 360
    }

    private val engineListener = object : VideoEngine.Listener {
        override fun onCameraReady(minZoom: Float, maxZoom: Float) {
            engineMinZoom = minZoom
            engineMaxZoom = maxZoom
            logicalMinZoom = minZoom
            engineZoom = 1f
            if (torchOn && !useFront) engine?.setTorch(true)
            pendingZoom?.let { z -> engineZoom = engine?.setZoom(z) ?: 1f; pendingZoom = null }
            videoCfg = engine?.cfg ?: videoCfg
            buildZoomRow()
            refreshTopChips()
        }

        override fun onCameraError(msg: String) { showInfo(msg, 7000) }
        override fun onNotice(msg: String) { showInfo(msg, 4000); videoCfg = engine?.cfg ?: videoCfg; refreshTopChips() }

        override fun onRecordingStarted() {
            if (prefs.sound) sound.play(MediaActionSound.START_VIDEO_RECORDING)
            onRecordingBegan()
        }

        override fun onRecordingFinished(uri: Uri?, error: String?) {
            onRecordingEnded()
            if (prefs.sound) sound.play(MediaActionSound.STOP_VIDEO_RECORDING)
            if (uri != null) {
                setLast(uri, true)
                showInfo("Video guardado en DCIM/$ALBUM")
            } else showInfo(error ?: "Error al grabar", 6000)
        }
    }

    private fun toggleVideo() {
        val e = engine ?: run { showInfo("La cámara de video todavía se está abriendo…"); return }
        if (e.isRecording) { e.stopRecording(); return }
        val lens = currentLens ?: return
        if (mode == Mode.YOUTUBE && (deviceDeg == 0 || deviceDeg == 180)) {
            showInfo("Ojo: tenés el celu vertical. El video se guarda horizontal igual.", 3000)
        }
        e.startRecording(videoHint(lens))
    }

    private fun onRecordingBegan() {
        recStartMs = SystemClock.elapsedRealtime()
        pausedTotalMs = 0L
        recPaused = false
        setRecordingUi(true)
        if (prefs.prompterOn && prefs.prompterText.isNotBlank()) { prompter.reset(); prompter.start() }
        main.post(recTicker)
    }

    private fun onRecordingEnded() {
        main.removeCallbacks(recTicker)
        setRecordingUi(false)
        prompter.pause()
        guideOverlay.progress = -1f
        audioMeter.reset()
        rotateUi(surfaceRotation)
    }

    private fun elapsedMs(): Long {
        val now = SystemClock.elapsedRealtime()
        val paused = pausedTotalMs + if (recPaused) now - pausedAtMs else 0L
        return now - recStartMs - paused
    }

    private fun fmtTime(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
        else String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
    }

    private fun shortsLimitMs(): Long = when (prefs.shortsLimit) { 0 -> 60_000L; 1 -> 180_000L; else -> 0L }

    private fun freeTimeText(bitsPerSecond: Int): String {
        return try {
            val free = StatFs(Environment.getExternalStorageDirectory().path).availableBytes
            val mins = free * 8 / bitsPerSecond.coerceAtLeast(1) / 60
            if (mins > 120) "quedan ${mins / 60}h" else "quedan ${mins}min"
        } catch (e: Exception) { "" }
    }

    private fun updateRecHud() {
        val amp = engine?.amplitude() ?: voice?.amplitude() ?: 0
        audioMeter.setAmplitude(amp)
        val t = elapsedMs()
        val dot = if (recPaused) "❚❚" else if ((t / 500) % 2 == 0L) "●" else "○"
        if (mode == Mode.VOICE) {
            voiceTime.text = fmtTime(t)
            recBadge.text = "$dot GRABANDO VOZ"
            return
        }
        val cfg = videoCfg
        val limit = if (mode == Mode.SHORTS) shortsLimitMs() else 0L
        val limitTxt = if (limit > 0) " / ${fmtTime(limit)}" else ""
        val extra = if (cfg != null) " · ${cfg.label} · ${freeTimeText(cfg.bitrate + 256_000)}" else ""
        recBadge.text = "$dot ${fmtTime(t)}$limitTxt$extra"
        if (limit > 0) {
            guideOverlay.progress = t.toFloat() / limit
            if (t >= limit && engine?.isRecording == true) {
                engine?.stopRecording()
                showInfo("Llegaste al límite del Short (${fmtTime(limit)})", 4000)
            }
        }
    }

    // =====================================================================================
    // Voz en off
    // =====================================================================================

    private fun toggleVoice() {
        val v = voice
        if (v != null && v.isRecording) { stopVoice(); return }
        if (!hasPerm(Manifest.permission.RECORD_AUDIO)) { permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO)); return }
        val cfg = AudioSetup.build(this, prefs, force = true) ?: return
        val rec = VoiceRecorder(this)
        val err = rec.start(cfg)
        if (err != null) { showInfo(err, 6000); return }
        voice = rec
        if (prefs.sound) sound.play(MediaActionSound.START_VIDEO_RECORDING)
        onRecordingBegan()
    }

    private fun stopVoice() {
        val v = voice ?: return
        val uri = v.stop()
        voice = null
        onRecordingEnded()
        if (prefs.sound) sound.play(MediaActionSound.STOP_VIDEO_RECORDING)
        if (uri != null) {
            showInfo("Audio guardado en Música/$ALBUM", 4000)
            lastUri = uri
            lastIsVideo = false
        } else showInfo("No se pudo guardar el audio", 5000)
    }

    // =====================================================================================
    // Foto (CameraX)
    // =====================================================================================

    private fun isExtAvailable(lens: Lens, ext: Int): Boolean {
        if (lens.physicalId != null) return false
        val em = extensionsManager ?: return false
        return try { em.isExtensionAvailable(lens.selector, ext) } catch (e: Exception) { false }
    }

    private fun desiredExtension(lens: Lens): Int? =
        if (mode == Mode.PHOTO && hdrOn && isExtAvailable(lens, ExtensionMode.HDR)) ExtensionMode.HDR else null

    private fun bindCamera() {
        if (!isPhotoMode()) return
        val provider = cameraProvider ?: return
        val lens = currentLens ?: run { showInfo("No encontré cámaras utilizables", 6000); return }

        supportedFormats = try {
            val s = ImageCapture.getImageCaptureCapabilities(lens.cameraInfo).supportedOutputFormats
            listOf(
                ImageCapture.OUTPUT_FORMAT_JPEG, ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR,
                ImageCapture.OUTPUT_FORMAT_RAW_JPEG, ImageCapture.OUTPUT_FORMAT_RAW
            ).filter { it == ImageCapture.OUTPUT_FORMAT_JPEG || it in s }
        } catch (e: Exception) { listOf(ImageCapture.OUTPUT_FORMAT_JPEG) }
        if (lens.physicalId != null) supportedFormats = listOf(ImageCapture.OUTPUT_FORMAT_JPEG)

        val ext = desiredExtension(lens)
        val wantFormat = if (ext == null && prefs.photoFormat in supportedFormats) prefs.photoFormat else ImageCapture.OUTPUT_FORMAT_JPEG
        val jpeg = ImageCapture.OUTPUT_FORMAT_JPEG
        val attempts = listOf(
            BindCfg(ext, prefs.histogram, wantFormat, prefs.maxRes),
            BindCfg(ext, false, wantFormat, prefs.maxRes),
            BindCfg(ext, false, jpeg, prefs.maxRes),
            BindCfg(ext, false, jpeg, false),
            BindCfg(null, false, jpeg, false)
        ).distinct()

        var lastError: Exception? = null
        for ((i, cfg) in attempts.withIndex()) {
            try {
                doBind(provider, lens, cfg)
                if (i > 0) {
                    val what = mutableListOf<String>()
                    if (cfg.analysis != attempts[0].analysis) what += "histograma"
                    if (cfg.format != attempts[0].format) what += "formato RAW/HDR"
                    if (cfg.maxRes != attempts[0].maxRes) what += "108MP"
                    if (cfg.ext != attempts[0].ext) what += "HDR del fabricante"
                    if (what.isNotEmpty()) showInfo("Este lente no soporta a la vez: ${what.joinToString()}", 4500)
                }
                return
            } catch (e: Exception) {
                Log.w(TAG, "bind falló con $cfg", e)
                lastError = e
            }
        }
        showInfo("No se pudo abrir este lente (${lastError?.message ?: "?"})", 7000)
    }

    private fun doBind(provider: ProcessCameraProvider, lens: Lens, cfg: BindCfg) {
        provider.unbindAll()
        camera?.cameraInfo?.zoomState?.removeObservers(this)
        camera = null; imageCapture = null

        val ratio = if (prefs.ratio169) AspectRatio.RATIO_16_9 else AspectRatio.RATIO_4_3
        val aspect = AspectRatioStrategy(ratio, AspectRatioStrategy.FALLBACK_RULE_AUTO)
        val interop = cfg.ext == null

        val previewBuilder = Preview.Builder()
            .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(aspect).build())
        if (interop) {
            val e = Camera2Interop.Extender(previewBuilder)
            lens.physicalId?.let { e.setPhysicalCameraId(it) }
            e.setSessionCaptureCallback(liveCallback)
        }
        val preview = previewBuilder.build()
        preview.setSurfaceProvider(previewView.surfaceProvider)
        val useCases = mutableListOf<UseCase>(preview)

        val capSel = ResolutionSelector.Builder().setAspectRatioStrategy(aspect)
        if (cfg.maxRes) {
            capSel.setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
            capSel.setAllowedResolutionMode(ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE)
        } else {
            capSel.setResolutionStrategy(ResolutionStrategy(Size(4000, 3000), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
        }
        val quality = mode == Mode.PRO || !prefs.fastShutter
        val ib = ImageCapture.Builder()
            .setCaptureMode(if (quality) ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY else ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setFlashMode(if (useFront) ImageCapture.FLASH_MODE_OFF else flashMode)
            .setResolutionSelector(capSel.build())
            .setJpegQuality(prefs.jpegQuality)
            .setTargetRotation(surfaceRotation)
        if (cfg.format != ImageCapture.OUTPUT_FORMAT_JPEG) ib.setOutputFormat(cfg.format)
        if (interop) {
            val e = Camera2Interop.Extender(ib)
            lens.physicalId?.let { e.setPhysicalCameraId(it) }
            if (mode == Mode.PRO) {
                e.setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY)
                e.setCaptureRequestOption(CaptureRequest.EDGE_MODE, CameraMetadata.EDGE_MODE_HIGH_QUALITY)
            }
        }
        val ic = ib.build()
        imageCapture = ic
        useCases += ic

        if (cfg.analysis && interop) {
            val ab = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                        .build()
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            lens.physicalId?.let { Camera2Interop.Extender(ab).setPhysicalCameraId(it) }
            val a = ab.build()
            a.setAnalyzer(analysisExecutor) { img -> analyzeHistogram(img) }
            useCases += a
        }

        val selector = if (cfg.ext != null) extensionsManager!!.getExtensionEnabledCameraSelector(lens.selector, cfg.ext) else lens.selector
        val cam = provider.bindToLifecycle(this, selector, *useCases.toTypedArray())
        camera = cam
        activeExtension = cfg.ext
        boundFormat = cfg.format
        onCameraBound(cam, lens, cfg)
    }

    private fun onCameraBound(cam: Camera, lens: Lens, cfg: BindCfg) {
        val zs = cam.cameraInfo.zoomState
        logicalMinZoom = zs.value?.minZoomRatio ?: 1f
        zs.observe(this) { z: ZoomState -> onZoomChanged(z) }
        pendingZoom?.let { cam.cameraControl.setZoomRatio(it); pendingZoom = null }

        val es = cam.cameraInfo.exposureState
        if (es.isExposureCompensationSupported) {
            val r = es.exposureCompensationRange
            evSlider.valueFrom = r.lower.toFloat()
            evSlider.valueTo = r.upper.toFloat()
            evSlider.stepSize = 1f
            evSlider.value = es.exposureCompensationIndex.coerceIn(r.lower, r.upper).toFloat()
            updateEvLabel(es.exposureCompensationIndex)
        }

        val isoR = lens.isoRange
        isoValues = if (isoR != null) ISO_STEPS.filter { it in isoR.lower..isoR.upper }.ifEmpty { listOf(isoR.lower, isoR.upper) } else emptyList()
        val expR = lens.exposureRange
        shutterValues = if (expR != null) SHUTTER_STEPS.filter { it in expR.lower..expR.upper } else emptyList()
        if (mode == Mode.PRO && cfg.ext == null) {
            if (lens.manualSensor) proEvIndex = es.exposureCompensationIndex
            else showInfo("Este lente no permite ISO/velocidad manual, sí enfoque y blancos", 4000)
        }
        applyCaptureOptions()

        if (torchOn && !useFront) cam.cameraControl.enableTorch(true)

        histogram.visibility = if (cfg.analysis) View.VISIBLE else View.GONE
        buildZoomRow()
        refreshTopChips()
        if (mode == Mode.PRO) configureProSlider()

        imageCapture?.resolutionInfo?.resolution?.let { s ->
            val mp = s.width.toLong() * s.height / 1_000_000.0
            mpLabel = if (mp >= 10) "${mp.roundToInt()}MP" else String.format(Locale.US, "%.1fMP", mp)
            refreshTopChips()
        }
    }

    private fun applyCaptureOptions() {
        val cam = camera ?: return
        if (activeExtension != null) return
        val lens = currentLens ?: return
        val b = CaptureRequestOptions.Builder()
        if (mode == Mode.PRO) {
            if (lens.manualSensor && (proIso != null || proShutterNs != null)) {
                val iso = proIso ?: liveIso.takeIf { it > 0 } ?: 100
                val exp = proShutterNs ?: liveExposure.takeIf { it > 0 } ?: 16_666_666L
                b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
                b.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso)
                b.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, exp)
                b.setCaptureRequestOption(CaptureRequest.SENSOR_FRAME_DURATION, max(exp, 33_333_333L))
            }
            val f = proFocus
            if (f != null && lens.minFocusDistance > 0f) {
                b.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
                b.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, f)
            }
            if (proWbIndex > 0) b.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, WB_MODES[proWbIndex].first)
        }
        if (prefs.antibanding50) {
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_50HZ)
        }
        try {
            Camera2CameraControl.from(cam.cameraControl).setCaptureRequestOptions(b.build())
        } catch (e: Exception) { Log.w(TAG, "opciones", e) }
    }

    // =====================================================================================
    // Lentes y zoom (sirve para foto y video)
    // =====================================================================================

    private fun currentZoom(): Float =
        if (isVideoMode()) engineZoom else camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f

    private fun setZoom(z: Float) {
        if (isVideoMode()) {
            val e = engine ?: return
            val prev = engineZoom
            engineZoom = e.setZoom(z)
            if (abs(prev - engineZoom) > 0.01f) showInfo(LensFinder.fmtZoom(engineZoom), 900)
            if (abs(prev - engineZoom) > 0.05f) buildZoomRow()
        } else {
            val zs = camera?.cameraInfo?.zoomState?.value ?: return
            camera?.cameraControl?.setZoomRatio(z.coerceIn(zs.minZoomRatio, zs.maxZoomRatio))
        }
    }

    private fun buildZoomRow() {
        buildZoomRowInner()
        zoomRow.visibility = if (zoomRow.childCount == 0) View.INVISIBLE else View.VISIBLE
    }

    private fun buildZoomRowInner() {
        zoomRow.removeAllViews()
        if (mode == Mode.VOICE) return
        if (useFront) {
            val fronts = frontLenses()
            if (fronts.size > 1) fronts.forEach { l -> addZoomChip(l.zoomLabel, l == currentLens) { switchLens(l, null) } }
            return
        }
        val backs = backLenses()
        val mainLens = backs.firstOrNull { it.isMain }
        val cur = currentLens
        val zoom = currentZoom()

        if (cur == mainLens && logicalMinZoom < 0.95f && backs.none { it.relZoom < 0.9f }) {
            addZoomChip(LensFinder.fmtZoom(logicalMinZoom), zoom < 0.98f) { setZoom(logicalMinZoom) }
        }
        for (l in backs) {
            val selected = l == cur && (l != mainLens || zoom in 0.98f..1.5f)
            addZoomChip(l.zoomLabel, selected) {
                if (l == cur) setZoom(1f) else switchLens(l, null)
            }
        }
        if (mainLens != null) {
            for (z in listOf(2f, 3f)) {
                if (backs.any { abs(it.relZoom - z) < 0.3f }) continue
                val selected = cur == mainLens && abs(zoom - z) < 0.25f
                addZoomChip(LensFinder.fmtZoom(z), selected) {
                    if (cur == mainLens) setZoom(z) else switchLens(mainLens, z)
                }
            }
        }
    }

    private fun addZoomChip(label: String, selected: Boolean, onClick: () -> Unit) {
        val size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40f, resources.displayMetrics).toInt()
        val margin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6f, resources.displayMetrics).toInt()
        val tv = TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = if (selected) 13.5f else 12f
            setTextColor(if (selected) Ui.ACCENT else Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            if (selected) setBackgroundResource(R.drawable.bg_round)
            setOnClickListener { onClick() }
            rotation = uiRotation
        }
        val lp = LinearLayout.LayoutParams(size, size).apply { setMargins(margin, 0, margin, 0) }
        zoomRow.addView(tv, lp)
    }

    private fun onZoomChanged(z: ZoomState) {
        val prev = zoomRow.tag as? Float
        zoomRow.tag = z.zoomRatio
        if (prev == null || abs(prev - z.zoomRatio) > 0.05f) buildZoomRow()
        if (prev != null && abs(prev - z.zoomRatio) > 0.01f) showInfo(LensFinder.fmtZoom(z.zoomRatio), 900)
    }

    private fun switchLens(lens: Lens, zoom: Float?) {
        if (isRecording()) return
        currentLens = lens
        pendingZoom = zoom
        resetProManual()
        if (isVideoMode()) restartEngine() else bindCamera()
    }

    private fun flipCamera() {
        if (isRecording() || mode == Mode.VOICE) return
        useFront = !useFront
        if (useFront && frontLenses().isEmpty()) { useFront = false; showInfo("No hay cámara frontal disponible"); return }
        selectDefaultLens()
        resetProManual()
        flipButton.animate().rotationBy(180f).setDuration(250).start()
        if (isVideoMode()) restartEngine() else bindCamera()
        refreshTopChips()
    }

    // =====================================================================================
    // Modos
    // =====================================================================================

    private fun setMode(m: Mode) {
        if (isRecording() || micTesting || m == mode) return
        mode = m
        prefs.lastMode = m.ordinal
        cancelCountdown()
        camera?.cameraControl?.cancelFocusAndMetering()
        focusRing.hide()
        if (isVideoMode(m) && flashMode != ImageCapture.FLASH_MODE_OFF) flashMode = ImageCapture.FLASH_MODE_OFF
        if (m == Mode.VOICE) torchOn = false
        refreshModeUi()
        applyStage()
        when (m) {
            Mode.SHORTS -> showInfo("SHORTS: vertical 9:16 · las zonas grises las tapa YouTube", 3500)
            Mode.YOUTUBE -> showInfo("YOUTUBE: girá el celu de costado · 16:9", 3500)
            Mode.VOICE -> showInfo("VOZ: grabá la narración de tus videos", 3000)
            else -> {}
        }
    }

    private fun shiftMode(dir: Int) {
        val all = Mode.values()
        val i = (all.indexOf(mode) + dir).coerceIn(0, all.size - 1)
        setMode(all[i])
    }

    private fun refreshModeUi() {
        for ((m, v) in modeViews) {
            val sel = m == mode
            v.setTextColor(ContextCompat.getColor(this, if (sel) R.color.accent else R.color.dim))
            v.background = if (sel) ContextCompat.getDrawable(this, R.drawable.bg_mode_sel) else null
        }
        shutterButton.videoMode = isVideoMode() || mode == Mode.VOICE
        proPanel.visibility = if (mode == Mode.PRO) View.VISIBLE else View.GONE
        evRow.visibility = View.GONE
        histogram.visibility = View.GONE
        gridOverlay.showGrid = prefs.grid && mode != Mode.VOICE && mode != Mode.SHORTS
        levelOverlay.showLevel = prefs.level && mode != Mode.VOICE
        guideOverlay.kind = when (mode) {
            Mode.SHORTS -> GuideOverlay.Kind.SHORTS
            Mode.YOUTUBE -> GuideOverlay.Kind.YOUTUBE
            else -> GuideOverlay.Kind.NONE
        }
        guideOverlay.showZones = prefs.guides
        flipButton.visibility = if (mode == Mode.VOICE) View.INVISIBLE else View.VISIBLE
        main.removeCallbacks(proTicker)
        if (mode == Mode.PRO) { selectPro(selectedPro); main.post(proTicker) }
        rotateUi(surfaceRotation)
    }

    private fun applyPrompterVisibility() {
        val show = prefs.prompterOn && prefs.prompterText.isNotBlank() && (isVideoMode() || mode == Mode.VOICE)
        prompter.visibility = if (show) View.VISIBLE else View.GONE
    }

    /** Barra de arriba: ícono de flash y resumen de cómo vas a grabar. */
    private fun refreshTopChips() {
        val voiceMode = mode == Mode.VOICE
        btnFlash.visibility = if (useFront || voiceMode) View.INVISIBLE else View.VISIBLE
        btnFlash.setImageResource(
            when {
                torchOn -> R.drawable.ic_flashlight
                isVideoMode() -> R.drawable.ic_flash_off
                flashMode == ImageCapture.FLASH_MODE_AUTO -> R.drawable.ic_flash_auto
                flashMode == ImageCapture.FLASH_MODE_ON -> R.drawable.ic_flash_on
                else -> R.drawable.ic_flash_off
            }
        )
        val flashActive = torchOn || (!isVideoMode() && flashMode != ImageCapture.FLASH_MODE_OFF)
        btnFlash.imageTintList = ColorStateList.valueOf(if (flashActive) Ui.ACCENT else Color.WHITE)
        val timer = if (timerSec > 0) " · ${timerSec}s" else ""
        statusText.text = when {
            isPhotoMode() -> "$mpLabel · ${if (prefs.ratio169) "16:9" else "4:3"}" +
                (if (boundFormat != ImageCapture.OUTPUT_FORMAT_JPEG) " · ${formatLabel(boundFormat)}" else "") + timer
            isVideoMode() -> videoLabel() + (if (lookLocked) " · fijo" else "") + timer
            else -> micShort() + timer
        }
        if (quickPanel.visibility == View.VISIBLE) buildQuickPanel()
    }

    private fun videoLabel(): String {
        val cfg = videoCfg
        if (cfg != null) return "${cfg.label.substringBefore("·")} · ${cfg.fps} fps"
        val res = when (prefs.videoRes) { 0 -> "2K"; 1 -> "1080p"; 3 -> "4K"; else -> "720p" }
        val fps = if (prefs.fps60 && prefs.videoRes == 1 || prefs.fps60 && prefs.videoRes == 2) 60 else 30
        return "$res · $fps fps"
    }

    private fun micShort(): String =
        if (!prefs.audioOn && mode != Mode.VOICE) "Sin audio"
        else AudioSetup.build(this, prefs, force = true)?.label ?: "Micrófono"

    // ---------- Panel de accesos rápidos ----------

    private class Tile(val icon: Int, val label: String, val value: String, val active: Boolean, val onLong: (() -> Unit)? = null, val onClick: () -> Unit)

    private fun prompterActive() = prefs.prompterOn && prefs.prompterText.isNotBlank()

    private fun quickTiles(): List<Tile> {
        val t = mutableListOf<Tile>()
        val timerTile = Tile(R.drawable.ic_timer, "Temporizador", if (timerSec == 0) "No" else "$timerSec s", timerSec > 0) {
            timerSec = when (timerSec) { 0 -> 3; 3 -> 10; else -> 0 }
            refreshTopChips()
        }
        val prompterTile = Tile(R.drawable.ic_script, "Guion",
            if (prefs.prompterText.isBlank()) "Escribir" else if (prefs.prompterOn) "Sí" else "No",
            prompterActive(), onLong = { editPrompter() }) { togglePrompterQuick() }
        when {
            isPhotoMode() -> {
                t += Tile(R.drawable.ic_ratio, "Proporción", if (prefs.ratio169) "16:9" else "4:3", false) {
                    prefs.ratio169 = !prefs.ratio169
                    applyRatio(); bindCamera(); refreshTopChips()
                }
                t += Tile(R.drawable.ic_resolution, "Resolución", if (prefs.maxRes) "108MP" else "12MP", prefs.maxRes) {
                    prefs.maxRes = !prefs.maxRes
                    showInfo(if (prefs.maxRes) "108MP: más detalle con buena luz" else "12MP: mejor con poca luz y menos ruido")
                    bindCamera()
                }
                t += timerTile
                if (activeExtension == null && supportedFormats.size > 1) {
                    t += Tile(R.drawable.ic_format, "Formato", formatLabel(boundFormat), boundFormat != ImageCapture.OUTPUT_FORMAT_JPEG) { cycleFormat() }
                }
                val lens = currentLens
                if (mode == Mode.PHOTO && lens != null && isExtAvailable(lens, ExtensionMode.HDR)) {
                    t += Tile(R.drawable.ic_hdr, "HDR", if (hdrOn) "Sí" else "No", hdrOn) { hdrOn = !hdrOn; bindCamera(); refreshTopChips() }
                }
                t += Tile(R.drawable.ic_grid, "Cuadrícula", if (prefs.grid) "Sí" else "No", prefs.grid) {
                    prefs.grid = !prefs.grid; refreshModeUi(); refreshTopChips()
                }
            }
            isVideoMode() -> {
                t += Tile(R.drawable.ic_quality, "Calidad", videoLabel(), false) { cycleVideoQuality() }
                t += Tile(R.drawable.ic_mic, "Micrófono", micShort().substringBefore(" ·"), prefs.audioOn) {
                    Ui.showMicSheet(this, prefs) { afterVideoChange() }
                }
                t += timerTile
                t += prompterTile
                t += Tile(if (lookLocked) R.drawable.ic_lock else R.drawable.ic_unlock, "Look", if (lookLocked) "Fijo" else "Auto", lookLocked) { toggleLook() }
                t += Tile(R.drawable.ic_guides, "Guías", if (prefs.guides) "Sí" else "No", prefs.guides) {
                    prefs.guides = !prefs.guides
                    guideOverlay.showZones = prefs.guides
                    refreshTopChips()
                }
            }
            else -> {
                t += Tile(R.drawable.ic_mic, "Micrófono", micShort().substringBefore(" ·"), true) {
                    Ui.showMicSheet(this, prefs) { refreshTopChips() }
                }
                t += timerTile
                t += prompterTile
            }
        }
        return t
    }

    private fun buildQuickPanel() {
        quickPanel.removeAllViews()
        val perRow = 3
        val m = Ui.dp(this, 4f)
        quickTiles().chunked(perRow).forEach { rowTiles ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (i in 0 until perRow) {
                val tile = rowTiles.getOrNull(i)
                val v: View = if (tile == null) View(this) else tileView(tile)
                row.addView(v, LinearLayout.LayoutParams(0, Ui.dp(this, 90f), 1f).apply { setMargins(m, m, m, m) })
            }
            quickPanel.addView(row)
        }
        val more = Ui.pillButton(this, "Todos los ajustes", false, R.drawable.ic_settings) { openSettings() }
        quickPanel.addView(more, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(m, Ui.dp(this@MainActivity, 8f), m, m)
        })
    }

    private fun tileView(t: Tile): View {
        val r = Ui.dp(this, 18f).toFloat()
        val fg = if (t.active) Ui.ACCENT else Color.WHITE
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = Ui.ripple(Ui.rounded(if (t.active) Ui.ACCENT_SOFT else Ui.CARD2, r), r)
            addView(Ui.icon(context, t.icon, fg, 22f))
            addView(Ui.text(context, t.value, 13.5f, if (t.active) Ui.ACCENT else Ui.TEXT, true).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                setPadding(Ui.dp(context, 6f), Ui.dp(context, 6f), Ui.dp(context, 6f), 0)
            })
            addView(Ui.text(context, t.label, 11f, Ui.TEXT2).apply { maxLines = 1; gravity = Gravity.CENTER })
            setOnClickListener { t.onClick() }
            val long = t.onLong
            if (long != null) setOnLongClickListener { long(); true }
        }
    }

    private fun toggleQuickPanel() {
        if (quickPanel.visibility == View.VISIBLE) hideQuickPanel() else showQuickPanel()
    }

    private fun showQuickPanel() {
        if (isRecording()) return
        buildQuickPanel()
        quickPanel.animate().cancel()
        quickPanel.alpha = 0f
        quickPanel.translationY = -Ui.dp(this, 12f).toFloat()
        quickPanel.visibility = View.VISIBLE
        quickPanel.animate().alpha(1f).translationY(0f).setDuration(180).start()
        statusChevron.animate().rotation(180f).setDuration(180).start()
    }

    private fun hideQuickPanel() {
        if (quickPanel.visibility != View.VISIBLE) return
        statusChevron.animate().rotation(0f).setDuration(150).start()
        quickPanel.animate().cancel()
        quickPanel.animate().alpha(0f).translationY(-Ui.dp(this, 12f).toFloat()).setDuration(150)
            .withEndAction { quickPanel.visibility = View.GONE }.start()
    }

    private fun cycleVideoQuality() {
        if (isRecording()) return
        val lens = currentLens
        val sizes = lens?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(MediaRecorder::class.java)?.toList() ?: emptyList()
        val has60 = lens?.aeFpsRanges?.any { it.upper >= 60 } == true
        val combos = mutableListOf<Pair<Int, Boolean>>()
        if (sizes.any { it.width >= 3840 }) combos += 3 to false
        if (sizes.any { it.width == 2560 }) combos += 0 to false
        combos += 1 to false
        if (has60) combos += 1 to true
        combos += 2 to false
        val fps60Now = prefs.fps60 && (prefs.videoRes == 1 || prefs.videoRes == 2)
        val cur = combos.indexOfFirst { it.first == prefs.videoRes && it.second == fps60Now }
        val next = combos[(cur + 1) % combos.size]
        prefs.videoRes = next.first
        prefs.fps60 = next.second
        afterVideoChange()
    }

    private fun togglePrompterQuick() {
        if (prefs.prompterText.isBlank()) { editPrompter(); return }
        prefs.prompterOn = !prefs.prompterOn
        applyPrompterVisibility()
        refreshTopChips()
    }

    private fun editPrompter() {
        if (isRecording()) return
        hideQuickPanel()
        Ui.showPrompterSheet(this, prefs) {
            prompter.text = prefs.prompterText
            prompter.speedLevel = prefs.prompterSpeed
            prompter.textSizeSp = prefs.prompterSize.toFloat()
            applyPrompterVisibility()
            refreshTopChips()
            if (prefs.prompterOn) showInfo("El guion arranca solo al grabar. Tocalo para pausar.", 4000)
        }
    }

    private fun toggleLook() {
        lookLocked = !lookLocked
        engine?.setLookLocked(lookLocked)
        showInfo(if (lookLocked) "Look fijo: luz, color y enfoque no cambian mientras grabás" else "Look automático")
        refreshTopChips()
    }

    private fun formatLabel(f: Int) = when (f) {
        ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR -> "ULTRA HDR"
        ImageCapture.OUTPUT_FORMAT_RAW_JPEG -> "RAW+JPG"
        ImageCapture.OUTPUT_FORMAT_RAW -> "RAW"
        else -> "JPG"
    }

    private fun cycleFormat() {
        if (supportedFormats.size < 2) return
        val i = supportedFormats.indexOf(boundFormat)
        val next = supportedFormats[(i + 1) % supportedFormats.size]
        prefs.photoFormat = next
        when (next) {
            ImageCapture.OUTPUT_FORMAT_RAW_JPEG -> showInfo("RAW+JPG: guarda un .DNG para editar y un JPG")
            ImageCapture.OUTPUT_FORMAT_RAW -> showInfo("RAW: solo .DNG sin procesar")
            ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR -> showInfo("Ultra HDR: más brillo en pantallas HDR")
        }
        bindCamera()
    }

    private fun cycleFlash() {
        if (useFront) return
        if (isVideoMode()) {
            torchOn = !torchOn
            engine?.setTorch(torchOn)
            refreshTopChips()
            return
        }
        when {
            torchOn -> { torchOn = false; flashMode = ImageCapture.FLASH_MODE_OFF }
            flashMode == ImageCapture.FLASH_MODE_OFF -> flashMode = ImageCapture.FLASH_MODE_AUTO
            flashMode == ImageCapture.FLASH_MODE_AUTO -> flashMode = ImageCapture.FLASH_MODE_ON
            else -> { flashMode = ImageCapture.FLASH_MODE_OFF; torchOn = true }
        }
        imageCapture?.flashMode = if (torchOn) ImageCapture.FLASH_MODE_OFF else flashMode
        camera?.cameraControl?.enableTorch(torchOn)
        refreshTopChips()
    }

    // =====================================================================================
    // Enfoque y exposición
    // =====================================================================================

    private fun focusAt(x: Float, y: Float, lock: Boolean) {
        if (mode == Mode.VOICE) return
        if (isVideoMode()) {
            val e = engine ?: return
            val w = videoSurface.width.toFloat().coerceAtLeast(1f)
            val h = videoSurface.height.toFloat().coerceAtLeast(1f)
            e.focusAt(x / w, y / h)
            focusRing.show(x, y, lock)
            if (lock && !lookLocked) {
                lookLocked = true
                e.setLookLocked(true)
                previewView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                showInfo("Look bloqueado")
                refreshTopChips()
            }
            return
        }
        val cam = camera ?: return
        val point = previewView.meteringPointFactory.createPoint(x, y)
        val flags = if (mode == Mode.PRO && proFocus != null) FocusMeteringAction.FLAG_AE
        else FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB
        val b = FocusMeteringAction.Builder(point, flags)
        if (lock) b.disableAutoCancel() else b.setAutoCancelDuration(4, TimeUnit.SECONDS)
        try { cam.cameraControl.startFocusAndMetering(b.build()) } catch (_: Exception) { }
        focusRing.show(x, y, lock)
        if (lock) {
            previewView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            showInfo("Enfoque y exposición bloqueados")
        }
        if (mode != Mode.PRO && cam.cameraInfo.exposureState.isExposureCompensationSupported) {
            evRow.visibility = View.VISIBLE
            main.removeCallbacks(hideEv)
            main.postDelayed(hideEv, 5000)
        }
    }

    private fun updateEvLabel(idx: Int) {
        val step = camera?.cameraInfo?.exposureState?.exposureCompensationStep?.toFloat() ?: 0f
        evLabel.text = String.format(Locale.US, "EV %+.1f", idx * step)
    }

    // =====================================================================================
    // Modo PRO
    // =====================================================================================

    private fun selectPro(p: ProParam) {
        selectedPro = p
        val chips = mapOf(ProParam.ISO to chipIso, ProParam.SHUTTER to chipShutter, ProParam.FOCUS to chipFocus, ProParam.WB to chipWb, ProParam.EV to chipEv)
        for ((k, v) in chips) v.setTextColor(ContextCompat.getColor(this, if (k == p) R.color.accent else R.color.white))
        configureProSlider()
    }

    private fun proCountAndPos(): Pair<Int, Int> {
        val lens = currentLens
        val focusOk = lens != null && lens.minFocusDistance > 0f
        return when (selectedPro) {
            ProParam.ISO -> isoValues.size to nearestIndex(isoValues.map { it.toLong() }, (proIso ?: liveIso).toLong())
            ProParam.SHUTTER -> shutterValues.size to nearestIndex(shutterValues, proShutterNs ?: liveExposure)
            ProParam.FOCUS -> if (lens != null && focusOk) 101 to (((proFocus ?: liveFocus) / lens.minFocusDistance) * 100).roundToInt() else 0 to 0
            ProParam.WB -> WB_MODES.size to proWbIndex
            ProParam.EV -> {
                val r = camera?.cameraInfo?.exposureState?.exposureCompensationRange
                if (r == null || r.lower == r.upper) 0 to 0 else (r.upper - r.lower + 1) to (proEvIndex - r.lower)
            }
        }
    }

    private fun nearestIndex(values: List<Long>, v: Long): Int {
        if (values.isEmpty()) return 0
        var best = 0
        var diff = Long.MAX_VALUE
        values.forEachIndexed { i, x -> val d = abs(x - v); if (d < diff) { diff = d; best = i } }
        return best
    }

    private fun configureProSlider() {
        val (count, pos) = proCountAndPos()
        val manualBlocked = (selectedPro == ProParam.ISO || selectedPro == ProParam.SHUTTER) && currentLens?.manualSensor != true
        if (count < 2 || manualBlocked || activeExtension != null) {
            proSlider.isEnabled = false
            proSlider.valueFrom = 0f; proSlider.valueTo = 1f; proSlider.value = 0f
            proValue.text = "—"
        } else {
            proSlider.isEnabled = true
            proSlider.valueFrom = 0f
            proSlider.valueTo = (count - 1).toFloat()
            proSlider.stepSize = 1f
            proSlider.value = pos.coerceIn(0, count - 1).toFloat()
            proValue.text = proValueText()
        }
        btnProAuto.visibility = if (selectedPro == ProParam.WB) View.GONE else View.VISIBLE
        updateProChips()
    }

    private fun onProSlider(i: Int) {
        val lens = currentLens ?: return
        when (selectedPro) {
            ProParam.ISO -> {
                val v = isoValues.getOrNull(i)
                if (v != null) {
                    proIso = v
                    if (proShutterNs == null && liveExposure > 0) proShutterNs = nearestShutter(liveExposure)
                }
            }
            ProParam.SHUTTER -> {
                val v = shutterValues.getOrNull(i)
                if (v != null) {
                    proShutterNs = v
                    if (proIso == null && liveIso > 0) proIso = isoValues.getOrNull(nearestIndex(isoValues.map { it.toLong() }, liveIso.toLong()))
                }
            }
            ProParam.FOCUS -> proFocus = lens.minFocusDistance * i / 100f
            ProParam.WB -> proWbIndex = i.coerceIn(0, WB_MODES.size - 1)
            ProParam.EV -> {
                val r = camera?.cameraInfo?.exposureState?.exposureCompensationRange ?: return
                proEvIndex = r.lower + i
                camera?.cameraControl?.setExposureCompensationIndex(proEvIndex)
            }
        }
        proValue.text = proValueText()
        applyCaptureOptions()
        updateProChips()
    }

    private fun nearestShutter(ns: Long): Long? = shutterValues.getOrNull(nearestIndex(shutterValues, ns))

    private fun setProAuto() {
        when (selectedPro) {
            ProParam.ISO, ProParam.SHUTTER -> { proIso = null; proShutterNs = null }
            ProParam.FOCUS -> proFocus = null
            ProParam.WB -> proWbIndex = 0
            ProParam.EV -> { proEvIndex = 0; camera?.cameraControl?.setExposureCompensationIndex(0) }
        }
        applyCaptureOptions()
        configureProSlider()
    }

    private fun resetProManual() {
        proIso = null; proShutterNs = null; proFocus = null; proWbIndex = 0; proEvIndex = 0
    }

    private fun shutterText(ns: Long): String =
        if (ns <= 0) "—" else if (ns >= 500_000_000L) String.format(Locale.US, "%.1f\"", ns / 1e9).replace(".0\"", "\"")
        else "1/${(1e9 / ns).roundToInt()}"

    private fun focusText(d: Float): String =
        if (d <= 0.01f) "∞" else if (1f / d >= 1f) String.format(Locale.US, "%.1fm", 1f / d) else "${(100f / d).roundToInt()}cm"

    private fun proValueText(): String = when (selectedPro) {
        ProParam.ISO -> proIso?.toString() ?: "A"
        ProParam.SHUTTER -> proShutterNs?.let { shutterText(it) } ?: "A"
        ProParam.FOCUS -> proFocus?.let { focusText(it) } ?: "AF"
        ProParam.WB -> WB_MODES[proWbIndex].second
        ProParam.EV -> {
            val step = camera?.cameraInfo?.exposureState?.exposureCompensationStep?.toFloat() ?: 0f
            String.format(Locale.US, "%+.1f", proEvIndex * step)
        }
    }

    private fun updateProChips() {
        if (mode != Mode.PRO) return
        chipIso.text = "ISO\n" + (proIso?.toString() ?: "A ${if (liveIso > 0) liveIso.toString() else "—"}")
        chipShutter.text = "VEL.\n" + (proShutterNs?.let { shutterText(it) } ?: "A ${shutterText(liveExposure)}")
        chipFocus.text = "ENFOQUE\n" + (proFocus?.let { focusText(it) } ?: "AF")
        chipWb.text = "BLANCOS\n" + WB_MODES[proWbIndex].second
        val step = camera?.cameraInfo?.exposureState?.exposureCompensationStep?.toFloat() ?: 0f
        chipEv.text = "EV\n" + String.format(Locale.US, "%+.1f", proEvIndex * step)
        if (proSlider.isEnabled) proValue.text = proValueText()
    }

    // =====================================================================================
    // Disparador (foto / video / voz) + cuenta regresiva
    // =====================================================================================

    private fun onShutter() {
        if (micTesting) return
        if (countdownRunnable != null) { cancelCountdown(); return }
        val action: () -> Unit = when {
            isVideoMode() -> { { toggleVideo() } }
            mode == Mode.VOICE -> { { toggleVoice() } }
            else -> { { takePhoto() } }
        }
        if (isRecording()) { action(); return }  // para frenar no hay cuenta regresiva
        if (isPhotoMode() && busy) return
        if (timerSec > 0) startCountdown(timerSec, action) else action()
    }

    private fun startCountdown(sec: Int, action: () -> Unit) {
        var left = sec
        countdownText.visibility = View.VISIBLE
        val r = object : Runnable {
            override fun run() {
                if (left <= 0) {
                    countdownText.visibility = View.GONE
                    countdownRunnable = null
                    action()
                    return
                }
                countdownText.text = left.toString()
                if (prefs.sound) sound.play(MediaActionSound.FOCUS_COMPLETE)
                left--
                main.postDelayed(this, 1000)
            }
        }
        countdownRunnable = r
        main.post(r)
    }

    private fun cancelCountdown() {
        countdownRunnable?.let { main.removeCallbacks(it) }
        countdownRunnable = null
        countdownText.visibility = View.GONE
    }

    private fun photoOptions(stamp: String, raw: Boolean): ImageCapture.OutputFileOptions {
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, if (raw) "ENF_$stamp.dng" else "ENF_$stamp.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, if (raw) "image/x-adobe-dng" else "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/" + ALBUM)
        }
        val meta = ImageCapture.Metadata().apply { isReversedHorizontal = useFront && prefs.mirrorFront }
        return ImageCapture.OutputFileOptions.Builder(contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
            .setMetadata(meta)
            .build()
    }

    private fun takePhoto() {
        val ic = imageCapture ?: return
        ic.targetRotation = surfaceRotation
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val slow = (proShutterNs ?: 0L) >= 250_000_000L
        setBusy(true, if (slow) "Mantené el celu quieto…" else null)
        shutterFeedback()

        val cb = object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val uri = outputFileResults.savedUri
                runOnUiThread {
                    setBusy(false, null)
                    if (uri != null) {
                        val type = try { contentResolver.getType(uri) } catch (e: Exception) { null }
                        if (type == null || !type.contains("dng")) setLast(uri, false)
                        else if (boundFormat == ImageCapture.OUTPUT_FORMAT_RAW) { setLast(uri, false); showInfo("RAW guardado en DCIM/$ALBUM") }
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "foto", exception)
                runOnUiThread {
                    setBusy(false, null)
                    showInfo("No se pudo guardar: ${exception.message}", 5000)
                }
            }
        }
        try {
            when (boundFormat) {
                ImageCapture.OUTPUT_FORMAT_RAW_JPEG -> ic.takePicture(photoOptions(stamp, true), photoOptions(stamp, false), cameraExecutor, cb)
                ImageCapture.OUTPUT_FORMAT_RAW -> ic.takePicture(photoOptions(stamp, true), cameraExecutor, cb)
                else -> ic.takePicture(photoOptions(stamp, false), cameraExecutor, cb)
            }
        } catch (e: Exception) {
            setBusy(false, null)
            showInfo("Error al disparar: ${e.message}", 5000)
        }
    }

    private fun setBusy(b: Boolean, msg: String?) {
        busy = b
        shutterButton.busy = b
        if (b && msg != null) showInfo(msg, 60_000) else if (!b && infoText.text?.startsWith("Mantené") == true) infoText.visibility = View.GONE
    }

    private fun shutterFeedback() {
        if (prefs.sound) sound.play(MediaActionSound.SHUTTER_CLICK)
        shutterButton.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        flashOverlay.animate().cancel()
        flashOverlay.alpha = 0.85f
        flashOverlay.animate().alpha(0f).setDuration(220).start()
    }

    private fun togglePause() {
        if (!isRecording()) return
        val now = SystemClock.elapsedRealtime()
        if (recPaused) {
            engine?.resume(); voice?.resume()
            pausedTotalMs += now - pausedAtMs
            recPaused = false
            if (prefs.prompterOn) prompter.start()
        } else {
            engine?.pause(); voice?.pause()
            pausedAtMs = now
            recPaused = true
            prompter.pause()
        }
        pauseButton.setImageResource(if (recPaused) R.drawable.ic_play else R.drawable.ic_pause)
    }

    private fun setRecordingUi(on: Boolean) {
        shutterButton.recording = on
        modeRow.visibility = if (on) View.INVISIBLE else View.VISIBLE
        thumbnail.visibility = if (on) View.GONE else View.VISIBLE
        pauseButton.visibility = if (on) View.VISIBLE else View.GONE
        pauseButton.setImageResource(R.drawable.ic_pause)
        if (on) hideQuickPanel()
        flipButton.isEnabled = !on
        flipButton.alpha = if (on) 0.3f else 1f
        recBadge.visibility = if (on) View.VISIBLE else View.GONE
        audioMeter.visibility = if (on && (mode == Mode.VOICE || videoCfg?.audio != null)) View.VISIBLE else View.GONE
        for (i in 0 until (findViewById<LinearLayout>(R.id.topBar)).childCount) {
            findViewById<LinearLayout>(R.id.topBar).getChildAt(i).isEnabled = !on
        }
        if (!on) infoText.visibility = View.GONE
    }

    // =====================================================================================
    // Histograma
    // =====================================================================================

    private fun analyzeHistogram(img: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (now - lastHistogramMs < 150) return
            lastHistogramMs = now
            val plane = img.planes[0]
            val buf = plane.buffer
            val rowStride = plane.rowStride
            val pixStride = plane.pixelStride
            val w = img.width
            val h = img.height
            val bins = IntArray(64)
            var y = 0
            while (y < h) {
                val row = y * rowStride
                var x = 0
                while (x < w) {
                    val idx = row + x * pixStride
                    if (idx < buf.limit()) bins[(buf.get(idx).toInt() and 0xFF) shr 2]++
                    x += 4
                }
                y += 4
            }
            histogram.post { histogram.setData(bins) }
        } catch (_: Exception) {
        } finally {
            img.close()
        }
    }

    // =====================================================================================
    // Galería
    // =====================================================================================

    private fun setLast(uri: Uri, video: Boolean) {
        lastUri = uri
        lastIsVideo = video
        cameraExecutor.execute {
            val bmp = try { contentResolver.loadThumbnail(uri, Size(240, 240), null) } catch (e: Exception) { null }
            runOnUiThread { if (bmp != null) thumbnail.setImageBitmap(bmp) }
        }
    }

    private fun loadLastThumbnail() {
        cameraExecutor.execute {
            try {
                val sel = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.MediaColumns.MIME_TYPE} = ?"
                contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(BaseColumns._ID), sel,
                    arrayOf("%$ALBUM%", "image/jpeg"), "${MediaStore.MediaColumns.DATE_ADDED} DESC"
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0))
                        runOnUiThread { if (lastUri == null) setLast(uri, false) }
                    }
                }
            } catch (_: Exception) { }
        }
    }

    private fun openLast() {
        val uri = lastUri ?: run { showInfo("Todavía no grabaste nada"); return }
        val type = try { contentResolver.getType(uri) } catch (e: Exception) { null } ?: if (lastIsVideo) "video/*" else "image/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, type)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try { startActivity(intent) } catch (e: Exception) { showInfo("No hay app para abrirlo") }
    }

    // =====================================================================================
    // Ajustes: video, micrófono, guion
    // =====================================================================================

    private fun openSettings() {
        if (isRecording()) return
        hideQuickPanel()
        settingsSnapshot = prefs.snapshot()
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    /** Al volver de Ajustes aplicamos lo que haya cambiado. */
    private fun applySettingsChanges() {
        prompter.text = prefs.prompterText
        prompter.speedLevel = prefs.prompterSpeed
        prompter.textSizeSp = prefs.prompterSize.toFloat()
        guideOverlay.showZones = prefs.guides
        refreshModeUi()
        if (isVideoMode()) {
            if (engine != null) restartEngine()
        } else {
            applyStage()
        }
        applyPrompterVisibility()
        refreshTopChips()
    }

    // =====================================================================================

    private fun showInfo(msg: String, ms: Long = 2500) {
        infoText.text = msg
        infoText.visibility = View.VISIBLE
        main.removeCallbacks(hideInfo)
        main.postDelayed(hideInfo, ms)
    }
}
