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

    @Query(
        """
        SELECT COUNT(*) FROM medios m
        INNER JOIN reportes r ON m.reporteId = r.id
        WHERE r.custodioUid = :custodioUid AND m.estadoSubida != 'COMPLETA'
        """,
    )
    fun contarPendientes(custodioUid: String): Flow<Int>

    @Query("UPDATE medios SET etiqueta = :etiqueta, ubicacionTexto = COALESCE(:ubicacionTexto, ubicacionTexto) WHERE id = :id")
    suspend fun etiquetar(id: String, etiqueta: String, ubicacionTexto: String?): Int

    @Query(
        """
        UPDATE medios
        SET gpsLat = :lat, gpsLng = :lng, gpsAccuracyM = :accuracyM, gpsAgeMs = :ageMs
        WHERE id = :id AND estadoSubida = 'PENDIENTE'
        """,
    )
    suspend fun actualizarGps(id: String, lat: Double, lng: Double, accuracyM: Double, ageMs: Long?): Int

    @Query("UPDATE medios SET estadoSubida = :estado WHERE id = :id")
    suspend fun actualizarEstadoSubida(id: String, estado: String): Int

    @Query("UPDATE medios SET estadoSubida = 'COMPLETA', storagePath = :storagePath, sha256 = :sha256 WHERE id = :id")
    suspend fun marcarSubidaCompleta(id: String, storagePath: String, sha256: String): Int

    @Query(
        """
        SELECT m.* FROM medios m
        INNER JOIN reportes r ON m.reporteId = r.id
        WHERE r.custodioUid = :custodioUid AND m.estadoSubida != 'COMPLETA'
        ORDER BY m.createdAtDevice ASC
        """,
    )
    fun observarPendientesDe(custodioUid: String): Flow<List<MedioEntity>>

    @Query(
        """
        SELECT m.* FROM medios m
        INNER JOIN reportes r ON m.reporteId = r.id
        WHERE r.custodioUid = :custodioUid
          AND m.estadoSubida != 'COMPLETA'
          AND m.etiqueta IS NOT NULL
        ORDER BY m.createdAtDevice ASC
        """,
    )
    suspend fun obtenerPendientes(custodioUid: String): List<MedioEntity>

    @Query("DELETE FROM medios WHERE id = :id AND estadoSubida = 'PENDIENTE'")
    suspend fun borrarPendiente(id: String): Int
}