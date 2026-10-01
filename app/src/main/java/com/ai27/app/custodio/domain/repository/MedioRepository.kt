package com.ai27.app.custodio.domain.repository

import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Gps
import com.ai27.app.custodio.domain.model.Medio
import kotlinx.coroutines.flow.Flow

interface MedioRepository {
    suspend fun guardar(medio: Medio, contenido: ByteArray, miniatura: ByteArray? = null): Result<Medio>
    suspend fun obtenerPorId(medioId: String): Medio?
    suspend fun obtenerDelReporte(reporteId: String): List<Medio>

    /** Marca de agua: bytes del archivo original del medio en disco, o `null` si no existe. */
    suspend fun leerOriginal(medioId: String): ByteArray?
    fun observarDelReporte(reporteId: String): Flow<List<Medio>>
    fun observarPendientes(custodioUid: String): Flow<Int>
    fun observarPendientesMedios(custodioUid: String): Flow<List<Medio>>

    /**
     * Desglose del badge de pendientes en sus tres componentes (U6): lo que falta por
     * etiquetar, lo que espera en cola y lo que quedó fallido. Son `COUNT` aditivos y por
     * custodio; los usa la ViewModel de pendientes para que el total del badge sea un solo
     * conteo y no la reconstrucción de la lista (R3).
     */
    fun observarSinEtiquetar(custodioUid: String): Flow<Int>
    fun observarEnCola(custodioUid: String): Flow<Int>
    fun observarFallidos(custodioUid: String): Flow<Int>
    suspend fun etiquetar(medioId: String, etiquetas: List<String>, ubicacionTexto: String? = null): Result<Medio>
    suspend fun actualizarGps(medioId: String, gps: Gps): Result<Medio>

    /**
     * Marca de agua: sobrescribe el archivo original del medio con [bytes] (el JPEG ya
     * estampado) y refresca `sizeBytes`. Solo surte efecto mientras el medio sigue
     * `PENDIENTE` —igual que [actualizarGps]—: una vez en subida, el documento remoto ya se
     * creó y no debe mutar. Devuelve `MedioNoEncontrado` si el medio se borró, ya salió, o
     * no tiene ruta local.
     */
    suspend fun reescribirOriginal(medioId: String, bytes: ByteArray): Result<Medio>
    suspend fun actualizarEstadoSubida(medioId: String, estado: EstadoSubida): Result<Medio>
    suspend fun marcarSubidaCompleta(medioId: String, storagePath: String, sha256: String): Result<Medio>

    /**
     * Persiste la causa y los intentos de un fallo permanente de subida (hallazgo C5).
     * El medio ya quedó `FALLIDA` por el pipeline; aquí solo se registra el diagnóstico
     * para poder mostrarlo y entender fallos que no se reintentan solos.
     */
    suspend fun registrarFallo(medioId: String, error: DominioError, intentos: Int): Result<Medio>
    suspend fun borrarPendiente(medioId: String): Result<Medio>

    /**
     * Cola de subida del custodio. Excluye los medios sin **etiqueta de Situación** —que
     * no deben salir sin clasificar— y se acota por custodio para no tocar la evidencia
     * de quien usó el equipo antes (hallazgos E3 y E6).
     */
    suspend fun obtenerPendientes(custodioUid: String): List<Medio>
}