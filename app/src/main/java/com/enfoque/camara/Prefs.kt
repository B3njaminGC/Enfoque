package com.enfoque.camara

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaRecorder

/** Ajustes guardados entre usos. */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("enfoque", Context.MODE_PRIVATE)

    /** Foto de todos los ajustes, para saber si algo cambió. */
    fun snapshot(): String = sp.all.toString()

    private fun bool(key: String, def: Boolean) = sp.getBoolean(key, def)
    private fun setBool(key: String, v: Boolean) = sp.edit().putBoolean(key, v).apply()
    private fun int(key: String, def: Int) = sp.getInt(key, def)
    private fun setInt(key: String, v: Int) = sp.edit().putInt(key, v).apply()
    private fun str(key: String, def: String) = sp.getString(key, def) ?: def
    private fun setStr(key: String, v: String) = sp.edit().putString(key, v).apply()

    // ---- Foto ----
    /** true = máxima resolución del sensor (108MP). false = 12MP con binning. */
    var maxRes: Boolean get() = bool("maxRes", false); set(v) = setBool("maxRes", v)
    var ratio169: Boolean get() = bool("ratio169", false); set(v) = setBool("ratio169", v)
    var fastShutter: Boolean get() = bool("fastShutter", false); set(v) = setBool("fastShutter", v)
    var jpegQuality: Int get() = int("jpegQuality", 95); set(v) = setInt("jpegQuality", v)
    var photoFormat: Int get() = int("photoFormat", 0); set(v) = setInt("photoFormat", v)
    var mirrorFront: Boolean get() = bool("mirrorFront", true); set(v) = setBool("mirrorFront", v)

    // ---- General ----
    var grid: Boolean get() = bool("grid", true); set(v) = setBool("grid", v)
    var level: Boolean get() = bool("level", true); set(v) = setBool("level", v)
    var histogram: Boolean get() = bool("histogram", false); set(v) = setBool("histogram", v)
    var sound: Boolean get() = bool("sound", true); set(v) = setBool("sound", v)
    var volumeShutter: Boolean get() = bool("volumeShutter", true); set(v) = setBool("volumeShutter", v)
    var lastMode: Int get() = int("lastMode", 0); set(v) = setInt("lastMode", v)
    var guides: Boolean get() = bool("guides", true); set(v) = setBool("guides", v)

    // ---- Video ----
    /** 0 = 2K (1440p), 1 = 1080p, 2 = 720p, 3 = 4K (si el celu lo tuviera) */
    var videoRes: Int get() = int("videoRes", 0); set(v) = setInt("videoRes", v)
    var fps60: Boolean get() = bool("fps60v2", false); set(v) = setBool("fps60v2", v)
    var hevc: Boolean get() = bool("hevc", false); set(v) = setBool("hevc", v)
    /** 0 = liviano, 1 = alta, 2 = máxima */
    var videoBitrateLevel: Int get() = int("videoBitrate", 1); set(v) = setInt("videoBitrate", v)
    var stabilization: Boolean get() = bool("stabilization", true); set(v) = setBool("stabilization", v)
    /** Evita el parpadeo de las luces en Argentina (red de 50 Hz). */
    var antibanding50: Boolean get() = bool("antibanding50", true); set(v) = setBool("antibanding50", v)
    /** 0 = 1 minuto, 1 = 3 minutos, 2 = sin límite */
    var shortsLimit: Int get() = int("shortsLimit", 0); set(v) = setInt("shortsLimit", v)

    // ---- Audio ----
    var audioOn: Boolean get() = bool("audioOn", true); set(v) = setBool("audioOn", v)
    var micKey: String get() = str("micKey", "auto"); set(v) = setStr("micKey", v)
    var audioSource: Int get() = int("audioSource", MediaRecorder.AudioSource.CAMCORDER); set(v) = setInt("audioSource", v)
    var stereo: Boolean get() = bool("stereo", true); set(v) = setBool("stereo", v)
    var audioBitrate: Int get() = int("audioBitrate", 256_000); set(v) = setInt("audioBitrate", v)

    // ---- Teleprompter ----
    var prompterOn: Boolean get() = bool("prompterOn", false); set(v) = setBool("prompterOn", v)
    var prompterText: String get() = str("prompterText", ""); set(v) = setStr("prompterText", v)
    var prompterSpeed: Int get() = int("prompterSpeed", 1); set(v) = setInt("prompterSpeed", v)
    var prompterSize: Int get() = int("prompterSize", 24); set(v) = setInt("prompterSize", v)
}
