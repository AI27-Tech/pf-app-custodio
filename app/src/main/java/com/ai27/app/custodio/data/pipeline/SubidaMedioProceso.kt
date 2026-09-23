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
        // Sin etiqueta de Situación no sale: el criterio de subida es "tiene al menos una
        // Situación" (PLAN §14, decisión A) y el analista no puede filtrar evidencia sin
        // clasificar. Es defensa en profundidad — la cola ya filtra por tieneSituacion—
        // para que ningún camino futuro se la salte (hallazgo E3).
        if (medio.etiquetas.none { it in ETIQUETAS_SITUACION_IDS }) return Result.Success(Unit)
        val reporte = reporteRepository.obtenerPorId(reporteId)
            ?: return Result.Success(Unit)
        if (reporte.ciclo == Ciclo.DESCARTADO) return Result.Success(Unit) // sin evidencia futura a un viaje descartado
        if (medio.estadoSubida == EstadoSubida.COMPLETA) {
            // C1: un reintento encuentra el medio ya COMPLETA (la subida se hizo pero el
            // avance de contadores quedó fallido/descartado). No se vuelve a subir: solo
            // se recalculan los contadores del reporte para sanar la divergencia.
            return propagarContadores(reporteId)
        }

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
        // El documento remoto se construye con el sha ya calculado: sin esto, el medio que
        // se escribió en Firestore lleva `sha256 = null` y, como `medios` es create-only
        // en las reglas, el valor queda irrellenable para siempre (hallazgo C2).
        val conSha = medio.copy(sha256 = sha)

        val rutas = when (val r = medioRemoto.subirArchivo(reporte, conSha, contenido, miniatura)) {
            is Result.Success -> r.value
            is Result.Failure -> {
                marcarFallida(medioId)
                return r
            }
        }
        when (val r = medioRemoto.crearDocumentoSiNoExiste(reporte, conSha, rutas)) {
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

        // El resultado se PROPAGA: si la escritura remota de contadores falla, el worker
        // reintenta y el reporte no queda marcado COMPLETA localmente sin el remoto (C1).
        return propagarContadores(reporteId)
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
        // Permanente (C5): el archivo no va a reaparecer, reintentar no tiene sentido.
        return Result.Failure(DominioError.ArchivoLocalAusente)
    }

    // Recalcula los contadores del reporte y los propaga remoto-primero. Firestore hace un
    // merge monótono (C3), así que las escrituras concurrentes de otros medios no se pisan.
    // Solo si la escritura remota gana se refleja el estado en Room: Room nunca queda
    // adelantado respecto al servidor (C1) — si falla, el worker reintenta en la rama
    // COMPLETA de `ejecutar`.
    private suspend fun propagarContadores(reporteId: String): Result<Unit> {
        val reporte = reporteRepository.obtenerPorId(reporteId) ?: return Result.Success(Unit)
        if (reporte.ciclo == Ciclo.DESCARTADO) return Result.Success(Unit)
        val medios = medioRepository.obtenerDelReporte(reporteId)
        val estado = SubidaReporte.computarEstado(medios)
        val subidos = SubidaReporte.mediosSubidos(medios)
        val completo = SubidaReporte.completo(reporte, medios)
        val remoto = reporteRemoto.actualizarContadores(reporteId, estado, subidos, completo)
        if (remoto is Result.Failure) return remoto
        reporteRepository.actualizarSubida(reporteId, estado, subidos, completo)
        return Result.Success(Unit)
    }
}