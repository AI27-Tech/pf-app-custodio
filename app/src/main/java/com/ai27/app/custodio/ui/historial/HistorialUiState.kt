package com.ai27.app.custodio.ui.historial

import androidx.annotation.StringRes
import com.ai27.app.custodio.domain.model.Reporte

data class HistorialUiState(
    val reportesVisible: List<Reporte> = emptyList(),
    val reportesTotales: Int = 0,
    val filtroPlaca: String = "",
    val cargando: Boolean = false,
    @StringRes val error: Int? = null,
)

fun filtrarPorPlaca(reportes: List<Reporte>, consulta: String): List<Reporte> {
    val termino = consulta.trim()
    if (termino.isBlank()) return reportes
    return reportes.filter { reporte ->
        reporte.placas.contains(termino, ignoreCase = true) ||
            reporte.placasCapturadas.contains(termino, ignoreCase = true)
    }
}

/**
 * Copias visibles de la pantalla de historial (U8).
 *
 * El orden es deliberado: `cargando` y `error` ganan al "sin reportes" para que un recargue
 * (reintento) no pinte intermitentemente el vacío mientras llega la respuesta, y para que el
 * error siga visible aunque la consulta previa haya sido vacía.
 */
enum class EstadoPantallaHistorial { CARGANDO, ERROR, VACIO_SIN_REPORTES, SIN_COINCIDENCIAS, LISTA }

fun estadoVisibileDe(estado: HistorialUiState): EstadoPantallaHistorial = when {
    estado.cargando -> EstadoPantallaHistorial.CARGANDO
    estado.error != null -> EstadoPantallaHistorial.ERROR
    estado.reportesTotales == 0 -> EstadoPantallaHistorial.VACIO_SIN_REPORTES
    estado.reportesVisible.isEmpty() -> EstadoPantallaHistorial.SIN_COINCIDENCIAS
    else -> EstadoPantallaHistorial.LISTA
}
