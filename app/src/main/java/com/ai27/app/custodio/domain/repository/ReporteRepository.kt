package com.ai27.app.custodio.domain.repository

import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Reporte
import com.ai27.app.custodio.domain.model.ResumenMedios
import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface ReporteRepository {
    suspend fun obtenerActivo(custodioUid: String): Reporte?
    suspend fun obtenerPorId(reporteId: String): Reporte?
    fun observarActivo(custodioUid: String): Flow<Reporte?>
    fun observarPorId(reporteId: String): Flow<Reporte?>
    fun observarHistorial(custodioUid: String, fechaDesde: String): Flow<List<Reporte>>
    suspend fun crear(reporte: Reporte): Result<Reporte>
    suspend fun cerrar(
        reporteId: String,
        closedAt: Instant,
        comentario: String?,
        resumen: ResumenMedios,
        mediosEsperados: Int,
    ): Result<Reporte>
    suspend fun descartar(reporteId: String, closedAt: Instant, motivo: String): Result<Reporte>
    suspend fun actualizarSubida(
        reporteId: String,
        estado: EstadoSubida,
        mediosSubidos: Int,
        completo: Boolean,
    ): Result<Reporte>
    suspend fun obtenerPendientesSubida(): List<Reporte>
}