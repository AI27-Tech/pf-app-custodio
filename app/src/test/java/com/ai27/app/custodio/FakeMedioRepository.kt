package com.ai27.app.custodio

import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.repository.MedioRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeMedioRepository(
    val medios: MutableList<Medio> = mutableListOf(),
    val archivosGuardados: MutableMap<String, ByteArray> = mutableMapOf(),
) : MedioRepository {

    override suspend fun guardar(medio: Medio, contenido: ByteArray): Result<Medio> {
        val guardado = medio.copy(localPath = "fisico/${medio.id}")
        medios.add(guardado)
        archivosGuardados[guardado.localPath!!] = contenido
        return Result.Success(guardado)
    }

    override suspend fun obtenerPorId(medioId: String): Medio? =
        medios.firstOrNull { it.id == medioId }

    override suspend fun obtenerDelReporte(reporteId: String): List<Medio> =
        medios.filter { it.reporteId == reporteId }

    override fun observarDelReporte(reporteId: String): Flow<List<Medio>> =
        MutableStateFlow(medios.filter { it.reporteId == reporteId })

    override suspend fun etiquetar(medioId: String, etiquetaId: String): Result<Medio> {
        val indice = medios.indexOfFirst { it.id == medioId }
        if (indice == -1) {
            return Result.Failure(com.ai27.app.custodio.domain.error.DominioError.MedioNoEncontrado)
        }
        val actualizado = medios[indice].copy(etiqueta = etiquetaId)
        medios[indice] = actualizado
        return Result.Success(actualizado)
    }

    override suspend fun actualizarEstadoSubida(medioId: String, estado: EstadoSubida): Result<Medio> {
        val indice = medios.indexOfFirst { it.id == medioId }
        if (indice == -1) {
            return Result.Failure(com.ai27.app.custodio.domain.error.DominioError.MedioNoEncontrado)
        }
        val actualizado = medios[indice].copy(estadoSubida = estado)
        medios[indice] = actualizado
        return Result.Success(actualizado)
    }

    override suspend fun borrarPendiente(medioId: String): Result<Medio> {
        val medio = medios.firstOrNull { it.id == medioId }
            ?: return Result.Failure(com.ai27.app.custodio.domain.error.DominioError.MedioNoEncontrado)
        if (medio.estadoSubida != EstadoSubida.PENDIENTE) {
            return Result.Failure(com.ai27.app.custodio.domain.error.DominioError.MedioEnSubida)
        }
        archivosGuardados.remove(medio.localPath)
        medios.remove(medio)
        return Result.Success(medio)
    }
}