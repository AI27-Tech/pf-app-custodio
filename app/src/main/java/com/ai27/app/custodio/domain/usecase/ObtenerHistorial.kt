package com.ai27.app.custodio.domain.usecase

import com.ai27.app.custodio.domain.model.Reporte
import com.ai27.app.custodio.domain.repository.ReporteRepository
import com.ai27.app.custodio.domain.util.fechaDesdeUltimos30Dias
import java.time.Instant
import kotlinx.coroutines.flow.Flow

class ObtenerHistorial(
    private val reporteRepository: ReporteRepository,
    private val ahora: () -> Instant = { Instant.now() },
) {
    operator fun invoke(custodioUid: String): Flow<List<Reporte>> =
        reporteRepository.observarHistorial(custodioUid, fechaDesdeUltimos30Dias(ahora()))
}