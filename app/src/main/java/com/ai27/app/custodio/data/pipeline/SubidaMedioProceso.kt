package com.ai27.app.custodio.data.pipeline

import com.ai27.app.custodio.data.local.archivo.AlmacenArchivos
import com.ai27.app.custodio.data.remote.MedioRemoto
import com.ai27.app.custodio.data.remote.ReporteRemoto
import com.ai27.app.custodio.data.repository.ETIQUETAS_SITUACION_IDS
import com.ai27.app.custodio.data.util.Sha256
import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
import com.ai27.app.custodio.domain.model.Ciclo
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Reporte
import com.ai27.app.custodio.domain.model.SubidaReporte
import com.ai27.app.custodio.domain.repository.MedioRepository
import com.ai27.app.custodio.domain.repository.ReporteRepository

// Pasos de la subida de un medio (PLAN §6.2): asegura el documento del reporte,
// sube miniatura y archivo a Storage, escribe el documento en medios, marca COMPLETA,
// borra el archivo local y avanza los contadores. Idempotente por diseño.
class SubidaMedioProceso(
    private val reporteRepository: ReporteRepository,
    private val medioRepository: MedioRepository,
    private val reporteRemoto: ReporteRemoto,
    private val medioRemoto: MedioRemoto,
    private val almacenArchivos: AlmacenArchivos,
) {
    suspend fun ejecutar(reporteId: String, medioId: String): Result<Unit> {
        val medio = medioRepository.obtenerPorId(medioId)
            ?: return Result.Success(Unit) // se borró antes de subir
        if (medio.estadoSubida == EstadoSubida.COMPLETA) return Result.Success(Unit)
        // Sin etiqueta de Situación no sale: el criterio de subida es "tiene al menos una
        // Situación" (PLAN §14, decisión A) y el analista no puede filtrar evidencia sin
        // clasificar. Es defensa en profundidad — la cola ya filtra por tieneSituacion—
        // para que ningún camino futuro se la salte (hallazgo E3).
        if (medio.etiquetas.none { it in ETIQUETAS_SITUACION_IDS }) return Result.Success(Unit)
        val reporte = reporteRepository.obtenerPorId(reporteId)
            ?: return Result.Success(Unit)
        if (reporte.ciclo == Ciclo.DESCARTADO) return Result.Success(Unit) // sin evidencia futura a un viaje descartado

        val aseguro = asegurarReporte(reporte)
        when (aseguro) {
            null -> return Result.Success(Unit) // reporte descartado en el servidor: no se sube
            is Result.Failure -> {
                marcarFallida(medioId)
                return aseguro
            }
            is Result.Success -> marcarEnProgreso(medioId)
        }

        val contenido = leerContenido(medioId)
            ?: return fracasoSinContenido(medioId)
        val miniatura = medio.thumbnailPath?.let { almacenArchivos.leer(it) }
        val sha = Sha256.digestHex(contenido)

        val rutas = when (val r = medioRemoto.subirArchivo(reporte, medio, contenido, miniatura)) {
            is Result.Success -> r.value
            is Result.Failure -> {
                marcarFallida(medioId)
                return r
            }
        }
        when (val r = medioRemoto.crearDocumentoSiNoExiste(reporte, medio, rutas)) {
            is Result.Success -> Unit
            is Result.Failure -> {
                marcarFallida(medioId)
                return r
            }
        }

        when (val r = medioRepository.marcarSubidaCompleta(medioId, rutas.media, sha)) {
            is Result.Success -> Unit
            is Result.Failure -> {
                marcarFallida(medioId)
                return r
            }
        }

        actualizarContadores(reporteId)
        return Result.Success(Unit)
    }

    // Devuelve false cuando el ciclo remoto impide subir (reporte descartado).
    private suspend fun asegurarReporte(reporte: Reporte): Result<Unit>? {
        val cicloRemoto = when (val r = reporteRemoto.obtenerCiclo(reporte.id)) {
            is Result.Success -> r.value
            is Result.Failure -> return r
        }
        return when (cicloRemoto) {
            null -> reporteRemoto.crear(reporte.copy(ciclo = Ciclo.ACTIVO))
            Ciclo.ACTIVO, Ciclo.CERRADO -> Result.Success(Unit)
            Ciclo.DESCARTADO -> null // no se sube evidencia a un viaje descartado
        }
    }

    private suspend fun marcarEnProgreso(medioId: String) {
        if (medioRepository.obtenerPorId(medioId)?.estadoSubida != EstadoSubida.EN_PROGRESO) {
            medioRepository.actualizarEstadoSubida(medioId, EstadoSubida.EN_PROGRESO)
        }
    }

    private suspend fun marcarFallida(medioId: String) {
        medioRepository.actualizarEstadoSubida(medioId, EstadoSubida.FALLIDA)
    }

    private suspend fun leerContenido(medioId: String): ByteArray? {
        val ruta = medioRepository.obtenerPorId(medioId)?.localPath ?: return null
        return almacenArchivos.leer(ruta)
    }

    private suspend fun fracasoSinContenido(medioId: String): Result<Unit> {
        marcarFallida(medioId)
        return Result.Failure(
            DominioError.Inesperado(IllegalStateException("Archivo local ausente para el medio $medioId")),
        )
    }

    private suspend fun actualizarContadores(reporteId: String) {
        val reporte = reporteRepository.obtenerPorId(reporteId) ?: return
        if (reporte.ciclo == Ciclo.DESCARTADO) return
        val medios = medioRepository.obtenerDelReporte(reporteId)
        val estado = SubidaReporte.computarEstado(medios)
        val subidos = SubidaReporte.mediosSubidos(medios)
        val completo = SubidaReporte.completo(reporte, medios)
        reporteRepository.actualizarSubida(reporteId, estado, subidos, completo)
        reporteRemoto.actualizarContadores(reporteId, estado, subidos, completo)
    }
}