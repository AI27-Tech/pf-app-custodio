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
    val etiquetas: List<String> = emptyList(),
    val localPath: String? = null,
    val storagePath: String? = null,
    val thumbnailPath: String? = null,
    val gps: Gps? = null,
    val ubicacionTexto: String? = null,
    val comentario: String? = null,
    val sizeBytes: Long? = null,
    val durationMs: Long? = null,
    val sha256: String? = null,
    /** Último error persistido al fallar la subida (hallazgo C5); null si nunca falló. */
    val ultimoError: String? = null,
    /** Intentos de subida transcurridos cuando quedó FALLIDA. */
    val intentosSubida: Int = 0,
    /**
     * `false` mientras la foto es **cruda** (los bytes JPEG del HAL, sin rotar ni marcar) y
     * `true` cuando ya es la foto **revelada** que se sube (`docs/PLAN_REVELAR_FOTO.md`, D5).
     * El audio y todo medio anterior a la v6 de la base nacen revelados.
     */
    val revelada: Boolean = true,
    /** Grados (0, 90, 180, 270) que hay que rotar la foto cruda; CameraX no los aplica. */
    val rotacionGrados: Int = 0,
)