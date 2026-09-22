package com.ai27.app.custodio.data.local.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import com.ai27.app.custodio.domain.model.Limites
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext

// Implementación real de [Grabadora] con MediaRecorder (MPEG-4/AAC en cacheDir).
class GrabadoraMediaRecorder(private val context: Context) : Grabadora {

    private var recorder: MediaRecorder? = null
    private var rutaActual: File? = null

    // La duración se lleva por segmentos porque la grabación se puede pausar: acumulado
    // guarda lo ya grabado y inicioSegmento marca el arranque del tramo en curso. Medir
    // "ahora menos el inicio" contaría también el tiempo que la app estuvo en segundo
    // plano, y el cronómetro mentiría respecto al audio real (hallazgo E5).
    private var acumuladoMs: Long = 0L
    private var inicioSegmentoMs: Long = 0L
    private var enGrabacion: Boolean = false
    private var enPausa: Boolean = false

    override val grabando: Boolean get() = enGrabacion
    override val pausada: Boolean get() = enPausa

    // replay = 1 y DROP_OLDEST: el evento puede llegar antes de que la UI se suscriba (el
    // grabador avisa desde su propio hilo), y perderlo sería volver al silencio de E5.
    private val flujoEventos = MutableSharedFlow<EventoGrabacion>(
        replay = 1,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val eventos: Flow<EventoGrabacion> = flujoEventos

    override suspend fun iniciar(): Boolean = withContext(Dispatchers.IO) {
        detenerInterno()
        borrarArchivoActual()
        val ruta = File(context.cacheDir, "$PREFIJO_NOTA${System.currentTimeMillis()}$SUFIJO_NOTA")
        try {
            val nuevo = nuevoRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64_000)
                setAudioSamplingRate(44_100)
                setOutputFile(ruta.absolutePath)
                // El tope lo impone el grabador, no el cronómetro de la UI: si la corrutina
                // del timer muere, el archivo crecía sin límite.
                setMaxDuration(Limites.AUDIO_MAX_MS.toInt())
                setOnErrorListener { _, que, _ ->
                    enGrabacion = false
                    enPausa = false
                    flujoEventos.tryEmit(EventoGrabacion.Fallo(que))
                }
                setOnInfoListener { _, que, _ ->
                    if (que == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                        flujoEventos.tryEmit(EventoGrabacion.LimiteAlcanzado)
                    }
                }
                prepare()
                start()
            }
            recorder = nuevo
            rutaActual = ruta
            acumuladoMs = 0L
            inicioSegmentoMs = SystemClock.elapsedRealtime()
            enGrabacion = true
            enPausa = false
            true
        } catch (_: Throwable) {
            // Throwable y no Exception: si el constructor de MediaRecorder no existe en el
            // equipo, el runtime lanza NoSuchMethodError, que es Error y no Exception.
            recorder = null
            enGrabacion = false
            enPausa = false
            ruta.delete()
            false
        }
    }

    override suspend fun pausar() = withContext(Dispatchers.IO) {
        if (!enGrabacion || enPausa) return@withContext
        val actual = recorder ?: return@withContext
        try {
            actual.pause()
            acumuladoMs += SystemClock.elapsedRealtime() - inicioSegmentoMs
            enPausa = true
        } catch (_: IllegalStateException) {
            // El grabador ya no está en un estado pausable: se trata como fallo para que
            // el custodio lo sepa, en lugar de dejarlo creyendo que su nota sigue viva.
            flujoEventos.tryEmit(EventoGrabacion.Fallo(MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN))
        }
    }

    override suspend fun reanudar(): Boolean = withContext(Dispatchers.IO) {
        if (!enGrabacion || !enPausa) return@withContext false
        val actual = recorder ?: return@withContext false
        try {
            actual.resume()
            inicioSegmentoMs = SystemClock.elapsedRealtime()
            enPausa = false
            true
        } catch (_: IllegalStateException) {
            flujoEventos.tryEmit(EventoGrabacion.Fallo(MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN))
            false
        }
    }

    override fun duracionActualMs(): Long {
        if (!enGrabacion) return 0L
        val enCurso = if (enPausa) 0L else SystemClock.elapsedRealtime() - inicioSegmentoMs
        return (acumuladoMs + enCurso).coerceAtMost(Limites.AUDIO_MAX_MS)
    }

    override suspend fun detener(): Grabacion? = withContext(Dispatchers.IO) {
        if (!enGrabacion) return@withContext null
        val duracion = duracionActualMs()
        val ruta = rutaActual ?: return@withContext null
        enGrabacion = false
        enPausa = false
        var bytes: ByteArray? = null
        try {
            // stop() cierra el índice del MPEG-4 incluso si venía en pausa: es lo que hace
            // el archivo legible. Por eso una nota pausada se puede guardar, pero una
            // interrumpida por muerte del proceso no.
            detenerInterno()
        } finally {
            if (ruta.exists()) {
                bytes = ruta.readBytes()
                ruta.delete()
            }
            rutaActual = null
        }
        val b = bytes
        if (b != null && b.isNotEmpty() && duracion > 0L) {
            Grabacion(bytes = b, durationMs = duracion)
        } else {
            null
        }
    }

    override suspend fun descartar() = withContext(Dispatchers.IO) {
        enGrabacion = false
        enPausa = false
        acumuladoMs = 0L
        detenerInterno()
        borrarArchivoActual()
    }

    override suspend fun barrerNotasHuerfanas(): Int = withContext(Dispatchers.IO) {
        val huerfanas = context.cacheDir
            .listFiles { archivo ->
                archivo.isFile &&
                    archivo.name.startsWith(PREFIJO_NOTA) &&
                    archivo.name.endsWith(SUFIJO_NOTA) &&
                    archivo.absolutePath != rutaActual?.absolutePath
            }
            ?: return@withContext 0
        huerfanas.forEach { it.delete() }
        huerfanas.size
    }

    /**
     * `MediaRecorder(Context)` existe desde API 31. Con `minSdk = 26` hay que caer al
     * constructor sin contexto en los equipos anteriores; usarlo sin el gate lanza
     * `NoSuchMethodError` y cierra la app al grabar (ver B2 en `docs/AUDIT.md`).
     */
    private fun nuevoRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

    private fun borrarArchivoActual() {
        rutaActual?.let { if (it.exists()) it.delete() }
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

    private companion object {
        const val PREFIJO_NOTA = "nota-"
        const val SUFIJO_NOTA = ".m4a"
    }
}
