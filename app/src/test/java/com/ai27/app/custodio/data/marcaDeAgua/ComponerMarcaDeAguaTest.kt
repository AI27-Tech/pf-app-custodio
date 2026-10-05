package com.ai27.app.custodio.data.marcaDeAgua

import com.ai27.app.custodio.data.mapa.ProveedorSnapshotMapa
import com.ai27.app.custodio.domain.marcaDeAgua.MarcaDeAgua
import com.ai27.app.custodio.domain.model.Gps
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ComponerMarcaDeAguaTest {

    private val instante = Instant.parse("2026-01-05T09:03:07Z")
    private val gps = Gps(lat = 19.432608, lng = -99.133209, accuracyM = 5.0, ageMs = 0L)
    private val jpegLimpio = ByteArray(16) { it.toByte() }

    private class MapaFalso : ImagenMapa

    private class EstampadorEspia : EstampadorFoto {
        var marcaRecibida: MarcaDeAgua? = null
        var mapaRecibido: ImagenMapa? = null
        var veces = 0
        override fun estampar(jpegLimpio: ByteArray, marca: MarcaDeAgua, mapa: ImagenMapa?): ByteArray {
            marcaRecibida = marca
            mapaRecibido = mapa
            veces++
            return byteArrayOf(1, 2, 3)
        }
        override fun ladoMapaPx(jpegLimpio: ByteArray): Int = 240
    }

    private class ProveedorFalso(private val resultado: ImagenMapa?) : ProveedorSnapshotMapa {
        var veces = 0
        var ladoPedido = -1
        override suspend fun snapshot(gps: Gps, ladoPx: Int): ImagenMapa? {
            veces++
            ladoPedido = ladoPx
            return resultado
        }
    }

    @Test
    fun `sin GPS no pide snapshot y estampa solo la fecha`() = runBlocking {
        val estampador = EstampadorEspia()
        val proveedor = ProveedorFalso(MapaFalso())
        val componer = ComponerMarcaDeAgua(estampador, proveedor, ZoneOffset.UTC)

        componer(jpegLimpio, instante, gps = null)

        assertEquals("No debe pedirse el mapa sin GPS", 0, proveedor.veces)
        assertEquals("2026/01/05 09:03:07", estampador.marcaRecibida?.fechaHora)
        assertNull(estampador.marcaRecibida?.coordenadas)
        assertNull("Sin GPS no se pasa mapa al dibujante", estampador.mapaRecibido)
    }

    @Test
    fun `con GPS pero snapshot null estampa fecha y coords sin mapa`() = runBlocking {
        val estampador = EstampadorEspia()
        val proveedor = ProveedorFalso(resultado = null) // sin red / sin API key
        val componer = ComponerMarcaDeAgua(estampador, proveedor, ZoneOffset.UTC)

        componer(jpegLimpio, instante, gps = gps)

        assertEquals("Con GPS sí se pide el mapa", 1, proveedor.veces)
        assertEquals(240, proveedor.ladoPedido)
        assertEquals("19.432608, -99.133209", estampador.marcaRecibida?.coordenadas)
        assertNull("Snapshot null => sin tile de mapa", estampador.mapaRecibido)
    }

    @Test
    fun `con GPS y snapshot ok pasa el mapa al dibujante`() = runBlocking {
        val estampador = EstampadorEspia()
        val mapa = MapaFalso()
        val proveedor = ProveedorFalso(resultado = mapa)
        val componer = ComponerMarcaDeAgua(estampador, proveedor, ZoneOffset.UTC)

        val salida = componer(jpegLimpio, instante, gps = gps)

        assertEquals("19.432608, -99.133209", estampador.marcaRecibida?.coordenadas)
        assertSame("El snapshot debe llegar al dibujante tal cual", mapa, estampador.mapaRecibido)
        assertEquals(1, estampador.veces)
        assertTrue(salida.contentEquals(byteArrayOf(1, 2, 3)))
    }
}
