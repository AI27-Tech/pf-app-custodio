package com.ai27.app.custodio.data.local.media

data class Grabacion(
    val bytes: ByteArray,
    val durationMs: Long,
)

/**
 * Graba una nota de voz. Es una interfaz para poder sustituirla por un doble en pruebas
 * de JVM; la implementación real [GrabadoraMediaRecorder] usa `MediaRecorder` y necesita
 * un `Context` de Android.
 */
interface Grabadora {
    val grabando: Boolean
    fun iniciar(): Boolean
    fun duracionActualMs(): Long
    fun detener(): Grabacion?
    fun descartar()
}
