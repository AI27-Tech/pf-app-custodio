package com.ai27.app.custodio.domain.usecase

import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
import com.ai27.app.custodio.domain.model.Ciclo
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Gps
import com.ai27.app.custodio.domain.model.Limites
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.OrigenMedio
import com.ai27.app.custodio.domain.model.TipoMedio
import com.ai27.app.custodio.domain.repository.MedioRepository
import com.ai27.app.custodio.domain.repository.ReporteRepository
import java.time.Instant
import java.util.UUID

/**
 * Persiste un medio recién capturado. La FOTO se guarda **cruda** (los bytes JPEG del HAL,
 * `revelada = false`, con los [rotacionGrados] que CameraX no aplica); `RevelarFoto` la
 * convierte después en el WebP marcado que se sube (`docs/PLAN_REVELAR_FOTO.md`, Task 4).
 * El audio nace revelado y sin rotación.
 */
class CapturarMedio(
    private val reporteRepository: ReporteRepository,
    private val medioRepository: MedioRepository,
    private val ahora: () -> Instant = { Instant.now() },
) {
    suspend operator fun invoke(
        custodioUid: String,
        reporteId: String,
        tipo: TipoMedio,
        origen: OrigenMedio,
        contenido: ByteArray,
        miniatura: ByteArray? = null,
        durationMs: Long? = null,
        gps: Gps? = null,
        ubicacionTexto: String? = null,
        rotacionGrados: Int = 0,
    ): Result<Medio> {
        if (contenido.isEmpty()) {
            return Result.Failure(DominioError.SinContenido)
        }

        val reporte = reporteRepository.obtenerPorId(reporteId)
            ?: return Result.Failure(DominioError.ReporteNoEncontrado)
        if (reporte.custodioUid != custodioUid) {
            return Result.Failure(DominioError.ReporteNoEncontrado)
        }
        if (reporte.ciclo != Ciclo.ACTIVO) {
            return Result.Failure(
                if (reporte.ciclo == Ciclo.CERRADO) DominioError.ReporteCerradoInmutable
                else DominioError.ReporteYaDescartado,
            )
        }

        if (tipo == TipoMedio.AUDIO && (durationMs ?: 0L) > Limites.AUDIO_MAX_MS) {
            return Result.Failure(DominioError.AudioExcedeCincoMinutos)
        }
        if (medioRepository.obtenerDelReporte(reporteId).size >= Limites.MEDIOS_POR_REPORTE) {
            return Result.Failure(DominioError.LimiteDeMediosAlcanzado)
        }

        val medio = Medio(
            id = UUID.randomUUID().toString(),
            reporteId = reporteId,
            tipo = tipo,
            origen = origen,
            createdAtDevice = ahora(),
            contentType = when (tipo) {
                TipoMedio.FOTO -> "image/jpeg"
                TipoMedio.AUDIO -> "audio/mp4"
            },
            estadoSubida = EstadoSubida.PENDIENTE,
            gps = gps,
            ubicacionTexto = ubicacionTexto,
            sizeBytes = contenido.size.toLong(),
            durationMs = durationMs,
            revelada = tipo != TipoMedio.FOTO,
            rotacionGrados = if (tipo == TipoMedio.FOTO) rotacionGrados else 0,
        )
        return medioRepository.guardar(medio, contenido, miniatura = miniatura)
    }
}