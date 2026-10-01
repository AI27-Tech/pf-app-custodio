package com.ai27.app.custodio.data.marcaDeAgua

import android.graphics.BitmapFactory
import com.ai27.app.custodio.data.local.media.DibujanteMarcaDeAgua
import com.ai27.app.custodio.domain.marcaDeAgua.DisposicionMarcaDeAgua
import com.ai27.app.custodio.domain.marcaDeAgua.MarcaDeAgua

/**
 * Estampador real: decodifica el JPEG a `Bitmap`, lo pasa por [DibujanteMarcaDeAgua] y
 * devuelve el JPEG estampado. Toda la dependencia de Android vive aquí; es la clase que NO
 * se prueba en JVM (su prueba de dibujo real es la instrumentada de `DibujanteMarcaDeAgua`).
 */
class BitmapEstampadorFoto : EstampadorFoto {

    override fun estampar(jpegLimpio: ByteArray, marca: MarcaDeAgua, mapa: ImagenMapa?): ByteArray {
        val original = BitmapFactory.decodeByteArray(jpegLimpio, 0, jpegLimpio.size)
            ?: return jpegLimpio // JPEG ilegible: se devuelve tal cual, mejor que perder la evidencia.
        val bitmapMapa = (mapa as? BitmapImagenMapa)?.bitmap
        return DibujanteMarcaDeAgua.estampar(original, marca, bitmapMapa)
    }

    override fun ladoMapaPx(jpegLimpio: ByteArray): Int {
        // Solo lee el tamaño (inJustDecodeBounds), sin cargar el bitmap entero en memoria.
        val opciones = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpegLimpio, 0, jpegLimpio.size, opciones)
        return DisposicionMarcaDeAgua.ladoMapaPx(opciones.outWidth, opciones.outHeight)
    }
}
