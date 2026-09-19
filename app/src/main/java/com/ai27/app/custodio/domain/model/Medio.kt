package com.ai27.app.custodio.domain.model

import java.time.Instant

enum class TipoMedio { FOTO, AUDIO }

enum class OrigenMedio { CAMARA, GRABADORA }

data class Medio(
    val id: String,
    val reporteId: String,
    val tipo: TipoMedio,
    val origen: OrigenMedio,
    val createdAtDevice: Instant,
    val contentType: String,
    val estadoSubida: EstadoSubida,
    val etiqueta: String? = null,
    val localPath: String? = null,
    val storagePath: String? = null,
    val thumbnailPath: String? = null,
    val gps: Gps? = null,
    val ubicacionTexto: String? = null,
    val comentario: String? = null,
    val sizeBytes: Long? = null,
    val durationMs: Long? = null,
    val sha256: String? = null,
)