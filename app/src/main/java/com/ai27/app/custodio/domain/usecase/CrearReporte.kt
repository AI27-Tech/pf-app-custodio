package com.ai27.app.custodio.domain.usecase

import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
import com.ai27.app.custodio.domain.model.Ciclo
import com.ai27.app.custodio.domain.model.Custodio
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Reporte
import com.ai27.app.custodio.domain.model.ResumenMedios
import com.ai27.app.custodio.domain.repository.ReporteRepository
import com.ai27.app.custodio.domain.util.fechaLocalDe
import com.ai27.app.custodio.domain.util.normalizarPlaca
import java.time.Instant
import java.util.UUID

class CrearReporte(
    private val reporteRepository: ReporteRepository,
    private val ahora: () -> Instant = { Instant.now() },
) {
    suspend operator fun invoke(
        custodio: Custodio,
        deviceId: String,
        clienteId: String,
        placasCapturadas: String,
    ): Result<Reporte> {
        val placas = normalizarPlaca(placasCapturadas)
        if (placas.isEmpty()) {
            return Result.Failure(DominioError.PlacaVacia(placasCapturadas.takeIf { it.isNotBlank() }))
        }

        val activo = reporteRepository.obtenerActivo(custodio.uid)
        if (activo != null) {
            return Result.Failure(DominioError.YaExisteReporteActivo(activo.id))
        }

        val instante = ahora()
        val reporte = Reporte(
            id = UUID.randomUUID().toString(),
            placas = placas,
            placasCapturadas = placasCapturadas.trim(),
            custodioUid = custodio.uid,
            custodioCorreo = custodio.correo,
            clienteId = clienteId,
            deviceId = deviceId,
            fechaLocal = fechaLocalDe(instante),
            createdAtDevice = instante,
            ciclo = Ciclo.ACTIVO,
            subida = EstadoSubida.PENDIENTE,
            completo = false,
            mediosEsperados = null,
            mediosSubidos = 0,
            resumenMedios = ResumenMedios(),
        )
        return reporteRepository.crear(reporte)
    }
}