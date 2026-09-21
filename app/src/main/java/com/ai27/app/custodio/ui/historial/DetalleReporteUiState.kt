package com.ai27.app.custodio.ui.historial

import androidx.annotation.StringRes
import com.ai27.app.custodio.domain.model.Etiqueta
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.Reporte

/**
 * Estado de la pantalla de detalle de un viaje cerrado/descartado. A diferencia de la
 * versión pre-C5, distingue explícitamente el primer frame sin datos (`cargando`) de un
 * fallo de lectura (`error`), para que la pantalla no confunda "todavía no llegó" con
 * "no existe" y ofrezca reintentar cuando corresponde.
 */
data class DetalleReporteUiState(
    val reporte: Reporte? = null,
    val medios: List<Medio> = emptyList(),
    val etiquetas: List<Etiqueta> = emptyList(),
    val cargando: Boolean = false,
    @StringRes val error: Int? = null,
)
