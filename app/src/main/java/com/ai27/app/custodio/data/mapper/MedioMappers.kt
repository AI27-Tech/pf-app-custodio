package com.ai27.app.custodio.data.mapper

import com.ai27.app.custodio.data.local.db.MedioEntity
import com.ai27.app.custodio.data.repository.ETIQUETAS_SITUACION_IDS
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Gps
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.OrigenMedio
import com.ai27.app.custodio.domain.model.TipoMedio
import java.time.Instant

fun MedioEntity.aDominio(): Medio = Medio(
    id = id,
    reporteId = reporteId,
    tipo = TipoMedio.valueOf(tipo),
    origen = OrigenMedio.valueOf(origen),
    createdAtDevice = Instant.ofEpochMilli(createdAtDevice),
    contentType = contentType,
    estadoSubida = EstadoSubida.valueOf(estadoSubida),
    etiquetas = etiquetas,
    localPath = localPath,
    storagePath = storagePath,
    thumbnailPath = thumbnailPath,
    gps = if (gpsLat != null && gpsLng != null) {
        Gps(
            lat = gpsLat,
            lng = gpsLng,
            accuracyM = gpsAccuracyM ?: 0.0,
            ageMs = gpsAgeMs,
        )
    } else {
        null
    },
    ubicacionTexto = ubicacionTexto,
    comentario = comentario,
    sizeBytes = sizeBytes,
    durationMs = durationMs,
    sha256 = sha256,
    ultimoError = ultimoError,
    intentosSubida = intentosSubida,
)

fun Medio.aEntidad(): MedioEntity = MedioEntity(
    id = id,
    reporteId = reporteId,
    tipo = tipo.name,
    origen = origen.name,
    createdAtDevice = createdAtDevice.toEpochMilli(),
    contentType = contentType,
    estadoSubida = estadoSubida.name,
    etiquetas = etiquetas,
    tieneSituacion = etiquetas.any { it in ETIQUETAS_SITUACION_IDS },
    localPath = localPath,
    storagePath = storagePath,
    thumbnailPath = thumbnailPath,
    gpsLat = gps?.lat,
    gpsLng = gps?.lng,
    gpsAccuracyM = gps?.accuracyM,
    gpsAgeMs = gps?.ageMs,
    ubicacionTexto = ubicacionTexto,
    comentario = comentario,
    sizeBytes = sizeBytes,
    durationMs = durationMs,
    sha256 = sha256,
    ultimoError = ultimoError,
    intentosSubida = intentosSubida,
)