package com.ai27.app.custodio

import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
import com.ai27.app.custodio.domain.model.Ciclo
import com.ai27.app.custodio.domain.model.Custodio
import com.ai27.app.custodio.domain.model.Gps
import com.ai27.app.custodio.domain.model.Limites
import com.ai27.app.custodio.domain.model.OrigenMedio
import com.ai27.app.custodio.domain.model.Reporte
import com.ai27.app.custodio.domain.model.TipoMedio
import com.ai27.app.custodio.domain.usecase.CapturarMedio
import com.ai27.app.custodio.domain.usecase.CrearReporte
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CapturarMedioTest {

    private val custodio = Custodio(uid = "c-1", correo = "custodio@ai27.com")
    private val fijo = Instant.parse("2026-09-18T15:00:00Z")
    private lateinit var repoReportes: FakeReporteRepository
    private lateinit var repoMedios: FakeMedioRepository
    private lateinit var reporteActivo: Reporte

    @Before
    fun setUp() = runBlocking {
        repoReportes = FakeReporteRepository()
        repoMedios = FakeMedioRepository()
        val crear = CrearReporte(repoReportes, ahora = { fijo })
        reporteActivo = (crear(custodio, "d1", "c1", "ABC-1234") as Result.Success).value
    }

    private fun caso() = CapturarMedio(repoReportes, repoMedios, ahora = { fijo })

    @Test
    fun `guarda foto cruda pendiente con ruta local, rotacion y gps`() = runBlocking {
        val resultado = caso()(
            custodioUid = "c-1",
            reporteId = reporteActivo.id,
            tipo = TipoMedio.FOTO,
            origen = OrigenMedio.CAMARA,
            contenido = ByteArray(2048),
            gps = Gps(19.43, -99.13, accuracyM = 5.0, ageMs = 300),
            rotacionGrados = 90,
        )

        assertTrue(resultado is Result.Success)
        val medio = (resultado as Result.Success).value
        assertEquals(TipoMedio.FOTO, medio.tipo)
        // Task 4 de PLAN_REVELAR_FOTO: la foto se persiste cruda (JPEG del HAL) y sin revelar;
        // RevelarFoto la convierte después en WebP.
        assertEquals("image/jpeg", medio.contentType)
        assertFalse(medio.revelada)
        assertEquals(90, medio.rotacionGrados)
        assertEquals(1, repoMedios.medios.size)
        assertEquals(2048L, medio.sizeBytes)
        assertTrue(medio.localPath!!.startsWith("fisico/"))
        assertEquals(19.43, medio.gps!!.lat, 0.001)
    }

    @Test
    fun `rechaza contenido vacio`() = runBlocking {
        val resultado = caso()(
            custodioUid = "c-1",
            reporteId = reporteActivo.id,
            tipo = TipoMedio.FOTO,
            origen = OrigenMedio.CAMARA,
            contenido = ByteArray(0),
        )

        assertTrue(resultado is Result.Failure)
        assertEquals(DominioError.SinContenido, (resultado as Result.Failure).error)
    }

    @Test
    fun `rechaza captura cuando no hay reporte`() = runBlocking {
        val resultado = caso()(
            custodioUid = "c-1",
            reporteId = "no-existe",
            tipo = TipoMedio.FOTO,
            origen = OrigenMedio.CAMARA,
            contenido = ByteArray(10),
        )

        assertTrue(resultado is Result.Failure)
        assertEquals(DominioError.ReporteNoEncontrado, (resultado as Result.Failure).error)
    }

    @Test
    fun `rechaza captura en reporte cerrado`() = runBlocking {
        val cerrar = com.ai27.app.custodio.domain.usecase.CerrarReporte(repoReportes, repoMedios, ahora = { fijo })
        val foto = caso()(
            custodioUid = "c-1",
            reporteId = reporteActivo.id,
            tipo = TipoMedio.FOTO,
            origen = OrigenMedio.CAMARA,
            contenido = ByteArray(10),
        )
        assertTrue((foto as Result.Success).value.id.isNotEmpty())
        cerrar(reporteActivo.id)

        val resultado = caso()(
            custodioUid = "c-1",
            reporteId = reporteActivo.id,
            tipo = TipoMedio.FOTO,
            origen = OrigenMedio.CAMARA,
            contenido = ByteArray(10),
        )

        assertTrue(resultado is Result.Failure)
        assertEquals(DominioError.ReporteCerradoInmutable, (resultado as Result.Failure).error)
    }

    @Test
    fun `rechaza audio de mas de cinco minutos`() = runBlocking {
        val resultado = caso()(
            custodioUid = "c-1",
            reporteId = reporteActivo.id,
            tipo = TipoMedio.AUDIO,
            origen = OrigenMedio.GRABADORA,
            contenido = ByteArray(100),
            durationMs = Limites.AUDIO_MAX_MS + 1,
        )

        assertTrue(resultado is Result.Failure)
        assertEquals(DominioError.AudioExcedeCincoMinutos, (resultado as Result.Failure).error)
    }

    @Test
    fun `rechaza captura sobre el limite de medios`() = runBlocking {
        val captura = caso()
        for (i in 1..Limites.MEDIOS_POR_REPORTE) {
            val r = captura("c-1", reporteActivo.id, TipoMedio.FOTO, OrigenMedio.CAMARA, ByteArray(1))
            assertTrue(r is Result.Success)
        }

        val resultado = captura("c-1", reporteActivo.id, TipoMedio.FOTO, OrigenMedio.CAMARA, ByteArray(1))

        assertTrue(resultado is Result.Failure)
        assertEquals(DominioError.LimiteDeMediosAlcanzado, (resultado as Result.Failure).error)
    }

    @Test
    fun `guarda audio con duracion`() = runBlocking {
        val resultado = caso()(
            custodioUid = "c-1",
            reporteId = reporteActivo.id,
            tipo = TipoMedio.AUDIO,
            origen = OrigenMedio.GRABADORA,
            contenido = ByteArray(4096),
            durationMs = 90_000,
        )

        assertTrue(resultado is Result.Success)
        val medio = (resultado as Result.Success).value
        assertEquals(TipoMedio.AUDIO, medio.tipo)
        assertEquals("audio/mp4", medio.contentType)
        assertEquals(90_000L, medio.durationMs)
        assertTrue("el audio nace revelado", medio.revelada)
        assertEquals(0, medio.rotacionGrados)
    }

    @Test
    fun `guarda miniatura cuando se pasa`() = runBlocking {
        val miniatura = ByteArray(512)
        val resultado = caso()(
            custodioUid = "c-1",
            reporteId = reporteActivo.id,
            tipo = TipoMedio.FOTO,
            origen = OrigenMedio.CAMARA,
            contenido = ByteArray(2048),
            miniatura = miniatura,
        )

        assertTrue(resultado is Result.Success)
        val medio = (resultado as Result.Success).value
        assertEquals("fisico/${medio.id}-min", medio.thumbnailPath)
        assertTrue(repoMedios.archivosGuardados[medio.thumbnailPath!!]!!.contentEquals(miniatura))
    }

    @Test
    fun `audio no lleva miniatura`() = runBlocking {
        val resultado = caso()(
            custodioUid = "c-1",
            reporteId = reporteActivo.id,
            tipo = TipoMedio.AUDIO,
            origen = OrigenMedio.GRABADORA,
            contenido = ByteArray(100),
            durationMs = 30_000,
        )

        assertTrue(resultado is Result.Success)
        assertEquals(null, (resultado as Result.Success).value.thumbnailPath)
    }
}