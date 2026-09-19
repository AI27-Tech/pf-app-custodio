package com.ai27.app.custodio.data.local.media

import android.content.Context
import android.media.MediaRecorder
import com.ai27.app.custodio.domain.model.Limites
import java.io.File

data class Grabacion(
    val bytes: ByteArray,
    val durationMs: Long,
)

class Grabadora(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var rutaActual: File? = null
    private var inicioElapsedMs: Long = 0L
    private var enGrabacion: Boolean = false

    val grabando: Boolean get() = enGrabacion

    fun iniciar(): Boolean {
        detenerInterno()
        val ruta = File(context.cacheDir, "nota-${System.currentTimeMillis()}.m4a")
        return try {
            val nuevo = MediaRecorder(context).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64_000)
                setAudioSamplingRate(44_100)
                setOutputFile(ruta.absolutePath)
                prepare()
                start()
            }
            recorder = nuevo
            rutaActual = ruta
            inicioElapsedMs = android.os.SystemClock.elapsedRealtime()
            enGrabacion = true
            true
        } catch (_: Exception) {
            recorder = null
            ruta.delete()
            false
        }
    }

    fun duracionActualMs(): Long {
        if (!enGrabacion) return 0L
        return (android.os.SystemClock.elapsedRealtime() - inicioElapsedMs)
            .coerceAtMost(Limites.AUDIO_MAX_MS)
    }

    fun detener(): Grabacion? {
        if (!enGrabacion) return null
        val duracion = duracionActualMs()
        val ruta = rutaActual ?: return null
        enGrabacion = false
        var bytes: ByteArray? = null
        try {
            detenerInterno()
        } finally {
            if (ruta.exists()) {
                bytes = ruta.readBytes()
                ruta.delete()
            }
        }
        val b = bytes
        return if (b != null && b.isNotEmpty() && duracion > 0L) {
            Grabacion(bytes = b, durationMs = duracion)
        } else {
            null
        }
    }

    fun descartar() {
        enGrabacion = false
        detenerInterno()
        rutaActual?.let { ruta ->
            if (ruta.exists()) ruta.delete()
        }
        rutaActual = null
    }

    private fun detenerInterno() {
        val actual = recorder ?: return
        recorder = null
        try {
            actual.stop()
        } catch (_: Exception) {
        } finally {
            try {
                actual.release()
            } catch (_: Exception) {
            }
        }
    }
}