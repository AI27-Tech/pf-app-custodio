package com.ai27.app.custodio.data.foto

import com.ai27.app.custodio.FakeMedioRepository
import com.ai27.app.custodio.FakeRegistroDeFallos
import com.ai27.app.custodio.data.diagnostico.EventoFotoRevelada
import com.ai27.app.custodio.data.diagnostico.OrigenRevelado
import com.ai27.app.custodio.data.diagnostico.ResultadoFotoRevelada
import com.ai27.app.custodio.data.mapa.ProveedorSnapshotMapa
import com.ai27.app.custodio.data.marcaDeAgua.ImagenMapa
import com.ai27.app.custodio.domain.marcaDeAgua.DisposicionMarcaDeAgua
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Gps
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.OrigenMedio
import com.ai27.app.custodio.domain.model.TipoMedio
import com.ai27.app.custodio.domain.repository.MedioRepository
import java.io.IOException
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
        assertEquals("2026/01/05 09:03:07", codec.revelados.single().marca?.fechaHora)
        assertNull(codec.revelados.single().marca?.coordenadas)
        assertNull(codec.revelados.single().mapa)
    }

    // Requisito 3 de PLAN_MARCA_DE_AGUA: sin red o sin API key el proveedor devuelve null y la
    // foto se marca igual con fecha y coordenadas, sin el tile.
    @Test
    fun con_gps_y_snapshot_null_marca_coordenadas_sin_mapa() = runBlocking {
        val revelar = RevelarFoto(
            repo, codec, ProveedorFalso(resultado = null), fallos, ZoneOffset.UTC, Dispatchers.Default,
            reloj = { tiempos.removeAt(0) },
        )
        sembrar(foto())

        val revelado = revelar.revelar("foto-1")

        assertEquals(ResultadoRevelado.OK, revelado.resultado)
        assertTrue(revelado.conGps)
        assertFalse(revelado.conMapa)
        assertEquals("19.432608, -99.133209", codec.revelados.single().marca?.coordenadas)
        assertNull(codec.revelados.single().mapa)
        assertTrue(fallos.registrados.isEmpty())
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

    // revelar corre en corrutinas sin manejador de excepciones (ViewModel y worker): un error
    // de disco no puede tumbar la app.
    @Test
    fun un_error_al_leer_el_archivo_no_lanza_y_deja_la_cruda() = runBlocking {
        sembrar(foto())
        val repoQueFalla = object : MedioRepository by repo {
            override suspend fun leerOriginal(medioId: String): ByteArray? = throw IOException("disco")
        }
        val revelar = RevelarFoto(repoQueFalla, codec, proveedor, fallos, ZoneOffset.UTC, Dispatchers.Default)

        assertEquals(ResultadoRevelado.NO_APLICA, revelar.revelar("foto-1").resultado)
        assertFalse(repo.obtenerPorId("foto-1")!!.revelada)
        assertArrayEquals(crudo, repo.leerOriginal("foto-1"))
        assertEquals(listOf("revelar_foto_leer"), fallos.registrados.map { it.first })
    }

    @Test
    fun si_el_mapa_lanza_se_marca_igual_sin_mapa() = runBlocking {
        sembrar(foto())
        val proveedorQueFalla = object : ProveedorSnapshotMapa {
            override suspend fun snapshot(gps: Gps, ladoPx: Int): ImagenMapa? = throw IllegalStateException("maps")
        }
        val revelar = RevelarFoto(
            repo, codec, proveedorQueFalla, fallos, ZoneOffset.UTC, Dispatchers.Default,
            reloj = { tiempos.removeAt(0) },
        )

        val revelado = revelar.revelar("foto-1")

        assertEquals(ResultadoRevelado.OK, revelado.resultado)
        assertFalse(revelado.conMapa)
        assertNull(codec.revelados.single().mapa)
        assertEquals("19.432608, -99.133209", codec.revelados.single().marca?.coordenadas)
    }

    // region aEvento (D11)

    private val revelado = Revelado(ResultadoRevelado.OK, msRevelado = 456, conGps = true, conMapa = true, kbFinal = 210)

    @Test
    fun aEvento_ok_lleva_todos_los_campos() {
        assertEquals(
            EventoFotoRevelada(
                origen = OrigenRevelado.CAPTURA,
                resultado = ResultadoFotoRevelada.OK,
                msDisparo = 123,
                msRevelado = 456,
                conGps = true,
                conMapa = true,
                kbFinal = 210,
            ),
            revelado.aEvento(OrigenRevelado.CAPTURA, msDisparo = 123),
        )
    }

    @Test
    fun aEvento_sin_marca_desde_la_subida_va_sin_ms_disparo() {
        assertEquals(
            EventoFotoRevelada(
                origen = OrigenRevelado.SUBIDA,
                resultado = ResultadoFotoRevelada.SIN_MARCA,
                msDisparo = null,
                msRevelado = 456,
                conGps = true,
                conMapa = false,
                kbFinal = 210,
            ),
            revelado.copy(resultado = ResultadoRevelado.SIN_MARCA, conMapa = false)
                .aEvento(OrigenRevelado.SUBIDA, msDisparo = null),
        )
    }

    @Test
    fun aEvento_fallo_decode_lleva_todos_los_campos() {
        assertEquals(
            EventoFotoRevelada(
                origen = OrigenRevelado.CAPTURA,
                resultado = ResultadoFotoRevelada.FALLO_DECODE,
                msDisparo = 123,
                msRevelado = 456,
                conGps = false,
                conMapa = false,
                kbFinal = 210,
            ),
            revelado.copy(resultado = ResultadoRevelado.FALLO_DECODE, conGps = false, conMapa = false)
                .aEvento(OrigenRevelado.CAPTURA, msDisparo = 123),
        )
    }

    @Test
    fun aEvento_ya_revelada_y_no_aplica_no_emiten() {
        assertNull(revelado.copy(resultado = ResultadoRevelado.YA_REVELADA).aEvento(OrigenRevelado.CAPTURA, 123))
        assertNull(revelado.copy(resultado = ResultadoRevelado.NO_APLICA).aEvento(OrigenRevelado.SUBIDA, null))
    }

    // endregion
}
