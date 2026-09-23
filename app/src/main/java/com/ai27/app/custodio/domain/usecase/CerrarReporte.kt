package com.ai27.app.custodio.domain.usecase

import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
import com.ai27.app.custodio.domain.model.Ciclo
import com.ai27.app.custodio.domain.model.Reporte
import com.ai27.app.custodio.domain.repository.MedioRepository
import com.ai27.app.custodio.domain.repository.ReporteRepository
import java.time.Instant

class CerrarReporte(
    private val reporteRepository: ReporteRepository,
    private val medioRepository: MedioRepository,
    private val ahora: () -> Instant = { Instant.now() },
) {
    suspend operator fun invoke(
        reporteId: String,
        comentario: String? = null,
        hayNovedad: Boolean = false,
    ): Result<Reporte> {
        val reporte = reporteRepository.obtenerPorId(reporteId)
            ?: return Result.Failure(DominioError.ReporteNoEncontrado)
        if (reporte.ciclo != Ciclo.ACTIVO) {
            return Result.Failure(
                if (reporte.ciclo == Ciclo.CERRADO) DominioError.ReporteCerradoInmutable
                else DominioError.ReporteYaDescartado,
            )
        }

        val medios = medioRepository.obtenerDelReporte(reporteId)
        if (medios.isEmpty()) {
            return Result.Failure(DominioError.ReporteSinMedios)
        }

        // El resumen y mediosEsperados los calcula el propio UPDATE atómico del DAO
        // (SELECT escalar sobre medios, hallazgo C6): un medio que entre justo antes del
        // cierre queda contado, y no hay ventana de carrera entre leer y escribir.
        return reporteRepository.cerrar(
            reporteId = reporteId,
            closedAt = ahora(),
            comentario = comentario,
            hayNovedad = hayNovedad,
        )
    }
}