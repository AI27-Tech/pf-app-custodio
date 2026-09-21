package com.ai27.app.custodio.data.repository

import com.ai27.app.custodio.data.local.db.AppCustodioDatabase
import com.ai27.app.custodio.data.mapper.aDominio
import com.ai27.app.custodio.data.mapper.aEntidad
import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Reporte
import com.ai27.app.custodio.domain.model.ResumenMedios
import com.ai27.app.custodio.domain.repository.ReporteRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomReporteRepository(
    private val database: AppCustodioDatabase,
) : ReporteRepository {

    private val dao = database.reporteDao()

    override suspend fun obtenerActivo(custodioUid: String): Reporte? =
        dao.obtenerActivo(custodioUid)?.aDominio()

    override suspend fun obtenerPorId(reporteId: String): Reporte? =
        dao.obtenerPorId(reporteId)?.aDominio()

    override fun observarActivo(custodioUid: String): Flow<Reporte?> =
        dao.observarActivo(custodioUid).map { it?.aDominio() }

    override fun observarPorId(reporteId: String): Flow<Reporte?> =
        dao.observarPorId(reporteId).map { it?.aDominio() }

    override fun observarHistorial(custodioUid: String, fechaDesde: String): Flow<List<Reporte>> =
        dao.observarHistorial(custodioUid, fechaDesde).map { lista -> lista.map { it.aDominio() } }

    override suspend fun crear(reporte: Reporte): Result<Reporte> {
        return if (dao.insertar(reporte.aEntidad()) != -1L) {
            Result.Success(reporte)
        } else {
            val existente = dao.obtenerActivo(reporte.custodioUid)
                ?: dao.obtenerPorId(reporte.id)
            Result.Failure(
                if (existente?.ciclo == "ACTIVO") {
                    DominioError.YaExisteReporteActivo(existente.id)
                } else {
                    DominioError.Inesperado(IllegalStateException("Insert ignorado sin motivo conocido"))
                },
            )
        }
    }

    override suspend fun cerrar(
        reporteId: String,
        closedAt: Instant,
        comentario: String?,
        hayNovedad: Boolean,
        resumen: ResumenMedios,
        mediosEsperados: Int,
    ): Result<Reporte> {
        val filas = dao.cerrar(
            id = reporteId,
            closedAt = closedAt.toEpochMilli(),
            comentario = comentario,
            hayNovedad = hayNovedad,
            mediosEsperados = mediosEsperados,
            fotos = resumen.fotos,
            audios = resumen.audios,
            durationMsTotal = resumen.durationMsTotal,
        )
        val actualizado = dao.obtenerPorId(reporteId)
        return if (filas == 1 && actualizado != null) {
            Result.Success(actualizado.aDominio())
        } else {
            Result.Failure(
                actualizado?.let {
                    if (it.ciclo == "CERRADO") DominioError.ReporteCerradoInmutable
                    else DominioError.ReporteYaDescartado
                } ?: DominioError.ReporteNoEncontrado,
            )
        }
    }

    override suspend fun descartar(reporteId: String, closedAt: Instant, motivo: String): Result<Reporte> {
        val filas = dao.descartar(reporteId, closedAt.toEpochMilli(), motivo)
        val actualizado = dao.obtenerPorId(reporteId)
        return if (filas == 1 && actualizado != null) {
            Result.Success(actualizado.aDominio())
        } else {
            Result.Failure(
                actualizado?.let {
                    if (it.ciclo == "DESCARTADO") DominioError.ReporteYaDescartado
                    else DominioError.ReporteCerradoInmutable
                } ?: DominioError.ReporteNoEncontrado,
            )
        }
    }

    override suspend fun actualizarSubida(
        reporteId: String,
        estado: EstadoSubida,
        mediosSubidos: Int,
        completo: Boolean,
    ): Result<Reporte> {
        val filas = dao.actualizarSubida(reporteId, estado.name, mediosSubidos, completo)
        val actualizado = dao.obtenerPorId(reporteId)
        return if (filas == 1 && actualizado != null) {
            Result.Success(actualizado.aDominio())
        } else {
            Result.Failure(DominioError.ReporteNoEncontrado)
        }
    }

    override suspend fun obtenerPendientesSubida(): List<Reporte> =
        dao.obtenerPendientesSubida().map { it.aDominio() }
}