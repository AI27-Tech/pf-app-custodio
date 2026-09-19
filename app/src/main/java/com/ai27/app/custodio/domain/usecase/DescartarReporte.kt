package com.ai27.app.custodio.domain.usecase

import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
import com.ai27.app.custodio.domain.model.Ciclo
import com.ai27.app.custodio.domain.model.Reporte
import com.ai27.app.custodio.domain.repository.ReporteRepository
import java.time.Instant

class DescartarReporte(
    private val reporteRepository: ReporteRepository,
    private val ahora: () -> Instant = { Instant.now() },
) {
    suspend operator fun invoke(reporteId: String, motivo: String): Result<Reporte> {
        if (motivo.isBlank()) {
            return Result.Failure(DominioError.MotivoDescarteVacio)
        }
        val reporte = reporteRepository.obtenerPorId(reporteId)
            ?: return Result.Failure(DominioError.ReporteNoEncontrado)
        if (reporte.ciclo != Ciclo.ACTIVO) {
            return Result.Failure(
                if (reporte.ciclo == Ciclo.CERRADO) DominioError.ReporteCerradoInmutable
                else DominioError.ReporteYaDescartado,
            )
        }
        return reporteRepository.descartar(reporteId, ahora(), motivo)
    }
}