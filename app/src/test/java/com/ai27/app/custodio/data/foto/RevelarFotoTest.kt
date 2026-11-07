package com.ai27.app.custodio.data.foto

import com.ai27.app.custodio.FakeMedioRepository
import com.ai27.app.custodio.FakeRegistroDeFallos
import com.ai27.app.custodio.data.mapa.ProveedorSnapshotMapa
import com.ai27.app.custodio.data.marcaDeAgua.ImagenMapa
import com.ai27.app.custodio.domain.marcaDeAgua.DisposicionMarcaDeAgua
import com.ai27.app.custodio.domain.marcaDeAgua.MarcaDeAgua
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Gps
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.OrigenMedio
import com.ai27.app.custodio.domain.model.TipoMedio
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RevelarFotoTest {

    private val crudo = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3)
    private val gps = Gps(lat = 19.432608, lng = -99.133209, accuracyM = 5.0, ageMs = 0L)

    private class MapaFalso : ImagenMapa

    /** Registra cada llamada y el hilo donde ocurrió; falla a demanda. */
    private class CodecFotoFalso(
        var dimensiones: Dimensiones? = Dimensiones(960, 1280),
        var fallarConMarca: Boolean = false,
        var fallarSinMarca: Boolean = false,
        var demoraMs: Long = 0,
    ) : CodecFoto {
        data class LlamadaRevelar(val rotacion: Int, val marca: MarcaDeAgua?, val mapa: ImagenMapa?)

        val revelados = mutableListOf<LlamadaRevelar>()
        val hilos = mutableListOf<Thread>()
        var llamadas = 0

        override fun dimensionesFinales(crudo: ByteArray, rotacionGrados: Int): Dimensiones? {
            registrar()
            return dimensiones
        }

        override fun revelar(crudo: ByteArray, rotacionGrados: Int, marca: MarcaDeAgua?, mapa: ImagenMapa?): ByteArray {
            registrar()
            revelados.add(LlamadaRevelar(rotacionGrados, marca, mapa))
            if (demoraMs > 0) Thread.sleep(demoraMs)
            if (marca != null && fallarConMarca) throw OutOfMemoryError("marca")
            if (marca == null && fallarSinMarca) throw IllegalStateException("sin marca")
            return if (marca != null) REVELADA else SIN_MARCA
        }

        override fun miniatura(crudo: ByteArray, rotacionGrados: Int): ByteArray {
            registrar()
            return MINIATURA
        }

        override fun conOrientacionExif(crudo: ByteArray, rotacionGrados: Int): ByteArray {
            registrar()
            return CON_EXIF
        }

        private fun registrar() {
            llamadas++
            hilos.add(Thread.currentThread())
        }

        companion object {
            val REVELADA = ByteArray(2048) { 7 }
            val SIN_MARCA = ByteArray(1024) { 8 }
            val MINIATURA = byteArrayOf(9, 9)
            val CON_EXIF = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x45)
        }
    }

    private class ProveedorFalso(private val resultado: ImagenMapa?) : ProveedorSnapshotMapa {
        val ladosPedidos = mutableListOf<Int>()
        override suspend fun snapshot(gps: Gps, ladoPx: Int): ImagenMapa? {
            ladosPedidos.add(ladoPx)
            return resultado
        }
    }

    private val repo = FakeMedioRepository()
    private val fallos = FakeRegistroDeFallos()
    private val mapa = MapaFalso()
    private val proveedor = ProveedorFalso(mapa)
    private val codec = CodecFotoFalso()
    private val tiempos = mutableListOf(0L, 7_000_000L)

    private val revelarFoto = RevelarFoto(
        medioRepository = repo,
        codec = codec,
        proveedorSnapshotMapa = proveedor,
        registroDeFallos = fallos,
        zona = ZoneOffset.UTC,
        despachador = Dispatchers.Default,
        reloj = { tiempos.removeAt(0) },
    )

    private fun foto(
        id: String = "foto-1",
        gps: Gps? = this.gps,
        revelada: Boolean = false,
        tipo: TipoMedio = TipoMedio.FOTO,
    ) = Medio(
        id = id,
        reporteId = "reporte-1",
        tipo = tipo,
        origen = if (tipo == TipoMedio.FOTO) OrigenMedio.CAMARA else OrigenMedio.GRABADORA,
        createdAtDevice = Instant.parse("2026-01-05T09:03:07Z"),
        contentType = if (tipo == TipoMedio.FOTO) "image/jpeg" else "audio/mp4",
        estadoSubida = EstadoSubida.PENDIENTE,
        gps = gps,
        revelada = revelada,
        rotacionGrados = 90,
    )

    private fun sembrar(medio: Medio) = runBlocking { repo.guardar(medio, crudo) }

    @Test
    fun audio_no_aplica() = runBlocking {
        sembrar(foto(tipo = TipoMedio.AUDIO))

        assertEquals(ResultadoRevelado.NO_APLICA, revelarFoto.revelar("foto-1").resultado)
        assertEquals(0, codec.llamadas)
    }

    @Test
    fun medio_inexistente_no_aplica() = runBlocking {
        assertEquals(ResultadoRevelado.NO_APLICA, revelarFoto.revelar("no-existe").resultado)
        assertEquals(0, codec.llamadas)
    }

    @Test
    fun ya_revelada_no_llama_al_codec() = runBlocking {
        sembrar(foto(revelada = true))

        assertEquals(ResultadoRevelado.YA_REVELADA, revelarFoto.revelar("foto-1").resultado)
        assertEquals(0, codec.llamadas)
        assertArrayEquals(crudo, repo.leerOriginal("foto-1"))
    }

    @Test
    fun con_gps_y_mapa_revela_con_marca_y_mapa() = runBlocking {
        sembrar(foto())

        val revelado = revelarFoto.revelar("foto-1")

        assertEquals(
            Revelado(ResultadoRevelado.OK, msRevelado = 7, conGps = true, conMapa = true, kbFinal = 2),
            revelado,
        )
        val llamada = codec.revelados.single()
        assertEquals(90, llamada.rotacion)
        assertEquals("19.432608, -99.133209", llamada.marca?.coordenadas)
        assertEquals("2026/01/05 09:03:07", llamada.marca?.fechaHora)
        assertSame(mapa, llamada.mapa)
        assertEquals(listOf(DisposicionMarcaDeAgua.ladoMapaPx(960, 1280)), proveedor.ladosPedidos)
        assertEquals(listOf(211), proveedor.ladosPedidos)
        val guardado = repo.obtenerPorId("foto-1")!!
        assertTrue(guardado.revelada)
        assertEquals("image/webp", guardado.contentType)
        assertArrayEquals(CodecFotoFalso.REVELADA, repo.leerOriginal("foto-1"))
    }

    @Test
    fun sin_gps_no_pide_mapa() = runBlocking {
        sembrar(foto(gps = null))

        val revelado = revelarFoto.revelar("foto-1")

        assertEquals(ResultadoRevelado.OK, revelado.resultado)
        assertFalse(revelado.conGps)
        assertFalse(revelado.conMapa)
        assertEquals(emptyList<Int>(), proveedor.ladosPedidos)
        assertNull(codec.revelados.single().marca?.coordenadas)
        assertNull(codec.revelados.single().mapa)
    }

    @Test
    fun con_mapa_false_no_pide_mapa_pero_marca_coordenadas() = runBlocking {
        sembrar(foto())

        val revelado = revelarFoto.revelar("foto-1", conMapa = false)

        assertEquals(ResultadoRevelado.OK, revelado.resultado)
        assertTrue(revelado.conGps)
        assertFalse(revelado.conMapa)
        assertEquals(emptyList<Int>(), proveedor.ladosPedidos)
        assertEquals("19.432608, -99.133209", codec.revelados.single().marca?.coordenadas)
        assertNull(codec.revelados.single().mapa)
    }

    @Test
    fun si_falla_la_marca_revela_sin_marca() = runBlocking {
        codec.fallarConMarca = true
        sembrar(foto())

        val revelado = revelarFoto.revelar("foto-1")

        assertEquals(ResultadoRevelado.SIN_MARCA, revelado.resultado)
        assertFalse(revelado.conMapa)
        assertEquals(1, revelado.kbFinal)
        assertEquals(2, codec.revelados.size)
        assertNull(codec.revelados[1].marca)
        assertNull(codec.revelados[1].mapa)
        assertEquals("image/webp", repo.obtenerPorId("foto-1")!!.contentType)
        assertArrayEquals(CodecFotoFalso.SIN_MARCA, repo.leerOriginal("foto-1"))
        assertEquals(listOf("revelar_foto_marca"), fallos.registrados.map { it.first })
    }

    @Test
    fun si_no_decodifica_guarda_la_cruda_con_exif_como_jpeg() = runBlocking {
        codec.dimensiones = null
        sembrar(foto())

        val revelado = revelarFoto.revelar("foto-1")

        assertEquals(ResultadoRevelado.FALLO_DECODE, revelado.resultado)
        assertEquals(0, codec.revelados.size)
        val guardado = repo.obtenerPorId("foto-1")!!
        assertTrue(guardado.revelada)
        assertEquals("image/jpeg", guardado.contentType)
        assertArrayEquals(CodecFotoFalso.CON_EXIF, repo.leerOriginal("foto-1"))
        assertEquals(listOf("revelar_foto_decode"), fallos.registrados.map { it.first })
    }

    @Test
    fun si_fallan_las_dos_vueltas_guarda_la_cruda_con_exif() = runBlocking {
        codec.fallarConMarca = true
        codec.fallarSinMarca = true
        sembrar(foto())

        val revelado = revelarFoto.revelar("foto-1")

        assertEquals(ResultadoRevelado.FALLO_DECODE, revelado.resultado)
        assertEquals("image/jpeg", repo.obtenerPorId("foto-1")!!.contentType)
        assertArrayEquals(CodecFotoFalso.CON_EXIF, repo.leerOriginal("foto-1"))
    }

    @Test
    fun si_el_medio_salio_de_pendiente_no_aplica_y_no_cambia_el_archivo() = runBlocking {
        sembrar(foto())
        repo.actualizarEstadoSubida("foto-1", EstadoSubida.EN_PROGRESO)

        val revelado = revelarFoto.revelar("foto-1")

        assertEquals(ResultadoRevelado.NO_APLICA, revelado.resultado)
        assertArrayEquals(crudo, repo.leerOriginal("foto-1"))
        assertFalse(repo.obtenerPorId("foto-1")!!.revelada)
        assertEquals(listOf("revelar_foto_guardar"), fallos.registrados.map { it.first })
    }

    @Test
    fun el_codec_no_corre_en_el_hilo_del_llamador() = runBlocking {
        sembrar(foto())
        val hiloDePrueba = Thread.currentThread()

        revelarFoto.revelar("foto-1")

        assertEquals(2, codec.hilos.size)
        codec.hilos.forEach { assertNotEquals(hiloDePrueba, it) }
    }

    @Test
    fun miniatura_rapida_devuelve_lo_del_codec_fuera_del_hilo_del_llamador() = runBlocking {
        val hiloDePrueba = Thread.currentThread()

        val miniatura = revelarFoto.miniaturaRapida(crudo, 90)

        assertArrayEquals(CodecFotoFalso.MINIATURA, miniatura)
        assertNotEquals(hiloDePrueba, codec.hilos.single())
    }

    // La cámara y el worker pueden revelar la misma foto a la vez (Tasks 5 y 6): solo uno debe
    // decodificar y marcar; el otro ve la foto ya revelada.
    @Test
    fun dos_revelados_simultaneos_de_la_misma_foto_marcan_una_sola_vez() = runBlocking {
        sembrar(foto())
        codec.demoraMs = 200

        val resultados = listOf(
            async(Dispatchers.Default) { revelarFoto.revelar("foto-1").resultado },
            async(Dispatchers.Default) { revelarFoto.revelar("foto-1").resultado },
        ).awaitAll()

        assertEquals(
            listOf(ResultadoRevelado.OK, ResultadoRevelado.YA_REVELADA),
            resultados.sortedBy { it.ordinal },
        )
        assertEquals(1, codec.revelados.size)
        assertArrayEquals(CodecFotoFalso.REVELADA, repo.leerOriginal("foto-1"))
    }
}
