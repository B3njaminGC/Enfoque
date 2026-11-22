package com.enfoque.camara

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Range
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Un lente usable. Si [physicalId] no es null, es un sensor "escondido" dentro de una cámara lógica
 * (muchos celulares esconden la gran angular así) y lo abrimos pidiendo esa cámara física.
 */
class Lens(
    val cameraId: String,
    val physicalId: String?,
    val facing: Int,
    val chars: CameraCharacteristics,
    val cameraInfo: CameraInfo,
    val focalMm: Float,
    val eq35: Float,
    val maxPixels: Long
) {
    var relZoom = 1f
    var isMain = false

    val selector: CameraSelector by lazy {
        CameraSelector.Builder()
            .addCameraFilter { infos -> infos.filter { Camera2CameraInfo.from(it).cameraId == cameraId } }
            .build()
    }

    val key: String get() = cameraId + "/" + (physicalId ?: "")

    val zoomLabel: String
        get() = if (relZoom < 0.95f || relZoom > 1.05f) LensFinder.fmtZoom(relZoom) else "1x"

    fun <T> get(key: CameraCharacteristics.Key<T>): T? = try { chars.get(key) } catch (e: Exception) { null }

    val isoRange: Range<Int>? get() = get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
    val exposureRange: Range<Long>? get() = get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
    val minFocusDistance: Float get() = get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
    val capabilities: IntArray get() = get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)
    val manualSensor: Boolean
        get() = capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
    val aeFpsRanges: Array<Range<Int>> get() = get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: emptyArray()
    val videoStabModes: IntArray get() = get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES) ?: IntArray(0)
}

object LensFinder {

    fun fmtZoom(z: Float): String {
        val s = if (z < 10f) String.format(java.util.Locale.US, "%.1f", z) else z.toInt().toString()
        return s.removeSuffix(".0") + "x"
    }

    fun maxPixels(ch: CameraCharacteristics): Long {
        var best = 0L
        fun consider(sizes: Array<Size>?) {
            sizes?.forEach { val p = it.width.toLong() * it.height; if (p > best) best = p }
        }
        try {
            val map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            consider(map?.getOutputSizes(ImageFormat.JPEG))
            consider(map?.getHighResolutionOutputSizes(ImageFormat.JPEG))
            if (Build.VERSION.SDK_INT >= 31) {
                val maxMap = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION)
                consider(maxMap?.getOutputSizes(ImageFormat.JPEG))
                consider(maxMap?.getOutputSizes(ImageFormat.RAW_SENSOR))
            }
        } catch (_: Exception) { }
        return best
    }

    fun eq35(ch: CameraCharacteristics): Pair<Float, Float> {
        val focal = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: 0f
        val size = ch.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val diag = if (size != null) hypot(size.width.toDouble(), size.height.toDouble()).toFloat() else 0f
        val eq = if (focal > 0f && diag > 0f) focal * 43.27f / diag else 0f
        return focal to eq
    }

    fun find(context: Context, provider: ProcessCameraProvider): List<Lens> {
        val cm = context.getSystemService(CameraManager::class.java)
        val infos = provider.availableCameraInfos
        val visibleIds = infos.map { Camera2CameraInfo.from(it).cameraId }.toSet()
        val found = mutableListOf<Lens>()

        fun make(id: String, phys: String?, ch: CameraCharacteristics, info: CameraInfo, facing: Int): Lens {
            val (focal, eq) = eq35(ch)
            return Lens(id, phys, facing, ch, info, focal, eq, maxPixels(ch))
        }

        for (info in infos) {
            val id = Camera2CameraInfo.from(info).cameraId
            val ch = try { cm.getCameraCharacteristics(id) } catch (e: Exception) { continue }
            val facing = ch.get(CameraCharacteristics.LENS_FACING) ?: continue
            if (facing == CameraCharacteristics.LENS_FACING_EXTERNAL) continue
            found += make(id, null, ch, info, facing)

            // Sensores físicos escondidos dentro de una cámara lógica (típico de la gran angular).
            if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                for (p in ch.physicalCameraIds) {
                    if (p in visibleIds) continue
                    val pc = try { cm.getCameraCharacteristics(p) } catch (e: Exception) { null } ?: continue
                    found += make(id, p, pc, info, facing)
                }
            }
        }

        // Fuera sensores de profundidad / macro de 2MP: no sirven para sacar fotos.
        var usable = found.filter { lens ->
            val depthOnly = lens.capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT) &&
                !lens.capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE)
            !depthOnly && (lens.facing == CameraCharacteristics.LENS_FACING_FRONT || lens.maxPixels >= 4_000_000L || lens.maxPixels == 0L)
        }
        if (usable.none { it.facing == CameraCharacteristics.LENS_FACING_BACK }) usable = found

        // Sacar duplicados (mismo lente visto dos veces).
        val unique = mutableListOf<Lens>()
        for (l in usable.sortedBy { if (it.physicalId == null) 0 else 1 }) {
            val dup = unique.any {
                it.facing == l.facing && it.eq35 > 0f && l.eq35 > 0f && abs(it.eq35 - l.eq35) / it.eq35 < 0.06f
            }
            if (!dup) unique += l
        }

        for (facing in listOf(CameraCharacteristics.LENS_FACING_BACK, CameraCharacteristics.LENS_FACING_FRONT)) {
            val group = unique.filter { it.facing == facing }
            val main = group.firstOrNull { it.physicalId == null } ?: group.firstOrNull() ?: continue
            main.isMain = true
            for (l in group) l.relZoom = if (main.eq35 > 0f && l.eq35 > 0f) l.eq35 / main.eq35 else 1f
        }
        return unique.sortedWith(compareBy<Lens>({ it.facing }, { it.relZoom }))
    }
}
