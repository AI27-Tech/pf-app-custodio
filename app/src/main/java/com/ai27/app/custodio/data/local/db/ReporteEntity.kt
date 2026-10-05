package com.ai27.app.custodio.data.local.db

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "reportes")
data class ReporteEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "placas") val placas: String,
    @ColumnInfo(name = "placasCapturadas") val placasCapturadas: String,
    @ColumnInfo(name = "custodioUid") val custodioUid: String,
    @ColumnInfo(name = "custodioCorreo") val custodioCorreo: String,
    @ColumnInfo(name = "clienteId") val clienteId: String,
    @ColumnInfo(name = "deviceId") val deviceId: String,
    @ColumnInfo(name = "fechaLocal") val fechaLocal: String,
    @ColumnInfo(name = "createdAtDevice") val createdAtDevice: Long,
    @ColumnInfo(name = "ciclo") val ciclo: String,
    @ColumnInfo(name = "subida") val subida: String,
    @ColumnInfo(name = "completo") val completo: Boolean,
    @ColumnInfo(name = "mediosEsperados") val mediosEsperados: Int?,
    @ColumnInfo(name = "mediosSubidos") val mediosSubidos: Int,
    @ColumnInfo(name = "fotos") val fotos: Int,
    @ColumnInfo(name = "audios") val audios: Int,
    @ColumnInfo(name = "durationMsTotal") val durationMsTotal: Long,
    @ColumnInfo(name = "schemaVersion") val schemaVersion: Int,
    @ColumnInfo(name = "closedAt") val closedAt: Long?,
    @ColumnInfo(name = "comentario") val comentario: String?,
    @ColumnInfo(name = "hayNovedad") val hayNovedad: Boolean,
    @ColumnInfo(name = "motivoDescarte") val motivoDescarte: String?,
)