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

    private val pendientes = MutableStateFlow(0)
    private val pendientesMedios = MutableStateFlow<List<Medio>>(emptyList())

    fun actualizarPendientes() {
        pendientes.value = medios.count { it.estadoSubida != EstadoSubida.COMPLETA }
        pendientesMedios.value = medios.filter { it.estadoSubida != EstadoSubida.COMPLETA }
    }

    override suspend fun guardar(
        medio: Medio,
        contenido: ByteArray,
        miniatura: ByteArray?,
    ): Result<Medio> {
        val guardado = if (miniatura != null) {
            val rutaMini = "fisico/${medio.id}-min"
            archivosGuardados[rutaMini] = miniatura
            medio.copy(localPath = "fisico/${medio.id}", thumbnailPath = rutaMini)
        } else {
            medio.copy(localPath = "fisico/${medio.id}")
        }
        medios.add(guardado)
        archivosGuardados[guardado.localPath!!] = contenido
        actualizarPendientes()
        return Result.Success(guardado)
    }

    override suspend fun obtenerPorId(medioId: String): Medio? =
        medios.firstOrNull { it.id == medioId }

    override suspend fun obtenerDelReporte(reporteId: String): List<Medio> =
        medios.filter { it.reporteId == reporteId }

    override fun observarDelReporte(reporteId: String): Flow<List<Medio>> =
        MutableStateFlow(medios.filter { it.reporteId == reporteId })

    override fun observarPendientes(custodioUid: String): Flow<Int> = pendientes

    override fun observarPendientesMedios(custodioUid: String): Flow<List<Medio>> = pendientesMedios

    override suspend fun etiquetar(
        medioId: String,
        etiquetaId: String,
        ubicacionTexto: String?,
    ): Result<Medio> {
        val indice = medios.indexOfFirst { it.id == medioId }
        if (indice == -1) {
            return Result.Failure(com.ai27.app.custodio.domain.error.DominioError.MedioNoEncontrado)
        }
        val anterior = medios[indice]
        val actualizado = anterior.copy(
            etiqueta = etiquetaId,
            ubicacionTexto = ubicacionTexto ?: anterior.ubicacionTexto,
        )
        medios[indice] = actualizado
        return Result.Success(actualizado)
    }

    override suspend fun actualizarGps(
        medioId: String,
        gps: com.ai27.app.custodio.domain.model.Gps,
    ): Result<Medio> {
        val indice = medios.indexOfFirst { it.id == medioId }
        if (indice == -1) {
            return Result.Failure(com.ai27.app.custodio.domain.error.DominioError.MedioNoEncontrado)
        }
        val anterior = medios[indice]
        // Refleja la query real: solo adjunta mientras sigue PENDIENTE.
        val actualizado = if (anterior.estadoSubida == EstadoSubida.PENDIENTE) {
            anterior.copy(gps = gps)
        } else {
            anterior
        }
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
        actualizarPendientes()
        return Result.Success(actualizado)
    }

    override suspend fun marcarSubidaCompleta(
        medioId: String,
        storagePath: String,
        sha256: String,
    ): Result<Medio> {
        val indice = medios.indexOfFirst { it.id == medioId }
        if (indice == -1) {
            return Result.Failure(com.ai27.app.custodio.domain.error.DominioError.MedioNoEncontrado)
        }
        val anterior = medios[indice]
        val actualizado = anterior.copy(
            estadoSubida = EstadoSubida.COMPLETA,
            storagePath = storagePath,
            sha256 = sha256,
            localPath = null, // el archivo grande se borra al subir; la miniatura queda
        )
        medios[indice] = actualizado
        archivosGuardados.remove(anterior.localPath)
        actualizarPendientes()
        return Result.Success(actualizado)
    }

    override suspend fun obtenerPendientes(): List<Medio> =
        medios.filter {
            it.reporteId.isNotBlank() &&
                it.estadoSubida != EstadoSubida.COMPLETA &&
                it.estadoSubida != EstadoSubida.FALLIDA
        }

    override suspend fun borrarPendiente(medioId: String): Result<Medio> {
        val medio = medios.firstOrNull { it.id == medioId }
            ?: return Result.Failure(com.ai27.app.custodio.domain.error.DominioError.MedioNoEncontrado)
        if (medio.estadoSubida != EstadoSubida.PENDIENTE) {
            return Result.Failure(com.ai27.app.custodio.domain.error.DominioError.MedioEnSubida)
        }
        archivosGuardados.remove(medio.localPath)
        medio.thumbnailPath?.let { archivosGuardados.remove(it) }
        medios.remove(medio)
        actualizarPendientes()
        return Result.Success(medio)
    }
}