package com.ai27.app.custodio

import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
import com.ai27.app.custodio.domain.model.Ciclo
import com.ai27.app.custodio.domain.model.Custodio
import com.ai27.app.custodio.domain.usecase.CrearReporte
import com.ai27.app.custodio.domain.usecase.DescartarReporte
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DescartarReporteTest {

    private val custodio = Custodio(uid = "c-1", correo = "custodio@ai27.com")
    private val fijo = Instant.parse("2026-09-18T15:00:00Z")
    private lateinit var repoReportes: FakeReporteRepository
    private lateinit var reporteId: String

    @Before
    fun setUp() = runBlocking {
        repoReportes = FakeReporteRepository()
        val crear = CrearReporte(repoReportes, ahora = { fijo })
        reporteId = (crear(custodio, "d1", "c1", "ABC-1234") as Result.Success).value.id
    }

    @Test
    fun `descarta reporte activo con motivo`() = runBlocking {
        val resultado = DescartarReporte(repoReportes, ahora = { fijo })(reporteId, "Cliente no entregó mercancía")

        assertTrue(resultado is Result.Success)
        val descartado = (resultado as Result.Success).value
        assertEquals(Ciclo.DESCARTADO, descartado.ciclo)
        assertEquals("Cliente no entregó mercancía", descartado.motivoDescarte)
        assertEquals(fijo, descartado.closedAt)
    }

    @Test
    fun `libera el slot de reporte activo al descartar`() = runBlocking {
        DescartarReporte(repoReportes, ahora = { fijo })(reporteId, "motivo")

        val activo = repoReportes.obtenerActivo("c-1")
        assertEquals(null, activo)
    }

    @Test
    fun `rechaza motivo vacio`() = runBlocking {
        val resultado = DescartarReporte(repoReportes, ahora = { fijo })(reporteId, "   ")

        assertTrue(resultado is Result.Failure)
        assertEquals(DominioError.MotivoDescarteVacio, (resultado as Result.Failure).error)
    }

    @Test
    fun `no descarta dos veces`() = runBlocking {
        val descartar = DescartarReporte(repoReportes, ahora = { fijo })
        descartar(reporteId, "motivo")

        val resultado = descartar(reporteId, "motivo")

        assertTrue(resultado is Result.Failure)
        assertEquals(DominioError.ReporteYaDescartado, (resultado as Result.Failure).error)
    }
}