package com.ai27.app.custodio.data.local.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MedioDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertar(medio: MedioEntity): Long

    @Query("SELECT * FROM medios WHERE id = :id LIMIT 1")
    suspend fun obtenerPorId(id: String): MedioEntity?

    @Query("SELECT * FROM medios WHERE reporteId = :reporteId ORDER BY createdAtDevice ASC")
    suspend fun obtenerDelReporte(reporteId: String): List<MedioEntity>

    @Query("SELECT * FROM medios WHERE reporteId = :reporteId ORDER BY createdAtDevice ASC")
    fun observarDelReporte(reporteId: String): Flow<List<MedioEntity>>

    @Query("UPDATE medios SET etiqueta = :etiqueta, ubicacionTexto = COALESCE(:ubicacionTexto, ubicacionTexto) WHERE id = :id")
    suspend fun etiquetar(id: String, etiqueta: String, ubicacionTexto: String?): Int

    @Query("UPDATE medios SET estadoSubida = :estado WHERE id = :id")
    suspend fun actualizarEstadoSubida(id: String, estado: String): Int

    @Query("DELETE FROM medios WHERE id = :id AND estadoSubida = 'PENDIENTE'")
    suspend fun borrarPendiente(id: String): Int
}