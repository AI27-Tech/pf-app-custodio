package com.ai27.app.custodio.data.repository

import com.ai27.app.custodio.data.local.archivo.AlmacenArchivos
import com.ai27.app.custodio.data.local.db.EchoDatabase
import com.ai27.app.custodio.data.mapper.aDominio
import com.ai27.app.custodio.data.mapper.aEntidad
import com.ai27.app.custodio.data.remote.aTextoPersistible
import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
import com.ai27.app.custodio.domain.model.CategoriaEtiqueta
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Gps
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.repository.EtiquetaRepository
import com.ai27.app.custodio.domain.repository.MedioRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomMedioRepository(
    private val database: EchoDatabase,
    private val almacenArchivos: AlmacenArchivos,
    private val etiquetaRepository: EtiquetaRepository,
) : MedioRepository {

    private val dao = database.medioDao()

    override suspend fun guardar(medio: Medio, contenido: ByteArray, miniatura: ByteArray?): Result<Medio> {
        // Si el disco está lleno se corta aquí, sin fila en Room y sin archivo a medias
        // (hallazgo E4). Antes la IOException escapaba y tiraba la app en el disparo.
        val ruta = when (val r = almacenArchivos.guardar(medio.id, contenido)) {
            is Result.Success -> r.value
            is Result.Failure -> return r
        }
        val rutaMiniatura = if (miniatura != null) {
            when (val r = almacenArchivos.guardar("${medio.id}-min", miniatura)) {
                is Result.Success -> r.value
                is Result.Failure -> {
                    // El original ya está escrito: sin fila que lo referencie sería un
                    // huérfano invisible que nadie recolecta.
                    almacenArchivos.borrar(ruta)
                    return r
                }
            }
        } else {
            null
        }
        val entidad = medio
            .copy(localPath = ruta, thumbnailPath = rutaMiniatura)
            .aEntidad()
        return if (dao.insertar(entidad) != -1L) {
            Result.Success(entidad.aDominio())
        } else {
            almacenArchivos.borrar(ruta)
            rutaMiniatura?.let { r -> almacenArchivos.borrar(r) }
            Result.Failure(DominioError.Inesperado(IllegalStateException("Insert de medio ignorado")))
        }
    }

    override suspend fun obtenerPorId(medioId: String): Medio? =
        dao.obtenerPorId(medioId)?.aDominio()

    override suspend fun obtenerDelReporte(reporteId: String): List<Medio> =
        dao.obtenerDelReporte(reporteId).map { it.aDominio() }

    override suspend fun leerOriginal(medioId: String): ByteArray? {
        val ruta = dao.obtenerPorId(medioId)?.localPath ?: return null
        return almacenArchivos.leer(ruta)
    }

    override fun observarDelReporte(reporteId: String): Flow<List<Medio>> =
        dao.observarDelReporte(reporteId).map { lista -> lista.map { it.aDominio() } }

    override fun observarPendientes(custodioUid: String): Flow<Int> =
        dao.contarPendientes(custodioUid)

    override fun observarSinEtiquetar(custodioUid: String): Flow<Int> =
        dao.contarSinEtiquetar(custodioUid)

    override fun observarEnCola(custodioUid: String): Flow<Int> =
        dao.contarEnCola(custodioUid)

    override fun observarFallidos(custodioUid: String): Flow<Int> =
        dao.contarFallidos(custodioUid)

    override fun observarPendientesMedios(custodioUid: String): Flow<List<Medio>> =
        dao.observarPendientesDe(custodioUid).map { lista -> lista.map { it.aDominio() } }

    override suspend fun etiquetar(medioId: String, etiquetas: List<String>, ubicacionTexto: String?): Result<Medio> {
        // A4: qué cuenta como Situación lo define el catálogo activo (remoto con fallback
        // al set empaquetado), no un set fijo en código. La columna `tieneSituacion` se
        // recalcula acá, en la escritura, para que Room y la cola de subida usen el mismo
        // criterio que validó EtiquetarMedio.
        val catalogo = etiquetaRepository.obtener()
        val tieneSituacion = etiquetas.any { id ->
            catalogo.any { it.id == id && it.categoria == CategoriaEtiqueta.SITUACION }
        }
        val filas = dao.etiquetar(medioId, etiquetas, tieneSituacion, ubicacionTexto)
        val actualizado = dao.obtenerPorId(medioId)
        return if (filas == 1 && actualizado != null) {
            Result.Success(actualizado.aDominio())
        } else {
            Result.Failure(DominioError.MedioNoEncontrado)
        }
    }

    override suspend fun actualizarGps(medioId: String, gps: Gps): Result<Medio> {
        // Solo se adjunta la ubicación mientras el medio sigue PENDIENTE (la query lo
        // restringe): una vez EN_PROGRESO/COMPLETA el documento remoto ya se creó y no
        // debe mutar. Si el medio se borró o ya salió, se devuelve MedioNoEncontrado.
        dao.actualizarGps(medioId, gps.lat, gps.lng, gps.accuracyM, gps.ageMs)
        val actualizado = dao.obtenerPorId(medioId)
            ?: return Result.Failure(DominioError.MedioNoEncontrado)
        return Result.Success(actualizado.aDominio())
    }

    override suspend fun reescribirOriginal(medioId: String, bytes: ByteArray): Result<Medio> {
        val medio = dao.obtenerPorId(medioId)
            ?: return Result.Failure(DominioError.MedioNoEncontrado)
        // Si ya salió de PENDIENTE, el documento remoto se creó: no se reescribe el archivo.
        if (medio.estadoSubida != EstadoSubida.PENDIENTE.name) {
            return Result.Failure(DominioError.MedioNoEncontrado)
        }
        val ruta = medio.localPath
            ?: return Result.Failure(DominioError.MedioNoEncontrado)
        // guardar() usa writeBytes, que trunca/sobrescribe: la ruta local no cambia, solo su
        // contenido (el JPEG ya estampado). Si falla la escritura (disco lleno), se propaga.
        when (val r = almacenArchivos.guardar(nombreDesdeRuta(ruta), bytes)) {
            is Result.Success -> Unit
            is Result.Failure -> return r
        }
        val filas = dao.actualizarTamano(medioId, bytes.size.toLong())
        val actualizado = dao.obtenerPorId(medioId)
            ?: return Result.Failure(DominioError.MedioNoEncontrado)
        return if (filas == 1) {
            Result.Success(actualizado.aDominio())
        } else {
            Result.Failure(DominioError.MedioNoEncontrado)
        }
    }

    // El AlmacenArchivos trabaja con nombres relativos (guardar(nombre) devuelve la ruta
    // absoluta); el nombre del original es el id del medio, igual que en guardar().
    private fun nombreDesdeRuta(ruta: String): String = ruta.substringAfterLast('/')

    override suspend fun guardarRevelado(medioId: String, bytes: ByteArray, contentType: String): Result<Medio> {
        val medio = dao.obtenerPorId(medioId)
            ?: return Result.Failure(DominioError.MedioNoEncontrado)
        // Si ya salió de PENDIENTE, la subida tomó el archivo: no se reescribe.
        if (medio.estadoSubida != EstadoSubida.PENDIENTE.name) {
            return Result.Failure(DominioError.MedioNoEncontrado)
        }
        val ruta = medio.localPath
            ?: return Result.Failure(DominioError.MedioNoEncontrado)
        when (val r = almacenArchivos.guardar(nombreDesdeRuta(ruta), bytes)) {
            is Result.Success -> Unit
            is Result.Failure -> return r
        }
        val filas = dao.marcarRevelada(medioId, bytes.size.toLong(), contentType)
        val actualizado = dao.obtenerPorId(medioId)
            ?: return Result.Failure(DominioError.MedioNoEncontrado)
        return if (filas == 1) {
            Result.Success(actualizado.aDominio())
        } else {
            Result.Failure(DominioError.MedioNoEncontrado)
        }
    }


    override suspend fun actualizarEstadoSubida(medioId: String, estado: EstadoSubida): Result<Medio> {
        return if (dao.actualizarEstadoSubida(medioId, estado.name) == 1) {
            Result.Success(dao.obtenerPorId(medioId)!!.aDominio())
        } else {
            Result.Failure(DominioError.MedioNoEncontrado)
        }
    }

    override suspend fun marcarSubidaCompleta(
        medioId: String,
        storagePath: String,
        sha256: String,
    ): Result<Medio> {
        val medio = dao.obtenerPorId(medioId)
            ?: return Result.Failure(DominioError.MedioNoEncontrado)
        val filas = dao.marcarSubidaCompleta(medioId, storagePath, sha256)
        if (filas != 1) {
            return Result.Failure(DominioError.MedioNoEncontrado)
        }
        // El archivo original ya está en Storage; se borra local. La miniatura se conserva
        // porque la rejilla la muestra mientras el viaje sigue abierto.
        medio.localPath?.let { ruta -> almacenArchivos.borrar(ruta) }
        return Result.Success(dao.obtenerPorId(medioId)!!.aDominio())
    }

    override suspend fun registrarFallo(
        medioId: String,
        error: DominioError,
        intentos: Int,
    ): Result<Medio> {
        val mensaje = error.aTextoPersistible()
        val filas = dao.registrarFallo(medioId, mensaje, intentos)
        val actualizado = dao.obtenerPorId(medioId)
            ?: return Result.Failure(DominioError.MedioNoEncontrado)
        return if (filas == 1) {
            Result.Success(actualizado.aDominio())
        } else {
            Result.Failure(DominioError.MedioNoEncontrado)
        }
    }

    override suspend fun obtenerPendientes(custodioUid: String): List<Medio> =
        dao.obtenerPendientes(custodioUid).map { it.aDominio() }

    override suspend fun borrarPendiente(medioId: String): Result<Medio> {
        val medio = dao.obtenerPorId(medioId)
            ?: return Result.Failure(DominioError.MedioNoEncontrado)
        val filas = dao.borrarPendiente(medioId)
        return if (filas == 1) {
            medio.localPath?.let { ruta -> almacenArchivos.borrar(ruta) }
            medio.thumbnailPath?.let { ruta -> almacenArchivos.borrar(ruta) }
            Result.Success(medio.aDominio())
        } else {
            Result.Failure(DominioError.MedioEnSubida)
        }
    }
}