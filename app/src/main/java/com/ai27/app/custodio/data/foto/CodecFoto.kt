package com.ai27.app.custodio.data.foto

import com.ai27.app.custodio.data.marcaDeAgua.ImagenMapa
import com.ai27.app.custodio.domain.marcaDeAgua.MarcaDeAgua

/** Ancho y alto en píxeles de una foto ya rotada. */
data class Dimensiones(val ancho: Int, val alto: Int)

/**
 * Costura interna de [RevelarFoto]: esconde `Bitmap` detrás de bytes para que la lógica de
 * decisión (con o sin marca, con o sin mapa, qué hacer si falla) se pruebe en JVM con un
 * codec falso. El adaptador de producción es [BitmapCodecFoto].
 *
 * Rotaciones admitidas: 0, 90, 180 y 270; cualquier otra es un error del llamador.
 */
interface CodecFoto {
    /**
     * Dimensiones de la foto revelada (tras rotar y acotar el lado largo a [LADO_MAX_PX]) sin
     * decodificar los píxeles. `null` si los bytes no son una imagen legible.
     */
    fun dimensionesFinales(crudo: ByteArray, rotacionGrados: Int): Dimensiones?

    /**
     * Una sola vuelta: decode al tamaño objetivo, rotación, marca (si [marca] no es `null`;
     * el [mapa] es opcional) y WebP `CALIDAD_ORIGINAL`. Lanza si no puede decodificar.
     */
    fun revelar(crudo: ByteArray, rotacionGrados: Int, marca: MarcaDeAgua?, mapa: ImagenMapa?): ByteArray

    /**
     * Miniatura limpia (sin marca), rotada, lado largo `DIMENSION_MAX_PX`, WebP
     * `CALIDAD_MINIATURA`. Lanza si no puede decodificar.
     */
    fun miniatura(crudo: ByteArray, rotacionGrados: Int): ByteArray

    /**
     * Los mismos bytes JPEG con la orientación EXIF que corresponde a [rotacionGrados], para
     * que una foto que no se pudo revelar no se vea de lado.
     */
    fun conOrientacionExif(crudo: ByteArray, rotacionGrados: Int): ByteArray

    companion object {
        /** Lado largo máximo de la foto revelada (D8), aunque el HAL entregue más. */
        const val LADO_MAX_PX = 1600
    }
}
