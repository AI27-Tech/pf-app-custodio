package com.ai27.app.custodio.ui.historial

import com.ai27.app.custodio.ui.previews.DatosDemo
import org.junit.Assert.assertEquals
import org.junit.Test

class HistorialEstadoTest {

    @Test
    fun `cargando gana al vacio total`() {
        val estado = HistorialUiState(cargando = true, reportesTotales = 0)
        assertEquals(EstadoPantallaHistorial.CARGANDO, estadoVisibileDe(estado))
    }

    @Test
    fun `error gana al vacio total`() {
        val estado = HistorialUiState(reportesTotales = 0, error = 123)
        assertEquals(EstadoPantallaHistorial.ERROR, estadoVisibileDe(estado))
    }

    @Test
    fun `cargando gana al error viejo durante la recarga`() {
        val estado = HistorialUiState(cargando = true, error = 123)
        assertEquals(EstadoPantallaHistorial.CARGANDO, estadoVisibileDe(estado))
    }

    @Test
    fun `sin reportes muestra el vacio total`() {
        val estado = HistorialUiState(reportesTotales = 0)
        assertEquals(EstadoPantallaHistorial.VACIO_SIN_REPORTES, estadoVisibileDe(estado))
    }

    @Test
    fun `con reportes pero filtrando muestra sin coincidencias`() {
        val estado = HistorialUiState(reportesTotales = 3, reportesVisible = emptyList())
        assertEquals(EstadoPantallaHistorial.SIN_COINCIDENCIAS, estadoVisibileDe(estado))
    }

    @Test
    fun `con reportes visibles muestra la lista`() {
        val estado = HistorialUiState(
            reportesTotales = 3,
            reportesVisible = listOf(DatosDemo.reporteCerrado),
        )
        assertEquals(EstadoPantallaHistorial.LISTA, estadoVisibileDe(estado))
    }
}