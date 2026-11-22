package com.enfoque.camara

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.MediaCodec
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

data class VideoConfig(
    val size: Size,
    val previewSize: Size,
    val fps: Int,
    val bitrate: Int,
    val hevc: Boolean,
    val stabilization: Boolean,
    val antibanding50: Boolean,
    val audio: AudioConfig?
) {
    val label: String
        get() {
            val res = when {
                size.width >= 3840 -> "4K"
                size.width >= 2560 -> "2K"
                size.width >= 1920 -> "1080p"
                else -> "${size.height}p"
            }
            return "$res·$fps"
        }
}

/**
 * Motor de video propio (Camera2 + MediaRecorder). Existe porque CameraX no ofrece 2K (2560x1440),
 * que es justo la máxima del Zero 40 4G, y porque así podemos elegir el micrófono.
 */
class VideoEngine(private val context: Context, private val listener: Listener) {

    interface Listener {
        fun onCameraReady(minZoom: Float, maxZoom: Float)
        fun onCameraError(msg: String)
        fun onNotice(msg: String)
        fun onRecordingStarted()
        fun onRecordingFinished(uri: Uri?, error: String?)
    }

    companion object { private const val TAG = "EnfoqueVideo" }

    private val cm = context.getSystemService(CameraManager::class.java)
    private val thread = HandlerThread("enfoque-video").apply { start() }
    private val handler = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    private val executor = Executor { handler.post(it) }

    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var recSurface: Surface? = null
    private var recorder: MediaRecorder? = null
    private var pfd: ParcelFileDescriptor? = null
    private var outUri: Uri? = null
    private var comm = false

    private lateinit var lens: Lens
    private lateinit var preview: Surface
    lateinit var cfg: VideoConfig
        private set

    @Volatile var isRecording = false; private set
    @Volatile var isPaused = false; private set
    @Volatile private var closed = false
    @Volatile private var lastFocusDist = 0f

    private var zoom = 1f
    private var lookLocked = false
    private var torch = false
    private var afRegion: MeteringRectangle? = null
    private var openAttempts = 0

    fun open(lens: Lens, preview: Surface, cfg: VideoConfig) {
        this.lens = lens
        this.preview = preview
        this.cfg = cfg
        handler.post { openCamera() }
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        if (closed) return
        try {
            cm.openCamera(lens.cameraId, executor, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    if (closed) { camera.close(); return }
                    device = camera
                    createSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close(); device = null
                    if (!closed) retryOpen("cámara desconectada")
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close(); device = null
                    if (!closed) retryOpen("error $error")
                }
            })
        } catch (e: Exception) {
            retryOpen(e.message ?: "?")
        }
    }

    /** La cámara puede seguir ocupada un instante al venir del modo foto: reintentamos. */
    private fun retryOpen(why: String) {
        if (openAttempts++ < 8) handler.postDelayed({ openCamera() }, 350)
        else postError("No se pudo abrir la cámara ($why)")
    }

    private fun postError(msg: String) = main.post { if (!closed) listener.onCameraError(msg) }

    private fun configureVideo(r: MediaRecorder, c: VideoConfig, channels: Int) {
        val audio = c.audio
        if (audio != null) r.setAudioSource(audio.source)
        r.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setVideoEncodingBitRate(c.bitrate)
        r.setVideoFrameRate(c.fps)
        r.setVideoSize(c.size.width, c.size.height)
        r.setVideoEncoder(if (c.hevc) MediaRecorder.VideoEncoder.HEVC else MediaRecorder.VideoEncoder.H264)
        if (audio != null) {
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(audio.sampleRate)
            r.setAudioEncodingBitRate(audio.bitrate)
            r.setAudioChannels(channels)
        }
    }

    /** Prepara una vez el encoder para fijar el tamaño del surface persistente. */
    private fun warmUp(surface: Surface, c: VideoConfig) {
        val r = AudioSetup.newRecorder(context)
        try {
            configureVideo(r, c.copy(audio = null), 1)
            r.setOutputFile(File(context.cacheDir, "calentamiento.mp4").absolutePath)
            r.setInputSurface(surface)
            r.prepare()
        } finally {
            r.release()
        }
    }

    private fun createSession() {
        val d = device ?: return
        try {
            val surface = MediaCodec.createPersistentInputSurface()
            // Si la combinación no la soporta el encoder, bajamos de a poco.
            val candidates = mutableListOf(cfg)
            if (cfg.hevc) candidates += cfg.copy(hevc = false)
            if (cfg.fps > 30) candidates += cfg.copy(hevc = false, fps = 30)
            var chosen: VideoConfig? = null
            for (c in candidates) {
                try { warmUp(surface, c); chosen = c; break } catch (e: Exception) { Log.w(TAG, "warmup $c", e) }
            }
            if (chosen == null) {
                surface.release()
                postError("El encoder no aceptó ${cfg.label}. Probá otra calidad en Ajustes.")
                return
            }
            if (chosen != cfg) {
                val msg = if (chosen.fps != cfg.fps) "60 fps no disponible en esta resolución: grabando a 30" else "HEVC no disponible: usando H.264"
                main.post { listener.onNotice(msg) }
                cfg = chosen
            }
            recSurface = surface

            val outs = listOf(OutputConfiguration(preview), OutputConfiguration(surface))
            lens.physicalId?.let { p -> outs.forEach { it.setPhysicalCameraId(p) } }
            val sc = SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outs, executor,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(s: CameraCaptureSession) {
                        if (closed) { s.close(); return }
                        session = s
                        updateRepeating()
                        val r = zoomRange()
                        main.post { if (!closed) listener.onCameraReady(r.lower, r.upper) }
                    }

                    override fun onConfigureFailed(s: CameraCaptureSession) {
                        postError("La cámara no aceptó esta configuración de video. Probá otra resolución.")
                    }
                })
            d.createCaptureSession(sc)
        } catch (e: Exception) {
            Log.e(TAG, "sesión", e)
            postError("No se pudo preparar el video: ${e.message}")
        }
    }

    fun zoomRange(): Range<Float> {
        if (Build.VERSION.SDK_INT >= 30) {
            val logical = try { cm.getCameraCharacteristics(lens.cameraId) } catch (e: Exception) { null }
            logical?.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.let { return it }
        }
        val maxDigital = lens.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
        return Range(1f, max(1f, maxDigital))
    }

    private fun fpsRange(): Range<Int>? {
        val ranges = lens.aeFpsRanges
        return ranges.firstOrNull { it.lower == cfg.fps && it.upper == cfg.fps }
            ?: ranges.filter { it.upper == cfg.fps }.maxByOrNull { it.lower }
    }

    private val captureCb = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            if (!lookLocked) result.get(CaptureResult.LENS_FOCUS_DISTANCE)?.let { lastFocusDist = it }
        }
    }

    private fun updateRepeating() {
        val s = session ?: return
        val d = device ?: return
        try {
            val b = d.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            b.addTarget(preview)
            if (isRecording) recSurface?.let { b.addTarget(it) }
            b.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            fpsRange()?.let { b.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it) }
            if (cfg.antibanding50) b.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_50HZ)
            if (cfg.stabilization && lens.videoStabModes.contains(CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON)) {
                b.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON)
            }
            val ois = lens.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) ?: IntArray(0)
            if (ois.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON)) {
                b.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON)
            }
            if (Build.VERSION.SDK_INT >= 30) b.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoom)
            if (torch) b.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH)
            b.set(CaptureRequest.CONTROL_AE_LOCK, lookLocked)
            b.set(CaptureRequest.CONTROL_AWB_LOCK, lookLocked)
            val region = afRegion
            if (lookLocked && lens.minFocusDistance > 0f) {
                b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
                b.set(CaptureRequest.LENS_FOCUS_DISTANCE, lastFocusDist)
            } else {
                b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                if (region != null) {
                    b.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(region))
                    b.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(region))
                }
            }
            s.setRepeatingRequest(b.build(), captureCb, handler)
        } catch (e: Exception) {
            Log.w(TAG, "repeating", e)
        }
    }

    /** Devuelve el zoom aplicado. */
    fun setZoom(z: Float): Float {
        val r = zoomRange()
        zoom = z.coerceIn(r.lower, r.upper)
        handler.post { updateRepeating() }
        return zoom
    }

    val currentZoom: Float get() = zoom

    /** nx, ny: punto tocado normalizado (0..1) sobre la vista en vertical. */
    fun focusAt(nx: Float, ny: Float) {
        val active = lens.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return
        val front = lens.facing == CameraCharacteristics.LENS_FACING_FRONT
        val sx = ny
        val sy = if (front) nx else 1f - nx
        val half = (active.width() * 0.07f).toInt()
        val cx = (sx * active.width()).toInt()
        val cy = (sy * active.height()).toInt()
        val l = max(0, cx - half)
        val t = max(0, cy - half)
        val w = min(half * 2, active.width() - l)
        val h = min(half * 2, active.height() - t)
        afRegion = MeteringRectangle(l, t, max(1, w), max(1, h), MeteringRectangle.METERING_WEIGHT_MAX - 1)
        handler.post { updateRepeating() }
    }

    /** Bloquea exposición, blancos y enfoque para que el video no "respire". */
    fun setLookLocked(lock: Boolean) {
        lookLocked = lock
        handler.post { updateRepeating() }
    }

    fun setTorch(on: Boolean) {
        torch = on
        handler.post { updateRepeating() }
    }

    fun startRecording(orientationHint: Int) = handler.post { doStart(orientationHint) }
    fun stopRecording() = handler.post { doStop() }
    fun pause() = handler.post { try { recorder?.pause(); isPaused = true } catch (_: Exception) { } }
    fun resume() = handler.post { try { recorder?.resume(); isPaused = false } catch (_: Exception) { } }

    fun amplitude(): Int = try { recorder?.maxAmplitude ?: 0 } catch (e: Exception) { 0 }

    private fun doStart(hint: Int) {
        if (isRecording || session == null) return
        val surface = recSurface ?: return
        val resolver = context.contentResolver
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "ENF_$stamp.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Enfoque")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = try { resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv) } catch (e: Exception) { null }
        if (uri == null) { finish(null, "No se pudo crear el archivo de video"); return }
        val fd = try { resolver.openFileDescriptor(uri, "rw") } catch (e: Exception) { null }
        if (fd == null) { resolver.delete(uri, null, null); finish(null, "Sin acceso al almacenamiento"); return }

        val audio = cfg.audio
        val channelTries = if (audio != null && audio.channels == 2) listOf(2, 1) else listOf(1)
        var rec: MediaRecorder? = null
        var lastErr: Exception? = null
        for (ch in channelTries) {
            var r: MediaRecorder? = null
            try {
                r = AudioSetup.newRecorder(context)
                configureVideo(r, cfg, ch)
                r.setOutputFile(fd.fileDescriptor)
                r.setOrientationHint(hint)
                r.setInputSurface(surface)
                val dev = audio?.device
                if (dev != null) r.setPreferredDevice(dev)
                r.prepare()
                rec = r
                break
            } catch (e: Exception) {
                lastErr = e
                try { r?.release() } catch (_: Exception) { }
            }
        }
        if (rec == null) {
            try { fd.close() } catch (_: Exception) { }
            resolver.delete(uri, null, null)
            finish(null, "No se pudo iniciar la grabación: ${lastErr?.message}")
            return
        }
        if (audio != null && audio.needsComm) comm = AudioSetup.setComm(context, audio.device)
        try {
            rec.start()
        } catch (e: Exception) {
            rec.release()
            try { fd.close() } catch (_: Exception) { }
            resolver.delete(uri, null, null)
            if (comm) { AudioSetup.clearComm(context); comm = false }
            finish(null, "El micrófono o la cámara están ocupados: ${e.message}")
            return
        }
        recorder = rec
        pfd = fd
        outUri = uri
        isPaused = false
        isRecording = true
        updateRepeating()
        main.post { listener.onRecordingStarted() }
    }

    private fun doStop() {
        val r = recorder ?: return
        isRecording = false
        isPaused = false
        updateRepeating()
        var err: String? = null
        try { r.stop() } catch (e: Exception) { err = "La grabación fue demasiado corta" }
        try { r.release() } catch (_: Exception) { }
        recorder = null
        try { pfd?.close() } catch (_: Exception) { }
        pfd = null
        if (comm) { AudioSetup.clearComm(context); comm = false }
        val uri = outUri
        outUri = null
        if (uri != null) {
            try {
                if (err == null) {
                    context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                } else {
                    context.contentResolver.delete(uri, null, null)
                }
            } catch (_: Exception) { }
        }
        finish(if (err == null) uri else null, err)
    }

    private fun finish(uri: Uri?, err: String?) = main.post { listener.onRecordingFinished(uri, err) }

    /** Cierra todo. Espera un momento para liberar la cámara antes de que la use el modo foto. */
    fun close() {
        if (closed) return
        closed = true
        val latch = CountDownLatch(1)
        handler.post {
            if (isRecording) doStop()
            try { session?.close() } catch (_: Exception) { }
            session = null
            try { device?.close() } catch (_: Exception) { }
            device = null
            try { recSurface?.release() } catch (_: Exception) { }
            recSurface = null
            latch.countDown()
        }
        try { latch.await(1500, TimeUnit.MILLISECONDS) } catch (_: InterruptedException) { }
        thread.quitSafely()
    }
}
