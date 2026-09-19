package com.ai27.app.custodio.data.repository

import com.ai27.app.custodio.data.local.archivo.AlmacenArchivos
import com.ai27.app.custodio.data.local.db.AppCustodioDatabase
import com.ai27.app.custodio.data.mapper.aDominio
import com.ai27.app.custodio.data.mapper.aEntidad
import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.repository.MedioRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomMedioRepository(
    private val database: AppCustodioDatabase,
    private val almacenArchivos: AlmacenArchivos,
) : MedioRepository {

    private val dao = database.medioDao()

    override suspend fun guardar(medio: Medio, contenido: ByteArray, miniatura: ByteArray?): Result<Medio> {
        val ruta = almacenArchivos.guardar(medio.id, contenido)
        val rutaMiniatura = miniatura?.let {
            almacenArchivos.guardar("${medio.id}-min", it)
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

    override fun observarDelReporte(reporteId: String): Flow<List<Medio>> =
        dao.observarDelReporte(reporteId).map { lista -> lista.map { it.aDominio() } }

    override fun observarPendientes(custodioUid: String): Flow<Int> =
        dao.contarPendientes(custodioUid)

    override suspend fun etiquetar(medioId: String, etiquetaId: String, ubicacionTexto: String?): Result<Medio> {
        val filas = dao.etiquetar(medioId, etiquetaId, ubicacionTexto)
        val actualizado = dao.obtenerPorId(medioId)
        return if (filas == 1 && actualizado != null) {
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