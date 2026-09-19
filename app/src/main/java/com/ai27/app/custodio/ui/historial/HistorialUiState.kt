package com.ai27.app.custodio.ui.historial

import com.ai27.app.custodio.domain.model.Reporte

data class HistorialUiState(
    val reportesVisible: List<Reporte> = emptyList(),
    val reportesTotales: Int = 0,
    val filtroPlaca: String = "",
    val pendientesSinSubir: Int = 0,
)

fun filtrarPorPlaca(reportes: List<Reporte>, consulta: String): List<Reporte> {
    val termino = consulta.trim()
    if (termino.isBlank()) return reportes
    return reportes.filter { reporte ->
        reporte.placas.contains(termino, ignoreCase = true) ||
            reporte.placasCapturadas.contains(termino, ignoreCase = true)
    }
}