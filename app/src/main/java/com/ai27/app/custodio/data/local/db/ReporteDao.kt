package com.ai27.app.custodio.data.local.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReporteDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertar(reporte: ReporteEntity): Long

    @Query("SELECT * FROM reportes WHERE custodioUid = :custodioUid AND ciclo = 'ACTIVO' LIMIT 1")
    suspend fun obtenerActivo(custodioUid: String): ReporteEntity?

    @Query("SELECT * FROM reportes WHERE custodioUid = :custodioUid AND ciclo = 'ACTIVO' LIMIT 1")
    fun observarActivo(custodioUid: String): Flow<ReporteEntity?>

    @Query("SELECT * FROM reportes WHERE id = :id LIMIT 1")
    suspend fun obtenerPorId(id: String): ReporteEntity?

    @Query("SELECT * FROM reportes WHERE id = :id LIMIT 1")
    fun observarPorId(id: String): Flow<ReporteEntity?>

    @Query(
        "SELECT * FROM reportes WHERE custodioUid = :custodioUid " +
            "AND ciclo IN ('CERRADO','DESCARTADO') AND fechaLocal >= :fechaDesde " +
            "ORDER BY createdAtDevice DESC",
    )
    fun observarHistorial(custodioUid: String, fechaDesde: String): Flow<List<ReporteEntity>>

    @Query(
        "UPDATE reportes SET ciclo = 'CERRADO', closedAt = :closedAt, comentario = :comentario, " +
            "hayNovedad = :hayNovedad, " +
            "mediosEsperados = :mediosEsperados, fotos = :fotos, audios = :audios, durationMsTotal = :durationMsTotal " +
            "WHERE id = :id AND ciclo = 'ACTIVO'",
    )
    suspend fun cerrar(
        id: String,
        closedAt: Long,
        comentario: String?,
        hayNovedad: Boolean,
        mediosEsperados: Int,
        fotos: Int,
        audios: Int,
        durationMsTotal: Long,
    ): Int

    @Query(
        "UPDATE reportes SET ciclo = 'DESCARTADO', motivoDescarte = :motivo, closedAt = :closedAt " +
            "WHERE id = :id AND ciclo = 'ACTIVO'",
    )
    suspend fun descartar(id: String, closedAt: Long, motivo: String): Int

    @Query(
        "UPDATE reportes SET subida = :subida, mediosSubidos = :mediosSubidos, completo = :completo " +
            "WHERE id = :id",
    )
    suspend fun actualizarSubida(id: String, subida: String, mediosSubidos: Int, completo: Boolean): Int

    @Query("SELECT * FROM reportes WHERE subida != 'COMPLETA' ORDER BY createdAtDevice ASC")
    suspend fun obtenerPendientesSubida(): List<ReporteEntity>
}