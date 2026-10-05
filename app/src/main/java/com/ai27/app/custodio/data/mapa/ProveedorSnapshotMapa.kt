package com.ai27.app.custodio.data.mapa

import com.ai27.app.custodio.data.marcaDeAgua.ImagenMapa
import com.ai27.app.custodio.domain.model.Gps

/**
 * Produce un snapshot (imagen estática) de un mapa centrado en un punto, para incrustarlo
 * en la marca de agua de una foto.
 *
 * Devuelve `null` cuando no se pudo obtener el mapa: sin API key, sin red para los tiles,
 * timeout, o un snapshot en blanco. Ese `null` NO es un error que se propague: el requisito
 * 3 pide que, cuando el mapa falta, la foto se grabe igual con fecha+coords, omitiendo solo
 * el tile. Por eso el contrato es `ImagenMapa?` y no `Result`.
 *
 * El tipo de retorno es [ImagenMapa] (envoltorio opaco) y no `Bitmap` para que
 * [com.ai27.app.custodio.data.marcaDeAgua.ComponerMarcaDeAgua] se pruebe en JVM sin gráficos.
 */
interface ProveedorSnapshotMapa {
    /**
     * @param gps punto a centrar en el mapa.
     * @param ladoPx lado (cuadrado) deseado del snapshot en píxeles.
     * @return el mapa envuelto, o `null` si no se pudo producir.
     */
    suspend fun snapshot(gps: Gps, ladoPx: Int): ImagenMapa?
}
