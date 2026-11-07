package com.ai27.app.custodio.data.foto

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import com.ai27.app.custodio.data.foto.CodecFoto.Companion.LADO_MAX_PX
import com.ai27.app.custodio.data.local.media.DibujanteMarcaDeAgua
import com.ai27.app.custodio.data.local.media.Miniaturas
import com.ai27.app.custodio.data.marcaDeAgua.BitmapImagenMapa
import com.ai27.app.custodio.data.marcaDeAgua.ImagenMapa
import com.ai27.app.custodio.domain.marcaDeAgua.MarcaDeAgua
import java.io.File
import kotlin.math.roundToInt

/**
 * Adaptador Android de [CodecFoto]. Cada foto se decodifica una vez con `inSampleSize` y se
 * dibuja rotada y escalada en un solo bitmap destino, de modo que en memoria conviven a lo
 * sumo el decodificado y el destino, y el decodificado se recicla en cuanto se dibuja. En el
 * SC9863A de los ZTE cada bitmap completo cuesta caro (`docs/PLAN_REVELAR_FOTO.md`).
 *
 * [directorioTemporal] aloja el archivo que necesita `ExifInterface`, que en API 26 no
 * escribe sobre bytes en memoria.
 */
class BitmapCodecFoto(private val directorioTemporal: File) : CodecFoto {

    override fun dimensionesFinales(crudo: ByteArray, rotacionGrados: Int): Dimensiones? {
        validarRotacion(rotacionGrados)
        val (ancho, alto) = limites(crudo) ?: return null
        return acotar(ancho, alto, rotacionGrados, LADO_MAX_PX)
    }

    override fun revelar(crudo: ByteArray, rotacionGrados: Int, marca: MarcaDeAgua?, mapa: ImagenMapa?): ByteArray =
        procesar(crudo, rotacionGrados, LADO_MAX_PX, Miniaturas.CALIDAD_ORIGINAL) { destino ->
            if (marca != null) {
                DibujanteMarcaDeAgua.dibujarEn(destino, marca, (mapa as? BitmapImagenMapa)?.bitmap)
            }
        }

    override fun miniatura(crudo: ByteArray, rotacionGrados: Int): ByteArray =
        procesar(crudo, rotacionGrados, Miniaturas.DIMENSION_MAX_PX, Miniaturas.CALIDAD_MINIATURA) {}

    override fun conOrientacionExif(crudo: ByteArray, rotacionGrados: Int): ByteArray {
        val orientacion = when (rotacionGrados) {
            0 -> ExifInterface.ORIENTATION_NORMAL
            90 -> ExifInterface.ORIENTATION_ROTATE_90
            180 -> ExifInterface.ORIENTATION_ROTATE_180
            270 -> ExifInterface.ORIENTATION_ROTATE_270
            else -> throw IllegalArgumentException("Rotación no admitida: $rotacionGrados")
        }
        val temporal = File.createTempFile("cruda-", ".jpg", directorioTemporal)
        try {
            temporal.writeBytes(crudo)
            ExifInterface(temporal.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientacion.toString())
                saveAttributes()
            }
            return temporal.readBytes()
        } finally {
            temporal.delete()
        }
    }

    /**
     * Decode a la resolución justa, rotación + escala en una sola pasada de `Canvas` sobre el
     * destino, [dibujar] sobre ese mismo destino y un único WebP.
     */
    private inline fun procesar(
        crudo: ByteArray,
        rotacionGrados: Int,
        ladoMax: Int,
        calidad: Int,
        dibujar: (Bitmap) -> Unit,
    ): ByteArray {
        validarRotacion(rotacionGrados)
        val (anchoCrudo, altoCrudo) = limites(crudo)
            ?: throw IllegalArgumentException("Los bytes no son una imagen legible")
        val final = acotar(anchoCrudo, altoCrudo, rotacionGrados, ladoMax)
        val ladoLargoFinal = maxOf(final.ancho, final.alto)

        // La mayor potencia de 2 que deja el lado largo decodificado >= el objetivo: decodificar
        // de menos obligaría a ampliar y se perdería detalle de la evidencia.
        var muestreo = 1
        while (maxOf(anchoCrudo, altoCrudo) / (muestreo * 2) >= ladoLargoFinal) muestreo *= 2
        val decodificado = BitmapFactory.decodeByteArray(
            crudo, 0, crudo.size,
            BitmapFactory.Options().apply { inSampleSize = muestreo },
        ) ?: throw IllegalArgumentException("BitmapFactory no pudo decodificar la foto")

        val destino = try {
            val escala = ladoLargoFinal.toFloat() / maxOf(decodificado.width, decodificado.height)
            val matriz = Matrix().apply {
                postTranslate(-decodificado.width / 2f, -decodificado.height / 2f)
                postRotate(rotacionGrados.toFloat())
                postScale(escala, escala)
                postTranslate(final.ancho / 2f, final.alto / 2f)
            }
            Bitmap.createBitmap(final.ancho, final.alto, Bitmap.Config.ARGB_8888).also {
                Canvas(it).drawBitmap(decodificado, matriz, Paint(Paint.FILTER_BITMAP_FLAG))
            }
        } finally {
            decodificado.recycle()
        }
        try {
            dibujar(destino)
            return Miniaturas.webp(destino, calidad)
        } finally {
            destino.recycle()
        }
    }

    private fun limites(crudo: ByteArray): Pair<Int, Int>? {
        val opciones = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(crudo, 0, crudo.size, opciones)
        if (opciones.outWidth <= 0 || opciones.outHeight <= 0) return null
        return opciones.outWidth to opciones.outHeight
    }

    // Lado largo exactamente ladoMax (sin redondeo) cuando hay que reducir; nunca se amplía.
    private fun acotar(ancho: Int, alto: Int, rotacionGrados: Int, ladoMax: Int): Dimensiones {
        val girada = rotacionGrados == 90 || rotacionGrados == 270
        val w = if (girada) alto else ancho
        val h = if (girada) ancho else alto
        val largo = maxOf(w, h)
        if (largo <= ladoMax) return Dimensiones(w, h)
        val corto = (minOf(w, h).toLong() * ladoMax / largo.toDouble()).roundToInt().coerceAtLeast(1)
        return if (w >= h) Dimensiones(ladoMax, corto) else Dimensiones(corto, ladoMax)
    }

    private fun validarRotacion(rotacionGrados: Int) {
        require(rotacionGrados in ROTACIONES) { "Rotación no admitida: $rotacionGrados" }
    }

    private companion object {
        val ROTACIONES = setOf(0, 90, 180, 270)
    }
}
