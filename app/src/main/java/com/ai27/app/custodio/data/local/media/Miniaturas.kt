package com.ai27.app.custodio.data.local.media

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

object Miniaturas {
    const val DIMENSION_MAX_PX = 320
    const val CALIDAD_ORIGINAL = 88
    const val CALIDAD_MINIATURA = 75

    fun fotoOriginalYJpeg(bitmap: Bitmap): Pair<ByteArray, ByteArray> {
        val jpegOriginal = jpeg(bitmap, CALIDAD_ORIGINAL)
        val miniatura = jpeg(escalar(bitmap, DIMENSION_MAX_PX), CALIDAD_MINIATURA)
        return jpegOriginal to miniatura
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

    private fun jpeg(bitmap: Bitmap, calidad: Int): ByteArray {
        val salida = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, calidad, salida)
        salida.flush()
        return salida.toByteArray()
    }
}