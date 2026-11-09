package com.ai27.app.custodio.data.foto

import com.ai27.app.custodio.data.marcaDeAgua.ImagenMapa
import com.ai27.app.custodio.domain.marcaDeAgua.MarcaDeAgua

/**
 * Codec falso compartido por `RevelarFotoTest` y `SubidaMedioProcesoTest`: registra cada
 * llamada y el hilo donde ocurrió, y falla a demanda.
 */
internal class CodecFotoFalso(
    var dimensiones: Dimensiones? = Dimensiones(960, 1280),
    var fallarConMarca: Boolean = false,
    var fallarSinMarca: Boolean = false,
    var demoraMs: Long = 0,
) : CodecFoto {
    data class LlamadaRevelar(val rotacion: Int, val marca: MarcaDeAgua?, val mapa: ImagenMapa?)

    val revelados = mutableListOf<LlamadaRevelar>()
    val hilos = mutableListOf<Thread>()
    var llamadas = 0

    override fun dimensionesFinales(crudo: ByteArray, rotacionGrados: Int): Dimensiones? {
        registrar()
        return dimensiones
    }

    override fun revelar(crudo: ByteArray, rotacionGrados: Int, marca: MarcaDeAgua?, mapa: ImagenMapa?): ByteArray {
        registrar()
        revelados.add(LlamadaRevelar(rotacionGrados, marca, mapa))
        if (demoraMs > 0) Thread.sleep(demoraMs)
        if (marca != null && fallarConMarca) throw OutOfMemoryError("marca")
        if (marca == null && fallarSinMarca) throw IllegalStateException("sin marca")
        return if (marca != null) REVELADA else SIN_MARCA
    }

    override fun miniatura(crudo: ByteArray, rotacionGrados: Int): ByteArray {
        registrar()
        return MINIATURA
    }

    override fun conOrientacionExif(crudo: ByteArray, rotacionGrados: Int): ByteArray {
        registrar()
        return CON_EXIF
    }

    private fun registrar() {
        llamadas++
        hilos.add(Thread.currentThread())
    }

    companion object {
        val REVELADA = ByteArray(2048) { 7 }
        val SIN_MARCA = ByteArray(1024) { 8 }
        val MINIATURA = byteArrayOf(9, 9)
        val CON_EXIF = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x45)
    }
}
