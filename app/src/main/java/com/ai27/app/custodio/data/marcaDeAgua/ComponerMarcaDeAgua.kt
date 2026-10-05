package com.ai27.app.custodio.data.marcaDeAgua

import com.ai27.app.custodio.data.mapa.ProveedorSnapshotMapa
import com.ai27.app.custodio.domain.marcaDeAgua.FormatoMarcaDeAgua
import com.ai27.app.custodio.domain.model.Gps
import java.time.Instant
import java.time.ZoneId

/**
 * Orquesta la composición de la marca de agua sobre una foto. Ata las tres piezas —formateo
 * puro ([FormatoMarcaDeAgua]), snapshot de mapa ([ProveedorSnapshotMapa]) y dibujo
 * ([EstampadorFoto])— y resuelve los tres casos del requisito:
 *
 * - **Sin GPS**: solo fecha; no se pide snapshot (no hay punto que mapear).
 * - **Con GPS, sin mapa**: fecha + coords; el snapshot volvió `null` (sin red/API key).
 * - **Con GPS y mapa**: fecha + coords + tile del mapa.
 *
 * No toca `Bitmap`: el mapa viaja como [ImagenMapa] opaca. Eso lo hace probable en JVM, que
 * es donde vive la lógica de decisión que importa aquí.
 *
 * [zona] se inyecta (por defecto la del dispositivo) para fijar la hora local en pruebas;
 * [ahora] permite congelar el instante de captura.
 */
class ComponerMarcaDeAgua(
    private val estampador: EstampadorFoto,
    private val proveedorSnapshotMapa: ProveedorSnapshotMapa,
    private val zona: ZoneId = ZoneId.systemDefault(),
) {
    /**
     * @param jpegLimpio bytes del JPEG original (sin marca).
     * @param instante momento de captura; su hora local va en la marca.
     * @param gps ubicación de la captura, o `null` si no hubo fix.
     * @return bytes del JPEG estampado.
     */
    suspend operator fun invoke(jpegLimpio: ByteArray, instante: Instant, gps: Gps?): ByteArray {
        val marca = FormatoMarcaDeAgua.de(instante, gps, zona)

        // Solo se pide el mapa cuando hay coordenadas; sin GPS no hay punto que mapear.
        val mapa = if (gps != null) {
            val lado = estampador.ladoMapaPx(jpegLimpio)
            proveedorSnapshotMapa.snapshot(gps, lado)
        } else {
            null
        }

        return estampador.estampar(jpegLimpio, marca, mapa)
    }
}
