package com.ai27.app.custodio.domain.model

// Cálculo puro del estado agregado de subida de un reporte a partir de sus medios.
// Es el estado que se refleja en `reportes.subida` y en el documento remoto.
object SubidaReporte {

    fun computarEstado(medios: List<Medio>): EstadoSubida = when {
        medios.isEmpty() -> EstadoSubida.PENDIENTE
        medios.all { it.estadoSubida == EstadoSubida.COMPLETA } -> EstadoSubida.COMPLETA
        medios.any { it.estadoSubida == EstadoSubida.EN_PROGRESO } -> EstadoSubida.EN_PROGRESO
        medios.any { it.estadoSubida == EstadoSubida.FALLIDA } -> EstadoSubida.FALLIDA
        else -> EstadoSubida.PENDIENTE
    }

    fun mediosSubidos(medios: List<Medio>): Int =
        medios.count { it.estadoSubida == EstadoSubida.COMPLETA }

    fun completo(reporte: Reporte, medios: List<Medio>): Boolean =
        reporte.mediosEsperados != null &&
            mediosSubidos(medios) >= reporte.mediosEsperados
}