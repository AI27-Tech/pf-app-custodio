package com.ai27.app.custodio.domain.model

import java.time.Instant

enum class Ciclo { ACTIVO, CERRADO, DESCARTADO }

enum class EstadoSubida { PENDIENTE, EN_PROGRESO, COMPLETA, FALLIDA }

data class ResumenMedios(
    val fotos: Int = 0,
    val audios: Int = 0,
    val durationMsTotal: Long = 0,
)

data class Reporte(
    val id: String,
    val placas: String,
    val placasCapturadas: String,
    val custodioUid: String,
    val custodioTelefono: String,
    val clienteId: String,
    val deviceId: String,
    val fechaLocal: String,
    val createdAtDevice: Instant,
    val ciclo: Ciclo,
    val subida: EstadoSubida,
    val completo: Boolean,
    val mediosEsperados: Int?,
    val mediosSubidos: Int,
    val resumenMedios: ResumenMedios,
    val schemaVersion: Int = SCHEMA_VERSION,
    val closedAt: Instant? = null,
    val comentario: String? = null,
    val hayNovedad: Boolean = false,
    val motivoDescarte: String? = null,
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}