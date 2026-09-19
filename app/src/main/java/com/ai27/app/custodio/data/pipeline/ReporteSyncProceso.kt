package com.ai27.app.custodio.data.pipeline

import com.ai27.app.custodio.data.remote.ReporteRemoto
import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.model.Ciclo
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Reporte
import com.ai27.app.custodio.domain.model.SubidaReporte
import com.ai27.app.custodio.domain.repository.MedioRepository
import com.ai27.app.custodio.domain.repository.ReporteRepository

// Sincroniza el documento del reporte en Firestore con el estado local (Room).
// Idempotente: consulta el ciclo remoto y solo aplica la transición que las reglas permiten.
class ReporteSyncProceso(
    private val reporteRepository: ReporteRepository,
    private val medioRepository: MedioRepository,
    private val reporteRemoto: ReporteRemoto,
) {
    suspend fun ejecutar(reporteId: String): Result<Unit> {
        val reporte = reporteRepository.obtenerPorId(reporteId)
            ?: return Result.Success(Unit) // fue borrado localmente: no hay nada que sincronizar
        val cicloRemoto = when (val r = reporteRemoto.obtenerCiclo(reporteId)) {
            is Result.Success -> r.value
            is Result.Failure -> return r
        }
        return when (cicloRemoto) {
            null -> crearYTransicionar(reporte)
            Ciclo.ACTIVO -> transicionarDesdeActivo(reporte)
            Ciclo.CERRADO -> sincronizarContadoresSiCerrado(reporte)
            Ciclo.DESCARTADO -> Result.Success(Unit) // inamovible
        }
    }

    private suspend fun crearYTransicionar(reporte: Reporte): Result<Unit> {
        // El doc se escribe siempre con ciclo ACTIVO (reglas §5.3) y después se
        // transiciona: un reporte cerrado offline llega al servidor como ACTIVO->CERRADO.
        val creado = reporteRemoto.crear(reporte.copy(ciclo = Ciclo.ACTIVO))
        if (creado is Result.Failure) return creado
        return transicionarDesdeActivo(reporte)
    }

    private suspend fun transicionarDesdeActivo(reporte: Reporte): Result<Unit> = when (reporte.ciclo) {
        Ciclo.ACTIVO -> Result.Success(Unit) // ya quedó creado arriba o remoto, nada que hacer
        Ciclo.CERRADO -> {
            val sincronizado = conContadores(reporte)
            reporteRemoto.cerrar(sincronizado)
        }
        Ciclo.DESCARTADO -> reporteRemoto.descartar(reporte)
    }

    private suspend fun sincronizarContadoresSiCerrado(reporte: Reporte): Result<Unit> {
        if (reporte.ciclo != Ciclo.CERRADO) return Result.Success(Unit)
        val (estado, subidos, completo) = contadoresDe(reporte)
        reporteRepository.actualizarSubida(reporte.id, estado, subidos, completo)
        return reporteRemoto.actualizarContadores(reporte.id, estado, subidos, completo)
    }

    private suspend fun conContadores(reporte: Reporte): Reporte {
        val (estado, subidos, completo) = contadoresDe(reporte)
        reporteRepository.actualizarSubida(reporte.id, estado, subidos, completo)
        return reporte.copy(subida = estado, mediosSubidos = subidos, completo = completo)
    }

    private suspend fun contadoresDe(reporte: Reporte): Triple<EstadoSubida, Int, Boolean> {
        val medios = medioRepository.obtenerDelReporte(reporte.id)
        return Triple(
            SubidaReporte.computarEstado(medios),
            SubidaReporte.mediosSubidos(medios),
            SubidaReporte.completo(reporte, medios),
        )
    }
}