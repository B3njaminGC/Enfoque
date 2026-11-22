package com.enfoque.camara

import android.content.ContentValues
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Configuración de audio lista para usar en un MediaRecorder. */
class AudioConfig(
    val source: Int,
    val device: AudioDeviceInfo?,
    val channels: Int,
    val sampleRate: Int,
    val bitrate: Int,
    val needsComm: Boolean,
    val label: String
)

object AudioSetup {

    class MicOption(val key: String, val label: String, val device: AudioDeviceInfo?)
    class SourceOption(val source: Int, val label: String, val help: String)

    private const val TYPE_BLE_HEADSET = 26 // AudioDeviceInfo.TYPE_BLE_HEADSET (Android 12+)

    val SOURCES = listOf(
        SourceOption(MediaRecorder.AudioSource.CAMCORDER, "Natural", "Como la cámara del celu: equilibrado para video."),
        SourceOption(MediaRecorder.AudioSource.VOICE_RECOGNITION, "Voz clara", "Poco procesado, voz nítida. Ideal para hablar a cámara."),
        SourceOption(MediaRecorder.AudioSource.VOICE_COMMUNICATION, "Antirruido", "Reducción de ruido fuerte (como una llamada). Para lugares ruidosos."),
        SourceOption(MediaRecorder.AudioSource.UNPROCESSED, "Sin procesar", "Audio crudo para mejorarlo después al editar."),
        SourceOption(MediaRecorder.AudioSource.MIC, "Básico", "El micrófono tal cual, sin modo especial.")
    )

    fun sourceLabel(src: Int) = SOURCES.firstOrNull { it.source == src }?.label ?: "?"

    private fun place(addr: String?): String = when (addr?.lowercase(Locale.ROOT)) {
        null, "" -> ""
        "bottom" -> " (abajo)"
        "top" -> " (arriba)"
        "back" -> " (atrás)"
        "front" -> " (adelante)"
        else -> " ($addr)"
    }

    fun isBluetooth(d: AudioDeviceInfo?) =
        d != null && (d.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || d.type == TYPE_BLE_HEADSET)

    fun mics(ctx: Context): List<MicOption> {
        val am = ctx.getSystemService(AudioManager::class.java)
        val list = mutableListOf(MicOption("auto", "Automático (el que elija el celu)", null))
        var builtin = 0
        for (d in am.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
            val raw = d.productName?.toString()?.trim().orEmpty()
            val name = if (raw.isBlank() || raw.equals(Build.MODEL, true)) "" else " · $raw"
            val label = when (d.type) {
                AudioDeviceInfo.TYPE_BUILTIN_MIC -> { builtin++; "Interno $builtin" + place(d.address) }
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Por cable$name"
                AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY ->
                    "USB-C$name (inalámbrico/solapa)"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth$name (calidad llamada)"
                TYPE_BLE_HEADSET -> "Bluetooth LE$name"
                else -> continue
            }
            list += MicOption("${d.type}:${d.address}:$raw", label, d)
        }
        return list
    }

    fun unprocessedSupported(ctx: Context): Boolean {
        val am = ctx.getSystemService(AudioManager::class.java)
        return am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
    }

    /** Arma la configuración según los ajustes. Devuelve null si el audio está apagado. */
    fun build(ctx: Context, prefs: Prefs, force: Boolean = false): AudioConfig? {
        if (!prefs.audioOn && !force) return null
        val opts = mics(ctx)
        val opt = opts.firstOrNull { it.key == prefs.micKey }
            ?: opts.firstOrNull { o -> o.device != null && prefs.micKey.startsWith("${o.device.type}:") }
            ?: opts.first()
        val dev = opt.device
        val bt = isBluetooth(dev)
        var src = prefs.audioSource
        if (src == MediaRecorder.AudioSource.UNPROCESSED && !unprocessedSupported(ctx)) src = MediaRecorder.AudioSource.VOICE_RECOGNITION
        if (bt) src = MediaRecorder.AudioSource.VOICE_COMMUNICATION
        val channels = if (bt) 1 else if (prefs.stereo) 2 else 1
        val rate = if (bt) 16000 else 48000
        val br = if (bt) 64000 else prefs.audioBitrate
        val shortName = opt.label.substringBefore(" (").substringBefore(" ·")
        return AudioConfig(src, dev, channels, rate, br, bt, "$shortName · ${sourceLabel(src)}")
    }

    /** Crea y prepara un MediaRecorder solo de audio. Prueba estéreo y si falla, mono. */
    fun prepareAudioRecorder(ctx: Context, cfg: AudioConfig, setOutput: (MediaRecorder) -> Unit): MediaRecorder {
        val tries = if (cfg.channels == 2) listOf(2, 1) else listOf(1)
        var last: Exception? = null
        for (ch in tries) {
            var r: MediaRecorder? = null
            try {
                r = newRecorder(ctx)
                r.setAudioSource(cfg.source)
                r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutput(r)
                r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                r.setAudioSamplingRate(cfg.sampleRate)
                r.setAudioEncodingBitRate(cfg.bitrate)
                r.setAudioChannels(ch)
                val dev = cfg.device
                if (dev != null) r.setPreferredDevice(dev)
                r.prepare()
                return r
            } catch (e: Exception) {
                last = e
                try { r?.release() } catch (_: Exception) { }
            }
        }
        throw last ?: IllegalStateException("No se pudo preparar el micrófono")
    }

    fun newRecorder(ctx: Context): MediaRecorder =
        if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()

    /** Micrófonos Bluetooth: hay que activarlos como "dispositivo de comunicación". */
    fun setComm(ctx: Context, dev: AudioDeviceInfo?): Boolean {
        if (dev == null || Build.VERSION.SDK_INT < 31) return false
        return try {
            val am = ctx.getSystemService(AudioManager::class.java)
            val target = am.availableCommunicationDevices.firstOrNull { it.type == dev.type && it.address == dev.address }
                ?: am.availableCommunicationDevices.firstOrNull { it.type == dev.type }
            target != null && am.setCommunicationDevice(target)
        } catch (e: Exception) { false }
    }

    fun clearComm(ctx: Context) {
        if (Build.VERSION.SDK_INT < 31) return
        try { ctx.getSystemService(AudioManager::class.java).clearCommunicationDevice() } catch (_: Exception) { }
    }
}

/** Grabador de voz en off: guarda un .m4a en Música/Enfoque. */
class VoiceRecorder(private val ctx: Context) {
    private var recorder: MediaRecorder? = null
    private var pfd: ParcelFileDescriptor? = null
    private var uri: Uri? = null
    private var comm = false
    var isPaused = false
        private set
    val isRecording: Boolean get() = recorder != null

    /** Devuelve null si arrancó bien, o un mensaje de error. */
    fun start(cfg: AudioConfig): String? {
        val resolver = ctx.contentResolver
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "VOZ_$stamp.m4a")
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Enfoque")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val u = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cv) ?: return "No se pudo crear el archivo de audio"
        return try {
            val fd = resolver.openFileDescriptor(u, "rw") ?: throw IllegalStateException("sin acceso al archivo")
            val r = AudioSetup.prepareAudioRecorder(ctx, cfg) { it.setOutputFile(fd.fileDescriptor) }
            if (cfg.needsComm) comm = AudioSetup.setComm(ctx, cfg.device)
            r.start()
            recorder = r; pfd = fd; uri = u; isPaused = false
            null
        } catch (e: Exception) {
            try { resolver.delete(u, null, null) } catch (_: Exception) { }
            "No se pudo grabar: ${e.message}"
        }
    }

    fun pause() { try { recorder?.pause(); isPaused = true } catch (_: Exception) { } }
    fun resume() { try { recorder?.resume(); isPaused = false } catch (_: Exception) { } }

    fun amplitude(): Int = try { recorder?.maxAmplitude ?: 0 } catch (e: Exception) { 0 }

    /** Termina y devuelve el archivo (o null si falló). */
    fun stop(): Uri? {
        val r = recorder ?: return null
        var ok = true
        try { r.stop() } catch (e: Exception) { ok = false }
        try { r.release() } catch (_: Exception) { }
        recorder = null
        try { pfd?.close() } catch (_: Exception) { }
        pfd = null
        if (comm) { AudioSetup.clearComm(ctx); comm = false }
        val u = uri ?: return null
        uri = null
        return try {
            if (ok) {
                ctx.contentResolver.update(u, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                u
            } else {
                ctx.contentResolver.delete(u, null, null); null
            }
        } catch (e: Exception) { null }
    }
}
