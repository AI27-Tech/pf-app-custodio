package com.ai27.app.custodio.data.marcaDeAgua

import com.ai27.app.custodio.domain.marcaDeAgua.MarcaDeAgua

/**
 * Dibuja la marca sobre el JPEG y lo recomprime. Es la frontera Android del estampado: oculta
 * `Bitmap`/`Canvas` detrás de bytes de entrada y de salida, para que [ComponerMarcaDeAgua]
 * —que orquesta el flujo— se pueda probar en JVM con un doble sin tocar gráficos.
 *
 * El mapa se pasa como [ImagenMapa] (un envoltorio opaco del `Bitmap`) y no como `Bitmap`
 * crudo por la misma razón: el orquestador maneja el tipo opaco, no el de Android.
 */
interface EstampadorFoto {
    /**
     * @param jpegLimpio bytes del JPEG original sin marca.
     * @param marca textos ya formateados (fecha siempre; coords si hubo GPS).
     * @param mapa snapshot del mapa, o `null` para omitir el tile (requisito 3).
     * @return bytes de un JPEG nuevo con la marca grabada.
     */
    fun estampar(jpegLimpio: ByteArray, marca: MarcaDeAgua, mapa: ImagenMapa?): ByteArray

    /**
     * Lado (cuadrado) en píxeles del tile de mapa adecuado para [jpegLimpio], derivado de las
     * dimensiones reales de la foto. Lo expone el estampador porque decodificar el JPEG para
     * medirlo es trabajo de Android; el orquestador solo necesita el número para pedir el
     * snapshot del tamaño correcto.
     */
    fun ladoMapaPx(jpegLimpio: ByteArray): Int
}

/**
 * Envoltorio opaco de un `Bitmap` de mapa. Deja que [ComponerMarcaDeAgua] mueva el snapshot
 * del proveedor al estampador sin que aparezca `android.graphics.Bitmap` en código testeable
 * en JVM.
 */
interface ImagenMapa
