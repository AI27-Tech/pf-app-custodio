package com.ai27.app.custodio.data.local.media

import android.graphics.Bitmap
import android.os.Build
import java.io.ByteArrayOutputStream

object Miniaturas {
    const val DIMENSION_MAX_PX = 320

    // WebP-80 ≈ JPEG-88 en percepción con ~30% menos bytes; la miniatura baja a 70 porque
    // se ve a 320 px y el peso importa más que el detalle.
    const val CALIDAD_ORIGINAL = 80
    const val CALIDAD_MINIATURA = 70

    fun fotoOriginalYMiniatura(bitmap: Bitmap): Pair<ByteArray, ByteArray> {
        val original = webp(bitmap, CALIDAD_ORIGINAL)
        val miniatura = webp(escalar(bitmap, DIMENSION_MAX_PX), CALIDAD_MINIATURA)
        return original to miniatura
    }

    fun escalar(bitmap: Bitmap, dimensionMax: Int): Bitmap {
        val escala = dimensionMax.toFloat() / maxOf(bitmap.width, bitmap.height)
        if (escala >= 1f) {
            return bitmap
        }
        val w = (bitmap.width * escala).toInt().coerceAtLeast(1)
        val h = (bitmap.height * escala).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    /**
     * Comprime [bitmap] a WebP lossy. `WEBP_LOSSY` existe desde API 30; en API 26–29 se usa
     * el `WEBP` deprecado, que con calidad < 100 también es lossy.
     */
    fun webp(bitmap: Bitmap, calidad: Int): ByteArray {
        val formato = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        val salida = ByteArrayOutputStream()
        bitmap.compress(formato, calidad, salida)
        salida.flush()
        return salida.toByteArray()
    }

    /**
     * Detecta el formato de un buffer de imagen por sus magic bytes, sin decodificar. Función
     * pura para poder probar la codificación WebP en unit tests JVM (donde `Bitmap.compress`
     * no corre sin Robolectric).
     */
    fun esWebp(bytes: ByteArray): Boolean =
        bytes.size >= 12 &&
            bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" &&
            bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP"

    fun esJpeg(bytes: ByteArray): Boolean =
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
}
