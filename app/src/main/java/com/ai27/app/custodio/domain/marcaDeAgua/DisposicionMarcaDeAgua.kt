package com.ai27.app.custodio.domain.marcaDeAgua

/**
 * Geometría de la marca de agua, derivada del tamaño del bitmap. Es puro cálculo (sin
 * Android) para poder probar en JVM que las medidas escalan con la foto y que el tile del
 * mapa es cuadrado y proporcional, sin depender de un `Canvas`.
 *
 * Todas las medidas van en píxeles del bitmap destino. El origen (0,0) es la esquina
 * superior izquierda, como en `Canvas`.
 */
object DisposicionMarcaDeAgua {

    // El texto escala con el lado menor del bitmap para verse igual en 1920×1080 que en una
    // foto más chica. 2.6% del lado menor da ~28 px en 1080 de alto, legible sin tapar.
    private const val FRACCION_TEXTO = 0.026f
    private const val TEXTO_MIN_PX = 18f

    // Margen de las esquinas, relativo al lado menor.
    private const val FRACCION_MARGEN = 0.02f
    private const val MARGEN_MIN_PX = 12f

    // Lado del tile del mapa, relativo al lado menor del bitmap. 22% deja un recuadro útil
    // sin comerse la foto.
    private const val FRACCION_MAPA = 0.22f
    private const val MAPA_MIN_PX = 96

    fun tamanoTextoPx(anchoBitmap: Int, altoBitmap: Int): Float =
        (minOf(anchoBitmap, altoBitmap) * FRACCION_TEXTO).coerceAtLeast(TEXTO_MIN_PX)

    fun margenPx(anchoBitmap: Int, altoBitmap: Int): Float =
        (minOf(anchoBitmap, altoBitmap) * FRACCION_MARGEN).coerceAtLeast(MARGEN_MIN_PX)

    /** Lado (cuadrado) del tile del mapa en píxeles. */
    fun ladoMapaPx(anchoBitmap: Int, altoBitmap: Int): Int =
        (minOf(anchoBitmap, altoBitmap) * FRACCION_MAPA).toInt().coerceAtLeast(MAPA_MIN_PX)
}
