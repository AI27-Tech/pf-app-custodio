package com.ai27.app.custodio

import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.model.Ciclo
import com.ai27.app.custodio.domain.model.Reporte
import com.ai27.app.custodio.domain.model.ResumenMedios
import com.ai27.app.custodio.domain.repository.ReporteRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeReporteRepository(
    val reportes: MutableList<Reporte> = mutableListOf(),
) : ReporteRepository {

    override suspend fun obtenerActivo(custodioUid: String): Reporte? =
        reportes.firstOrNull { it.custodioUid == custodioUid && it.ciclo == Ciclo.ACTIVO }

    override suspend fun obtenerPorId(reporteId: String): Reporte? =
        reportes.firstOrNull { it.id == reporteId }

    override fun observarActivo(custodioUid: String): Flow<Reporte?> =
        MutableStateFlow(reportes.firstOrNull { it.custodioUid == custodioUid && it.ciclo == Ciclo.ACTIVO })

    override fun observarHistorial(custodioUid: String, fechaDesde: String): Flow<List<Reporte>> =
        MutableStateFlow(
            reportes
                .filter {
                    it.custodioUid == custodioUid &&
                        it.ciclo == Ciclo.CERRADO &&
                        it.fechaLocal >= fechaDesde
                }
                .sortedByDescending { it.createdAtDevice },
        )

    override suspend fun crear(reporte: Reporte): Result<Reporte> {
        val activo = obtenerActivo(reporte.custodioUid)
        return if (activo != null) {
            Result.Failure(com.ai27.app.custodio.domain.error.DominioError.YaExisteReporteActivo(activo.id))
        } else {
            reportes.add(reporte)
            Result.Success(reporte)
        }
    }

    override suspend fun cerrar(
        reporteId: String,
        closedAt: Instant,
        comentario: String?,
        resumen: ResumenMedios,
        mediosEsperados: Int,
    ): Result<Reporte> {
        val indice = reportes.indexOfFirst { it.id == reporteId }
        if (indice == -1) {
            return Result.Failure(com.ai27.app.custodio.domain.error.DominioError.ReporteNoEncontrado)
        }
        val actual = reportes[indice]
        if (actual.ciclo != Ciclo.ACTIVO) {
            return Result.Failure(
                if (actual.ciclo == Ciclo.CERRADO) {
                    com.ai27.app.custodio.domain.error.DominioError.ReporteCerradoInmutable
                } else {
                    com.ai27.app.custodio.domain.error.DominioError.ReporteYaDescartado
                },
            )
        }
        val cerrado = actual.copy(
            ciclo = Ciclo.CERRADO,
            closedAt = closedAt,
            comentario = comentario,
            completo = true,
            mediosEsperados = mediosEsperados,
            mediosSubidos = mediosEsperados,
            resumenMedios = resumen,
        )
        reportes[indice] = cerrado
        return Result.Success(cerrado)
    }

    override suspend fun descartar(reporteId: String, closedAt: Instant, motivo: String): Result<Reporte> {
        val indice = reportes.indexOfFirst { it.id == reporteId }
        if (indice == -1) {
            return Result.Failure(com.ai27.app.custodio.domain.error.DominioError.ReporteNoEncontrado)
        }
        val actual = reportes[indice]
        if (actual.ciclo != Ciclo.ACTIVO) {
            return Result.Failure(
                if (actual.ciclo == Ciclo.CERRADO) {
                    com.ai27.app.custodio.domain.error.DominioError.ReporteCerradoInmutable
                } else {
                    com.ai27.app.custodio.domain.error.DominioError.ReporteYaDescartado
                },
            )
        }
        val descartado = actual.copy(ciclo = Ciclo.DESCARTADO, motivoDescarte = motivo, closedAt = closedAt)
        reportes[indice] = descartado
        return Result.Success(descartado)
    }
}