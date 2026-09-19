package com.ai27.app.custodio.domain.repository

import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Medio
import kotlinx.coroutines.flow.Flow

interface MedioRepository {
    suspend fun guardar(medio: Medio, contenido: ByteArray, miniatura: ByteArray? = null): Result<Medio>
    suspend fun obtenerPorId(medioId: String): Medio?
    suspend fun obtenerDelReporte(reporteId: String): List<Medio>
    fun observarDelReporte(reporteId: String): Flow<List<Medio>>
    fun observarPendientes(custodioUid: String): Flow<Int>
    suspend fun etiquetar(medioId: String, etiquetaId: String, ubicacionTexto: String? = null): Result<Medio>
    suspend fun actualizarEstadoSubida(medioId: String, estado: EstadoSubida): Result<Medio>
    suspend fun borrarPendiente(medioId: String): Result<Medio>
}