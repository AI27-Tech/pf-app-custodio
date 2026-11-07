package com.ai27.app.custodio

import com.ai27.app.custodio.data.remote.aTextoPersistible
import com.ai27.app.custodio.data.repository.ETIQUETAS_SITUACION_IDS
import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.repository.MedioRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeMedioRepository(
    val medios: MutableList<Medio> = mutableListOf(),
    val archivosGuardados: MutableMap<String, ByteArray> = mutableMapOf(),
) : MedioRepository {

    private val pendientes = MutableStateFlow(0)
    private val pendientesMedios = MutableStateFlow<List<Medio>>(emptyList())
    private val sinEtiquetar = MutableStateFlow(0)
    private val enCola = MutableStateFlow(0)
    private val fallidos = MutableStateFlow(0)

    /**
     * Espejo observable de [medios]. Room devuelve un `Flow` **vivo** que reemite en cada
     * escritura; este fake devolvía un `MutableStateFlow` recién creado con un snapshot,
     * así que quien se suscribía antes de la primera captura no volvía a ver nada y la
     * rejilla quedaba vacía para siempre. Se publica aquí y se reemite en toda mutación.
     */
    private val todos = MutableStateFlow<List<Medio>>(emptyList())

    init {
        // El constructor admite una lista sembrada; el espejo tiene que arrancar con ella.
        actualizarPendientes()
    }

    fun actualizarPendientes() {
        pendientes.value = medios.count { it.estadoSubida != EstadoSubida.COMPLETA }
        pendientesMedios.value = medios.filter { it.estadoSubida != EstadoSubida.COMPLETA }
        sinEtiquetar.value = medios.count {
            !conSituacion(it) && it.estadoSubida != EstadoSubida.COMPLETA
        }
        enCola.value = medios.count {
            conSituacion(it) && it.estadoSubida in setOf(EstadoSubida.PENDIENTE, EstadoSubida.EN_PROGRESO)
        }
        fallidos.value = medios.count { it.estadoSubida == EstadoSubida.FALLIDA }
        todos.value = medios.toList()
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

    override suspend fun leerOriginal(medioId: String): ByteArray? {
        val ruta = medios.firstOrNull { it.id == medioId }?.localPath ?: return null
        return archivosGuardados[ruta]
    }

    override suspend fun reescribirOriginal(medioId: String, bytes: ByteArray): Result<Medio> {
        val indice = medios.indexOfFirst { it.id == medioId }
        if (indice == -1) {
            return Result.Failure(com.ai27.app.custodio.domain.error.DominioError.MedioNoEncontrado)
        }
        val anterior = medios[indice]
        // Refleja la query real: solo reescribe mientras sigue PENDIENTE y hay ruta local.
        if (anterior.estadoSubida != EstadoSubida.PENDIENTE || anterior.localPath == null) {
            return Result.Failure(com.ai27.app.custodio.domain.error.DominioError.MedioNoEncontrado)
        }
        archivosGuardados[anterior.localPath] = bytes
        val actualizado = anterior.copy(sizeBytes = bytes.size.toLong())
        medios[indice] = actualizado
        actualizarPendientes()
        return Result.Success(actualizado)
    }

    override suspend fun guardarRevelado(medioId: String, bytes: ByteArray, contentType: String): Result<Medio> {
        val indice = medios.indexOfFirst { it.id == medioId }
        if (indice == -1) {
            return Result.Failure(com.ai27.app.custodio.domain.error.DominioError.MedioNoEncontrado)
        }
        val anterior = medios[indice]
        // Refleja MedioDao.marcarRevelada: solo mientras sigue PENDIENTE y hay ruta local.
        if (anterior.estadoSubida != EstadoSubida.PENDIENTE || anterior.localPath == null) {
            return Result.Failure(com.ai27.app.custodio.domain.error.DominioError.MedioNoEncontrado)
        }
        archivosGuardados[anterior.localPath] = bytes
        val actualizado = anterior.copy(sizeBytes = bytes.size.toLong(), contentType = contentType, revelada = true)
        medios[indice] = actualizado
        actualizarPendientes()
        return Result.Success(actualizado)
    }

    override fun observarDelReporte(reporteId: String): Flow<List<Medio>> =
        todos.map { lista -> lista.filter { it.reporteId == reporteId } }

    override fun observarPendientes(custodioUid: String): Flow<Int> = pendientes

    override fun observarPendientesMedios(custodioUid: String): Flow<List<Medio>> = pendientesMedios

    override fun observarSinEtiquetar(custodioUid: String): Flow<Int> = sinEtiquetar

    override fun observarEnCola(custodioUid: String): Flow<Int> = enCola

    override fun observarFallidos(custodioUid: String): Flow<Int> = fallidos

    override suspend fun etiquetar(
        medioId: String,
        etiquetas: List<String>,
        ubicacionTexto: String?,
    ): Result<Medio> {
        val indice = medios.indexOfFirst { it.id == medioId }
        if (indice == -1) {
            return Result.Failure(com.ai27.app.custodio.domain.error.DominioError.MedioNoEncontrado)
        }
        val anterior = medios[indice]
        val actualizado = anterior.copy(
            etiquetas = etiquetas,
            ubicacionTexto = ubicacionTexto ?: anterior.ubicacionTexto,
        )
        medios[indice] = actualizado
        actualizarPendientes()
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
        actualizarPendientes()
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
            ultimoError = null,
            intentosSubida = 0,
            // localPath NO se limpia: el UPDATE del DAO solo toca estadoSubida,
            // storagePath, sha256 y el diagnóstico (ultimoError/intentosSubida). El
            // archivo grande se borra del disco, pero la columna conserva la ruta. Ver
            // el contrato en ContratoRepositoriosTest.
        )
        medios[indice] = actualizado
        // El archivo físico sí desaparece, como en RoomMedioRepository.
        archivosGuardados.remove(anterior.localPath)
        actualizarPendientes()
        return Result.Success(actualizado)
    }

    override suspend fun registrarFallo(
        medioId: String,
        error: com.ai27.app.custodio.domain.error.DominioError,
        intentos: Int,
    ): Result<Medio> {
        val indice = medios.indexOfFirst { it.id == medioId }
        if (indice == -1) {
            return Result.Failure(com.ai27.app.custodio.domain.error.DominioError.MedioNoEncontrado)
        }
        val actualizado = medios[indice].copy(
            ultimoError = error.aTextoPersistible(),
            intentosSubida = intentos,
        )
        medios[indice] = actualizado
        actualizarPendientes()
        return Result.Success(actualizado)
    }

    override suspend fun obtenerPendientes(custodioUid: String): List<Medio> =
        // RETIRO_ETIQUETADO (Task 3): mismo filtro que MedioDao.obtenerPendientes tras
        // retirar el candado: del custodio y no COMPLETA (FALLIDA incluida, para que la
        // reconciliación los reintente). Ya NO se exige Situación. El filtro conSituacion
        // queda comentado para reactivarlo con el etiquetado.
        medios.filter {
            it.estadoSubida != EstadoSubida.COMPLETA &&
                // conSituacion(it) &&
                esDelCustodio(it.reporteId, custodioUid)
        }

    private fun conSituacion(medio: Medio): Boolean =
        medio.etiquetas.any { etiqueta -> etiqueta in ETIQUETAS_SITUACION_IDS }

    /**
     * El fake de medios no conoce los reportes, así que para poder acotar por custodio las
     * pruebas que ejercitan el aislamiento registran aquí a quién pertenece cada reporte.
     * Un reporteId sin registrar se considera del custodio consultado, para no obligar a
     * las demás pruebas a llenar el mapa.
     */
    val custodioPorReporte: MutableMap<String, String> = mutableMapOf()

    private fun esDelCustodio(reporteId: String, custodioUid: String): Boolean =
        custodioPorReporte[reporteId]?.let { it == custodioUid } ?: true

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