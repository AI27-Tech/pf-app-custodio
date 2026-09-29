package com.ai27.app.custodio.data.mapper

import com.ai27.app.custodio.data.remote.RutasRemotas
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.Reporte
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue

// Mapeo dominio -> documento Firestore. Es el único lugar que conoce el contrato de
// datos del PLAN §5 (nombres en español para negocio, inglés para metadatos técnicos).

fun Reporte.aDocumentoFirestore(): Map<String, Any?> = mapOf(
    "placas" to placas,
    "placasCapturadas" to placasCapturadas,
    "custodioUid" to custodioUid,
    // Contrato Firestore: la identidad visible del custodio es el CORREO (plan de correo).
    // Punto de coordinación cross-repo: el panel del analista debe leer `custodioCorreo`.
    "custodioCorreo" to custodioCorreo,
    "clienteId" to clienteId,
    "deviceId" to deviceId,
    "fechaLocal" to fechaLocal,
    "createdAtDevice" to Timestamp(createdAtDevice.epochSecond, createdAtDevice.nano),
    "ciclo" to ciclo.name,
    "subida" to subida.name,
    "completo" to completo,
    "mediosEsperados" to mediosEsperados,
    "mediosSubidos" to mediosSubidos,
    "resumenMedios" to mapOf(
        "fotos" to resumenMedios.fotos,
        "audios" to resumenMedios.audios,
        "durationMsTotal" to resumenMedios.durationMsTotal,
    ),
    "schemaVersion" to schemaVersion,
    "closedAt" to closedAt?.let { Timestamp(it.epochSecond, it.nano) },
    "comentario" to comentario,
    "hayNovedad" to hayNovedad,
    "motivoDescarte" to motivoDescarte,
    "createdAtServer" to FieldValue.serverTimestamp(),
)

fun Medio.aDocumentoFirestore(reporte: Reporte, rutas: RutasRemotas): Map<String, Any?> = mapOf(
    "reporteId" to reporteId,
    "tipo" to tipo.name,
    "etiquetas" to etiquetas,
    "storagePath" to rutas.media,
    "thumbnailPath" to rutas.miniatura,
    "createdAtDevice" to Timestamp(createdAtDevice.epochSecond, createdAtDevice.nano),
    "gps" to gps?.let {
        mapOf(
            "lat" to it.lat,
            "lng" to it.lng,
            "accuracyM" to it.accuracyM,
            "ageMs" to it.ageMs,
        )
    },
    "ubicacionTexto" to ubicacionTexto,
    "comentario" to comentario,
    "contentType" to contentType,
    "sizeBytes" to sizeBytes,
    "durationMs" to durationMs,
    "sha256" to sha256,
    "origen" to origen.name,
    // Desnormalizado del reporte para que el documento se lea solo (PLAN §5.1).
    "placas" to reporte.placas,
    "custodioUid" to reporte.custodioUid,
    "clienteId" to reporte.clienteId,
    "fechaLocal" to reporte.fechaLocal,
    "schemaVersion" to reporte.schemaVersion,
    "createdAtServer" to FieldValue.serverTimestamp(),
)

// Firestore no admite valores null en update(); se filtran antes de enviar.
fun Map<String, Any?>.sinNulos(): Map<String, Any?> = filterValues { it != null }