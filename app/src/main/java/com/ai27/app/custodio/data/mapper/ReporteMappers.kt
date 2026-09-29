package com.ai27.app.custodio.data.mapper

import com.ai27.app.custodio.data.local.db.ReporteEntity
import com.ai27.app.custodio.domain.model.Ciclo
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Reporte
import com.ai27.app.custodio.domain.model.ResumenMedios
import java.time.Instant

fun ReporteEntity.aDominio(): Reporte = Reporte(
    id = id,
    placas = placas,
    placasCapturadas = placasCapturadas,
    custodioUid = custodioUid,
    custodioCorreo = custodioCorreo,
    clienteId = clienteId,
    deviceId = deviceId,
    fechaLocal = fechaLocal,
    createdAtDevice = Instant.ofEpochMilli(createdAtDevice),
    ciclo = Ciclo.valueOf(ciclo),
    subida = EstadoSubida.valueOf(subida),
    completo = completo,
    mediosEsperados = mediosEsperados,
    mediosSubidos = mediosSubidos,
    resumenMedios = ResumenMedios(
        fotos = fotos,
        audios = audios,
        durationMsTotal = durationMsTotal,
    ),
    schemaVersion = schemaVersion,
    closedAt = closedAt?.let(Instant::ofEpochMilli),
    comentario = comentario,
    hayNovedad = hayNovedad,
    motivoDescarte = motivoDescarte,
)

fun Reporte.aEntidad(): ReporteEntity = ReporteEntity(
    id = id,
    placas = placas,
    placasCapturadas = placasCapturadas,
    custodioUid = custodioUid,
    custodioCorreo = custodioCorreo,
    clienteId = clienteId,
    deviceId = deviceId,
    fechaLocal = fechaLocal,
    createdAtDevice = createdAtDevice.toEpochMilli(),
    ciclo = ciclo.name,
    subida = subida.name,
    completo = completo,
    mediosEsperados = mediosEsperados,
    mediosSubidos = mediosSubidos,
    fotos = resumenMedios.fotos,
    audios = resumenMedios.audios,
    durationMsTotal = resumenMedios.durationMsTotal,
    schemaVersion = schemaVersion,
    closedAt = closedAt?.toEpochMilli(),
    comentario = comentario,
    hayNovedad = hayNovedad,
    motivoDescarte = motivoDescarte,
)