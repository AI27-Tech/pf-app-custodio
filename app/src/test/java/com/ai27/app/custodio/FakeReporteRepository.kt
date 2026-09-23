package com.ai27.app.custodio

import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.model.Ciclo
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Reporte
import com.ai27.app.custodio.domain.model.ResumenMedios
import com.ai27.app.custodio.domain.model.TipoMedio
import com.ai27.app.custodio.domain.repository.MedioRepository
import com.ai27.app.custodio.domain.repository.ReporteRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeReporteRepository(
    val reportes: MutableList<Reporte> = mutableListOf(),
    // C6: el fake deriva los conteos del cierre igual que el UPDATE atómico del DAO
    // (SELECT sobre medios). Sin esta referencia, el fake no sabe cuántos medios hay.
    private val mediosConsultables: MedioRepository? = null,
) : ReporteRepository {

    override suspend fun obtenerActivo(custodioUid: String): Reporte? =
        reportes.firstOrNull { it.custodioUid == custodioUid && it.ciclo == Ciclo.ACTIVO }

    override suspend fun obtenerPorId(reporteId: String): Reporte? =
        reportes.firstOrNull { it.id == reporteId }

    override fun observarActivo(custodioUid: String): Flow<Reporte?> =
        MutableStateFlow(reportes.firstOrNull { it.custodioUid == custodioUid && it.ciclo == Ciclo.ACTIVO })

    override fun observarPorId(reporteId: String): Flow<Reporte?> =
        MutableStateFlow(reportes.firstOrNull { it.id == reporteId })

    override fun observarHistorial(custodioUid: String, fechaDesde: String): Flow<List<Reporte>> =
        MutableStateFlow(
            reportes
                .filter {
                    it.custodioUid == custodioUid &&
                        it.ciclo != Ciclo.ACTIVO &&
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
        hayNovedad: Boolean,
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
        // Misma derivación que el UPDATE atómico del DAO (hallazgo C6): los conteos salen
        // de los medios del reporte al momento del cierre, no de parámetros del llamador.
        val medios = mediosConsultables?.obtenerDelReporte(reporteId).orEmpty()
        val resumen = ResumenMedios(
            fotos = medios.count { it.tipo == TipoMedio.FOTO },
            audios = medios.count { it.tipo == TipoMedio.AUDIO },
            durationMsTotal = medios.sumOf { it.durationMs ?: 0L },
        )
        val cerrado = actual.copy(
            ciclo = Ciclo.CERRADO,
            closedAt = closedAt,
            comentario = comentario,
            hayNovedad = hayNovedad,
            mediosEsperados = medios.size,
            resumenMedios = resumen,
            // mediosSubidos y completo NO se tocan: el UPDATE de ReporteDao.cerrar no
            // los incluye, y hace bien — al cerrar, la evidencia puede seguir en la cola.
            // Quien los avanza es el pipeline vía actualizarSubida().
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

    override suspend fun actualizarSubida(
        reporteId: String,
        estado: com.ai27.app.custodio.domain.model.EstadoSubida,
        mediosSubidos: Int,
        completo: Boolean,
    ): Result<Reporte> {
        val indice = reportes.indexOfFirst { it.id == reporteId }
        if (indice == -1) {
            return Result.Failure(com.ai27.app.custodio.domain.error.DominioError.ReporteNoEncontrado)
        }
        val actualizado = reportes[indice].copy(
            subida = estado,
            mediosSubidos = mediosSubidos,
            completo = completo,
        )
        reportes[indice] = actualizado
        return Result.Success(actualizado)
    }

    override suspend fun obtenerPendientesSubida(custodioUid: String, fechaDesde: String): List<Reporte> =
        // Mismo filtro que ReporteDao.obtenerPendientesSubida (hallazgo C1 del AUDIT):
        // "pendiente" es no haber terminado la subida O estar en divergencia (subida
        // COMPLETA pero con mediosEsperados definidos y completo = false). El ciclo no se
        // mira, así que un reporte DESCARTADO sigue apareciendo (hallazgo E6).
        reportes.filter {
            it.custodioUid == custodioUid &&
                it.fechaLocal >= fechaDesde &&
                (it.subida != EstadoSubida.COMPLETA ||
                    (it.mediosEsperados != null && !it.completo))
        }
}