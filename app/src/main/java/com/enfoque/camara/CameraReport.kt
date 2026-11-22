package com.enfoque.camara

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.ImageCapture
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import java.util.Locale

/** Arma un informe legible de todo lo que el teléfono le deja usar a una app de cámara. */
object CameraReport {

    private val capNames = mapOf(
        0 to "Básica", 1 to "Sensor manual", 2 to "Post-proceso manual", 3 to "RAW",
        5 to "Lee ajustes del sensor", 6 to "Ráfaga", 8 to "Profundidad", 9 to "Cámara lenta",
        11 to "Multi-cámara lógica", 12 to "Monocromo", 16 to "Ultra alta resolución",
        17 to "Remosaico", 18 to "10 bits (HDR)", 19 to "Casos de uso de stream"
    )

    private fun level(l: Int?): String = when (l) {
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY (básico)"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL 3 (máximo)"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNA"
        else -> "?"
    }

    private fun mp(s: Size?): String =
        if (s == null) "-" else "${s.width}x${s.height} (${String.format(Locale.US, "%.1f", s.width * s.height / 1_000_000f)}MP)"

    private fun maxSize(sizes: Array<Size>?): Size? = sizes?.maxByOrNull { it.width.toLong() * it.height }

    private fun shutter(ns: Long): String =
        if (ns >= 1_000_000_000L) String.format(Locale.US, "%.1fs", ns / 1e9) else "1/${(1e9 / ns).toInt()}s"

    fun build(context: Context, provider: ProcessCameraProvider, ext: ExtensionsManager?, lenses: List<Lens>): String {
        val cm = context.getSystemService(CameraManager::class.java)
        val sb = StringBuilder()
        sb.append("Teléfono: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}\n")
        sb.append("Lentes que usa Enfoque: ")
        sb.append(lenses.joinToString { (if (it.facing == CameraCharacteristics.LENS_FACING_FRONT) "frontal " else "") + it.zoomLabel })
        sb.append("\n\n")

        val visible = provider.availableCameraInfos.associateBy { Camera2CameraInfo.from(it).cameraId }
        val ids = LinkedHashSet<String>()
        ids.addAll(cm.cameraIdList)
        for (id in cm.cameraIdList) try { ids.addAll(cm.getCameraCharacteristics(id).physicalCameraIds) } catch (_: Exception) { }

        for (id in ids) {
            val ch = try { cm.getCameraCharacteristics(id) } catch (e: Exception) { continue }
            val facing = when (ch.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_BACK -> "trasera"
                CameraCharacteristics.LENS_FACING_FRONT -> "frontal"
                else -> "externa"
            }
            val inList = id in cm.cameraIdList
            sb.append("■ Cámara $id ($facing)")
            sb.append(if (inList) " · visible para apps\n" else " · física (escondida dentro de otra)\n")
            sb.append("  Nivel: ${level(ch.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL))}\n")

            val (focal, eq) = LensFinder.eq35(ch)
            val ap = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.firstOrNull()
            sb.append("  Lente: ${String.format(Locale.US, "%.2f", focal)}mm (≈${eq.toInt()}mm equiv.)")
            if (ap != null) sb.append(" · f/$ap")
            sb.append("\n")
            val phys = ch.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            val arr = ch.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
            if (phys != null) sb.append("  Sensor: ${String.format(Locale.US, "%.2fx%.2f", phys.width, phys.height)}mm, ${mp(arr)}\n")

            val map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            sb.append("  Foto normal máx: ${mp(maxSize(map?.getOutputSizes(ImageFormat.JPEG)))}\n")
            val hi = maxSize(map?.getHighResolutionOutputSizes(ImageFormat.JPEG))
            if (hi != null) sb.append("  Foto alta resolución: ${mp(hi)}\n")
            if (Build.VERSION.SDK_INT >= 31) {
                val maxMap = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION)
                val m = maxSize(maxMap?.getOutputSizes(ImageFormat.JPEG)) ?: maxSize(maxMap?.getOutputSizes(ImageFormat.RAW_SENSOR))
                if (m != null) sb.append("  Modo máxima resolución: ${mp(m)}\n")
            }
            val raw = maxSize(map?.getOutputSizes(ImageFormat.RAW_SENSOR))
            if (raw != null) sb.append("  RAW: ${mp(raw)}\n")

            ch.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.let { sb.append("  ISO: ${it.lower}–${it.upper}\n") }
            ch.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.let {
                sb.append("  Exposición: ${shutter(it.lower)} a ${shutter(it.upper)}\n")
            }
            val mf = ch.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
            sb.append(if (mf > 0f) "  Enfoque mínimo: ${(100f / mf).toInt()}cm\n" else "  Enfoque: fijo\n")
            if (Build.VERSION.SDK_INT >= 30) {
                ch.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.let {
                    sb.append("  Zoom: ${LensFinder.fmtZoom(it.lower)} a ${LensFinder.fmtZoom(it.upper)}\n")
                }
            }
            ch.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.let { r ->
                sb.append("  FPS: ${r.joinToString { "${it.lower}-${it.upper}" }}\n")
            }
            val caps = ch.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)
            sb.append("  Capacidades: ${caps.toList().mapNotNull { capNames[it] }.joinToString()}\n")
            if (ch.physicalCameraIds.isNotEmpty()) sb.append("  Cámaras físicas adentro: ${ch.physicalCameraIds.joinToString()}\n")

            val info = visible[id]
            if (info != null) {
                try {
                    val fmts = ImageCapture.getImageCaptureCapabilities(info).supportedOutputFormats
                    val names = fmts.map {
                        when (it) {
                            ImageCapture.OUTPUT_FORMAT_JPEG -> "JPEG"
                            ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR -> "Ultra HDR"
                            ImageCapture.OUTPUT_FORMAT_RAW -> "RAW (DNG)"
                            ImageCapture.OUTPUT_FORMAT_RAW_JPEG -> "RAW+JPEG"
                            else -> "formato $it"
                        }
                    }
                    sb.append("  Formatos en Enfoque: ${names.joinToString()}\n")
                } catch (_: Exception) { }
                if (ext != null) {
                    val sel = androidx.camera.core.CameraSelector.Builder()
                        .addCameraFilter { list -> list.filter { Camera2CameraInfo.from(it).cameraId == id } }
                        .build()
                    val modes = listOf(
                        ExtensionMode.NIGHT to "Noche", ExtensionMode.HDR to "HDR", ExtensionMode.BOKEH to "Retrato",
                        ExtensionMode.FACE_RETOUCH to "Retoque", ExtensionMode.AUTO to "Auto"
                    )
                    val av = modes.filter { (m, _) -> try { ext.isExtensionAvailable(sel, m) } catch (e: Exception) { false } }
                    sb.append("  Modos del fabricante: ${if (av.isEmpty()) "ninguno abierto a otras apps" else av.joinToString { it.second }}\n")
                }
            }
            sb.append("\n")
        }
        return sb.toString()
    }
}
