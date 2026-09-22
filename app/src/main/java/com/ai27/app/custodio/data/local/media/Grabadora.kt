package com.ai27.app.custodio.data.local.media

import kotlinx.coroutines.flow.Flow

data class Grabacion(
    val bytes: ByteArray,
    val durationMs: Long,
)

/**
 * Lo que le puede pasar a una grabación **sin que nadie la haya tocado**: el grabador
 * muere por su cuenta o alcanza el tope de duración. Antes esto no existía y por eso una
 * nota podía quedar en silencio o corrupta sin que la app se enterara (hallazgo E5).
 */
sealed interface EventoGrabacion {
    /** `MediaRecorder` alcanzó el máximo configurado y se detuvo solo. La nota sirve. */
    object LimiteAlcanzado : EventoGrabacion

    /** El grabador falló (p. ej. `MEDIA_RECORDER_ERROR_SERVER_DIED`). La nota no sirve. */
    data class Fallo(val codigo: Int) : EventoGrabacion
}

/**
 * Graba una nota de voz. Es una interfaz para poder sustituirla por un doble en pruebas
 * de JVM; la implementación real [GrabadoraMediaRecorder] usa `MediaRecorder` y necesita
 * un `Context` de Android.
 *
 * Las operaciones son `suspend` porque `prepare`, `start`, `stop` y la lectura del `.m4a`
 * tocan disco: en el hilo principal eso son ANR en un equipo de campo (hallazgo E5).
 */
interface Grabadora {
    val grabando: Boolean

    /**
     * Grabación iniciada pero detenida a medias. Ocurre cuando la app pasa a segundo
     * plano: la nota se pausa sola para no quedarse con el micrófono ni grabar basura, y
     * el custodio la reanuda con el mismo botón al volver.
     */
    val pausada: Boolean

    /** Eventos asíncronos del grabador. Quien la use debe recogerlos o los fallos se pierden. */
    val eventos: Flow<EventoGrabacion>

    suspend fun iniciar(): Boolean

    /** Pausa el segmento actual. Sin efecto si no se está grabando o ya está pausada. */
    suspend fun pausar()

    /** Reanuda tras [pausar]. Devuelve `false` si el grabador no pudo continuar. */
    suspend fun reanudar(): Boolean

    /** Duración acumulada de todos los segmentos, sin contar las pausas. */
    fun duracionActualMs(): Long

    suspend fun detener(): Grabacion?
    suspend fun descartar()

    /**
     * Borra los `.m4a` que quedaron en caché de una grabación que nunca se cerró, y
     * devuelve cuántos había.
     *
     * Existe porque `pause()` no escribe el índice del MPEG-4: si el proceso muere a media
     * nota, el archivo es irrecuperable. Un archivo suelto es la prueba de que se perdió
     * una nota, así que sirve de marca para poder **avisar** en lugar de perderla en
     * silencio (R3 de §13 en `docs/PLAN.md`).
     */
    suspend fun barrerNotasHuerfanas(): Int
}
