package com.ai27.app.custodio.data.local.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.ai27.app.custodio.domain.marcaDeAgua.DisposicionMarcaDeAgua
import com.ai27.app.custodio.domain.marcaDeAgua.MarcaDeAgua
import java.io.ByteArrayOutputStream

/**
 * Graba la marca de agua sobre el pixel de una foto y la recomprime a JPEG. Es la capa
 * Android de la marca: el QUÉ (textos) y la geometría ya vienen resueltos
 * ([MarcaDeAgua] y [DisposicionMarcaDeAgua]); aquí solo se dibuja.
 *
 * Distribución pedida por producto:
 * - Fecha/hora: esquina superior izquierda.
 * - Coordenadas: esquina inferior derecha (se omiten si [MarcaDeAgua.coordenadas] es null).
 * - Mapa: esquina inferior izquierda (se omite si [mapa] es null — requisito 3).
 *
 * El [mapa] se pasa aparte y no dentro de [MarcaDeAgua] porque es un `Bitmap` (Android),
 * mientras que el modelo de la marca es dominio puro.
 */
object DibujanteMarcaDeAgua {

    private const val COLOR_TINTA = Color.WHITE
    private const val ALFA_FONDO = 150 // sobre 255: suficiente para leer el texto sin ocultar la foto
    private const val GROSOR_BORDE_MAPA_PX = 2f

    /**
     * Devuelve un JPEG nuevo con la marca grabada. [calidadJpeg] es la misma del original
     * (`Miniaturas.CALIDAD_ORIGINAL`) para no degradar la evidencia más de lo necesario.
     *
     * No muta [original]: dibuja sobre una copia mutable, así quien tenga el bitmap de
     * entrada (p. ej. una vista previa) no ve aparecer la marca encima.
     */
    fun estampar(
        original: Bitmap,
        marca: MarcaDeAgua,
        mapa: Bitmap?,
        calidadJpeg: Int = Miniaturas.CALIDAD_ORIGINAL,
    ): ByteArray {
        val lienzoBitmap = original.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(lienzoBitmap)
        val ancho = lienzoBitmap.width
        val alto = lienzoBitmap.height

        val margen = DisposicionMarcaDeAgua.margenPx(ancho, alto)
        val tamanoTexto = DisposicionMarcaDeAgua.tamanoTextoPx(ancho, alto)

        val pincelTexto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TINTA
            textSize = tamanoTexto
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val pincelFondo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = ALFA_FONDO
        }

        // Fecha/hora, arriba-izquierda.
        dibujarTextoConFondo(
            canvas = canvas,
            texto = marca.fechaHora,
            pincelTexto = pincelTexto,
            pincelFondo = pincelFondo,
            x = margen,
            yBaseline = margen + tamanoTexto,
        )

        // Coordenadas, abajo-derecha (si existen).
        marca.coordenadas?.let { coords ->
            val anchoTexto = pincelTexto.measureText(coords)
            dibujarTextoConFondo(
                canvas = canvas,
                texto = coords,
                pincelTexto = pincelTexto,
                pincelFondo = pincelFondo,
                x = ancho - margen - anchoTexto,
                yBaseline = alto - margen,
            )
        }

        // Mapa, abajo-izquierda (si llegó snapshot).
        if (mapa != null) {
            val lado = DisposicionMarcaDeAgua.ladoMapaPx(ancho, alto)
            val destino = RectF(
                margen,
                alto - margen - lado,
                margen + lado,
                alto - margen,
            )
            canvas.drawBitmap(mapa, null, destino, Paint(Paint.FILTER_BITMAP_FLAG))
            val pincelBorde = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = GROSOR_BORDE_MAPA_PX
                color = COLOR_TINTA
            }
            canvas.drawRect(destino, pincelBorde)
        }

        val salida = ByteArrayOutputStream()
        lienzoBitmap.compress(Bitmap.CompressFormat.JPEG, calidadJpeg, salida)
        salida.flush()
        return salida.toByteArray()
    }

    /**
     * Dibuja [texto] con un rectángulo semitransparente detrás, para que sea legible sobre
     * cualquier fondo de la foto. [x]/[yBaseline] son la posición del texto (yBaseline es la
     * línea base, como pide `Canvas.drawText`).
     */
    private fun dibujarTextoConFondo(
        canvas: Canvas,
        texto: String,
        pincelTexto: Paint,
        pincelFondo: Paint,
        x: Float,
        yBaseline: Float,
    ) {
        val limites = Rect()
        pincelTexto.getTextBounds(texto, 0, texto.length, limites)
        val padding = pincelTexto.textSize * 0.2f
        val fondo = RectF(
            x - padding,
            yBaseline + limites.top - padding,
            x + limites.width() + padding,
            yBaseline + padding,
        )
        canvas.drawRect(fondo, pincelFondo)
        canvas.drawText(texto, x, yBaseline, pincelTexto)
    }
}
