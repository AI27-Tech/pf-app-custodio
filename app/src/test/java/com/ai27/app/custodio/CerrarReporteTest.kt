package com.ai27.app.custodio

import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
import com.ai27.app.custodio.domain.model.Ciclo
import com.ai27.app.custodio.domain.model.Custodio
import com.ai27.app.custodio.domain.model.OrigenMedio
import com.ai27.app.custodio.domain.model.TipoMedio
import com.ai27.app.custodio.domain.usecase.CapturarMedio
import com.ai27.app.custodio.domain.usecase.CerrarReporte
import com.ai27.app.custodio.domain.usecase.CrearReporte
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CerrarReporteTest {

    private val custodio = Custodio(uid = "c-1", correo = "custodio@test.mx")
    private val fijo = Instant.parse("2026-09-18T15:00:00Z")
    private lateinit var repoReportes: FakeReporteRepository
    private lateinit var repoMedios: FakeMedioRepository
    private lateinit var reporteId: String

    @Before
    fun setUp() {
        runBlocking {
            repoMedios = FakeMedioRepository()
            repoReportes = FakeReporteRepository(mediosConsultables = repoMedios)
            val crear = CrearReporte(repoReportes, ahora = { fijo })
            val capturar = CapturarMedio(repoReportes, repoMedios, ahora = { fijo })
            val reporte = (crear(custodio, "d1", "c1", "ABC-1234") as Result.Success).value
            reporteId = reporte.id
            capturar("c-1", reporteId, TipoMedio.FOTO, OrigenMedio.CAMARA, ByteArray(1))
            capturar("c-1", reporteId, TipoMedio.FOTO, OrigenMedio.CAMARA, ByteArray(1))
            capturar("c-1", reporteId, TipoMedio.AUDIO, OrigenMedio.GRABADORA, ByteArray(1), durationMs = 60_000)
        }
    }

    @Test
    fun `cierra reporte agregando el resumen de medios`() = runBlocking {
        val resultado = CerrarReporte(repoReportes, repoMedios, ahora = { fijo })(reporteId)

        assertTrue(resultado is Result.Success)
        val cerrado = (resultado as Result.Success).value
        assertEquals(Ciclo.CERRADO, cerrado.ciclo)
        assertEquals(3, cerrado.mediosEsperados)
        // Cerrar fija mediosEsperados pero NO avanza mediosSubidos: los tres medios
        // siguen PENDIENTE y la evidencia puede tardar horas en salir. Quien sube el
        // contador es el pipeline, vía actualizarSubida(). Esta aserción decía 3 porque
        // el fake inflaba el contador; ReporteDao.cerrar nunca tocó esa columna (P2/D3).
        assertEquals(0, cerrado.mediosSubidos)
        assertFalse(cerrado.completo)
        assertEquals(2, cerrado.resumenMedios.fotos)
        assertEquals(1, cerrado.resumenMedios.audios)
        assertEquals(60_000L, cerrado.resumenMedios.durationMsTotal)
        assertEquals(fijo, cerrado.closedAt)
    }

    @Test
    fun `no cierra reporte sin medios`() = runBlocking {
        repoReportes = FakeReporteRepository()
        val crear = CrearReporte(repoReportes, ahora = { fijo })
        val vacioId = (crear(custodio, "d1", "c1", "XYZ-7777") as Result.Success).value.id

        val resultado = CerrarReporte(repoReportes, repoMedios, ahora = { fijo })(vacioId)

        assertTrue(resultado is Result.Failure)
        assertEquals(DominioError.ReporteSinMedios, (resultado as Result.Failure).error)
    }

    @Test
    fun `no permite cerrar un reporte ya cerrado`() = runBlocking {
        val cerrar = CerrarReporte(repoReportes, repoMedios, ahora = { fijo })
        cerrar(reporteId)

        val resultado = cerrar(reporteId)

        assertTrue(resultado is Result.Failure)
        assertEquals(DominioError.ReporteCerradoInmutable, (resultado as Result.Failure).error)
    }

    @Test
    fun `no cierra un reporte inexistente`() = runBlocking {
        val resultado = CerrarReporte(repoReportes, repoMedios, ahora = { fijo })("no-existe")

        assertTrue(resultado is Result.Failure)
        assertEquals(DominioError.ReporteNoEncontrado, (resultado as Result.Failure).error)
    }
}