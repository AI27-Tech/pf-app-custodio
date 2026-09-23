package com.ai27.app.custodio.ui.historial

import com.ai27.app.custodio.domain.model.Ciclo
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Reporte
import com.ai27.app.custodio.domain.model.ResumenMedios
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistorialFiltroTest {

    private val fijo = Instant.parse("2026-09-18T15:00:00Z")

    private fun reporte(placas: String) = Reporte(
        id = placas.replace("-", ""),
        placas = placas,
        placasCapturadas = placas.uppercase(),
        custodioUid = "c-1",
        custodioTelefono = "a@b.cl",
        clienteId = "cli-tmp",
        deviceId = "dev-1",
        fechaLocal = "2026-09-18",
        createdAtDevice = fijo,
        ciclo = Ciclo.CERRADO,
        subida = EstadoSubida.COMPLETA,
        completo = true,
        mediosEsperados = 1,
        mediosSubidos = 1,
        resumenMedios = ResumenMedios(),
        motivoDescarte = null,
    )

    @Test
    fun `consulta en blanco devuelve la lista completa`() {
        val reportes = listOf(reporte("AAA-1111"), reporte("BBB-2222"))

        assertEquals(2, filtrarPorPlaca(reportes, "").size)
    }

    @Test
    fun `filtra sin distinguir mayusculas`() {
        val reportes = listOf(reporte("AAA-1111"), reporte("BBB-2222"))

        val resultado = filtrarPorPlaca(reportes, "bbb")

        assertEquals(listOf("BBB-2222"), resultado.map { it.placas })
    }

    @Test
    fun `coincidencia parcial por las primeras letras`() {
        val reportes = listOf(reporte("AAA-1111"), reporte("BBB-2222"))

        val resultado = filtrarPorPlaca(reportes, "AA")

        assertEquals(listOf("AAA-1111"), resultado.map { it.placas })
    }

    @Test
    fun `sin coincidencias devuelve lista vacia`() {
        val reportes = listOf(reporte("AAA-1111"))

        assertTrue(filtrarPorPlaca(reportes, "XYZ").isEmpty())
    }
}