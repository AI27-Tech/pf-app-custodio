package com.ai27.app.custodio

import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
import com.ai27.app.custodio.domain.model.Ciclo
import com.ai27.app.custodio.domain.model.Custodio
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.usecase.CrearReporte
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrearReporteTest {

    private val custodio = Custodio(uid = "c-1", correo = "custodio@ai27.com")
    private val fijo = Instant.parse("2026-09-18T15:00:00Z")

    @Test
    fun `crea reporte activo valido en primer intento`() = runBlocking {
        val repoReportes = FakeReporteRepository()
        val caso = CrearReporte(repoReportes, ahora = { fijo })

        val resultado = caso(
            custodio = custodio,
            deviceId = "device-1",
            clienteId = "cliente-1",
            placasCapturadas = "ABC-1234",
        )

        assertTrue(resultado is Result.Success)
        val reporte = (resultado as Result.Success).value
        assertEquals(Ciclo.ACTIVO, reporte.ciclo)
        assertEquals(EstadoSubida.PENDIENTE, reporte.subida)
        assertEquals("ABC1234", reporte.placas)
        assertEquals("ABC-1234", reporte.placasCapturadas)
        assertEquals("c-1", reporte.custodioUid)
        assertEquals(1, repoReportes.reportes.size)
    }

    @Test
    fun `rechaza placa vacia`() = runBlocking {
        val caso = CrearReporte(FakeReporteRepository(), ahora = { fijo })

        val resultado = caso(
            custodio = custodio,
            deviceId = "device-1",
            clienteId = "cliente-1",
            placasCapturadas = "   ",
        )

        assertTrue(resultado is Result.Failure)
        assertTrue((resultado as Result.Failure).error is DominioError.PlacaVacia)
    }

    @Test
    fun `normaliza minusculas y separadores de placa`() = runBlocking {
        val caso = CrearReporte(FakeReporteRepository(), ahora = { fijo })

        val resultado = caso(
            custodio = custodio,
            deviceId = "device-1",
            clienteId = "cliente-1",
            placasCapturadas = "abc-1234",
        )

        assertEquals("ABC1234", (resultado as Result.Success).value.placas)
    }

    @Test
    fun `rechaza segundo reporte activo del mismo custodio`() = runBlocking {
        val repoReportes = FakeReporteRepository()
        val caso = CrearReporte(repoReportes, ahora = { fijo })

        val primero = caso(
            custodio = custodio,
            deviceId = "device-1",
            clienteId = "cliente-1",
            placasCapturadas = "ABC-1234",
        )
        val segundo = caso(
            custodio = custodio,
            deviceId = "device-1",
            clienteId = "cliente-1",
            placasCapturadas = "XYZ-9999",
        )

        assertTrue(primero is Result.Success)
        assertTrue(segundo is Result.Failure)
        assertEquals(
            DominioError.YaExisteReporteActivo((primero as Result.Success).value.id),
            (segundo as Result.Failure).error,
        )
    }

    @Test
    fun `permite activo en paralelo para custodios distintos`() = runBlocking {
        val repoReportes = FakeReporteRepository()
        val caso = CrearReporte(repoReportes, ahora = { fijo })

        val a = caso(Custodio("a", "+525544880514"), "d1", "c1", "AAA-0001")
        val b = caso(Custodio("b", "+525544880515"), "d1", "c1", "BBB-0002")

        assertTrue(a is Result.Success)
        assertTrue(b is Result.Success)
    }
}