package com.ai27.app.custodio.data.local.db

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "medios")
data class MedioEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "reporteId") val reporteId: String,
    @ColumnInfo(name = "tipo") val tipo: String,
    @ColumnInfo(name = "origen") val origen: String,
    @ColumnInfo(name = "createdAtDevice") val createdAtDevice: Long,
    @ColumnInfo(name = "contentType") val contentType: String,
    @ColumnInfo(name = "estadoSubida") val estadoSubida: String,
    @ColumnInfo(name = "etiquetas") val etiquetas: List<String>,
    /** Denormalizado (PLAN §14 D1): la cola filtra `tieneSituacion = 1`. */
    @ColumnInfo(name = "tieneSituacion") val tieneSituacion: Boolean,
    @ColumnInfo(name = "localPath") val localPath: String?,
    @ColumnInfo(name = "storagePath") val storagePath: String?,
    @ColumnInfo(name = "thumbnailPath") val thumbnailPath: String?,
    @ColumnInfo(name = "gpsLat") val gpsLat: Double?,
    @ColumnInfo(name = "gpsLng") val gpsLng: Double?,
    @ColumnInfo(name = "gpsAccuracyM") val gpsAccuracyM: Double?,
    @ColumnInfo(name = "gpsAgeMs") val gpsAgeMs: Long?,
    @ColumnInfo(name = "ubicacionTexto") val ubicacionTexto: String?,
    @ColumnInfo(name = "comentario") val comentario: String?,
    @ColumnInfo(name = "sizeBytes") val sizeBytes: Long?,
    @ColumnInfo(name = "durationMs") val durationMs: Long?,
    @ColumnInfo(name = "sha256") val sha256: String?,
)