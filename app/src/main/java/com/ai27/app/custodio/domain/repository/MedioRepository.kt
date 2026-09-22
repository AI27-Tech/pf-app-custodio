package com.ai27.app.custodio.domain.repository

import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Gps
import com.ai27.app.custodio.domain.model.Medio
import kotlinx.coroutines.flow.Flow

interface MedioRepository {
    suspend fun guardar(medio: Medio, contenido: ByteArray, miniatura: ByteArray? = null): Result<Medio>
    suspend fun obtenerPorId(medioId: String): Medio?
    suspend fun obtenerDelReporte(reporteId: String): List<Medio>
    fun observarDelReporte(reporteId: String): Flow<List<Medio>>
    fun observarPendientes(custodioUid: String): Flow<Int>
    fun observarPendientesMedios(custodioUid: String): Flow<List<Medio>>
    suspend fun etiquetar(medioId: String, etiquetas: List<String>, ubicacionTexto: String? = null): Result<Medio>
    suspend fun actualizarGps(medioId: String, gps: Gps): Result<Medio>
    suspend fun actualizarEstadoSubida(medioId: String, estado: EstadoSubida): Result<Medio>
    suspend fun marcarSubidaCompleta(medioId: String, storagePath: String, sha256: String): Result<Medio>
    suspend fun borrarPendiente(medioId: String): Result<Medio>

    /**
     * Cola de subida del custodio. Excluye los medios sin **etiqueta de Situación** —que
     * no deben salir sin clasificar— y se acota por custodio para no tocar la evidencia
     * de quien usó el equipo antes (hallazgos E3 y E6).
     */
    suspend fun obtenerPendientes(custodioUid: String): List<Medio>
}