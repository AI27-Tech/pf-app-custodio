package com.ai27.app.custodio.ui.previews

import com.ai27.app.custodio.domain.model.Ciclo
import com.ai27.app.custodio.domain.model.Custodio
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Etiqueta
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.OrigenMedio
import com.ai27.app.custodio.domain.model.Reporte
import com.ai27.app.custodio.domain.model.ResumenMedios
import com.ai27.app.custodio.domain.model.TipoMedio
import java.time.Instant

/**
 * Datos de demostración para las vistas previas (C7).
 *
 * Solo viven en presentación: ningún ViewModel produce estos valores y ninguna prueba de
 * dominio los conoce. El fondo lo pone [com.ai27.app.custodio.ui.components.Vitrina].
 */
internal object DatosDemo {

    private val instante = Instant.parse("2026-09-21T18:30:00Z")

    private val instantCierre = instante.plusSeconds(3_600L)

    val custodio = Custodio(uid = "uid-demo", correo = "c.duarte@acme.mx")

    val etiquetas = listOf(
        Etiqueta(id = "e-docs", nombre = "Documentación", orden = 1, activo = true),
        Etiqueta(id = "e-viveres", nombre = "Víveres", orden = 2, activo = true),
        Etiqueta(id = "e-valores", nombre = "Valores", orden = 3, activo = true),
        Etiqueta(id = "e-inactiva", nombre = "Mascotas", orden = 4, activo = false),
    )

    val reporteActivo = Reporte(
        id = "r-activo",
        placas = "736-DBK",
        placasCapturadas = "736-DBK",
        custodioUid = custodio.uid,
        custodioCorreo = custodio.correo,
        clienteId = "cli-7",
        deviceId = "dev-7",
        fechaLocal = "lun 21 sep",
        createdAtDevice = instante,
        ciclo = Ciclo.ACTIVO,
        subida = EstadoSubida.EN_PROGRESO,
        completo = false,
        mediosEsperados = null,
        mediosSubidos = 2,
        resumenMedios = ResumenMedios(fotos = 2, audios = 1, durationMsTotal = 138_000L),
    )

    val reporteCerrado = Reporte(
        id = "r-cerrado",
        placas = "452-XKH",
        placasCapturadas = "452-XKH",
        custodioUid = custodio.uid,
        custodioCorreo = custodio.correo,
        clienteId = "cli-7",
        deviceId = "dev-7",
        fechaLocal = "sáb 19 sep",
        createdAtDevice = instante.minusSeconds(60 * 60 * 24 * 2),
        ciclo = Ciclo.CERRADO,
        subida = EstadoSubida.COMPLETA,
        completo = true,
        mediosEsperados = 3,
        mediosSubidos = 3,
        resumenMedios = ResumenMedios(fotos = 2, audios = 1, durationMsTotal = 96_000L),
        closedAt = instantCierre,
        comentario = "Entrega a tiempo, sin novedad.",
        hayNovedad = false,
    )

    val reporteDescartado = Reporte(
        id = "r-descartado",
        placas = "888-ZZZ",
        placasCapturadas = "888-ZZZ",
        custodioUid = custodio.uid,
        custodioCorreo = custodio.correo,
        clienteId = "cli-7",
        deviceId = "dev-7",
        fechaLocal = "vie 18 sep",
        createdAtDevice = instante.minusSeconds(60 * 60 * 24 * 3),
        ciclo = Ciclo.DESCARTADO,
        subida = EstadoSubida.COMPLETA,
        completo = false,
        mediosEsperados = 1,
        mediosSubidos = 1,
        resumenMedios = ResumenMedios(fotos = 1),
        closedAt = instantCierre,
        motivoDescarte = "Placa registrada equivocada.",
    )

    val mediosViaje = listOf(
        Medio(
            id = "m1",
            reporteId = reporteActivo.id,
            tipo = TipoMedio.FOTO,
            origen = OrigenMedio.CAMARA,
            createdAtDevice = instante,
            contentType = "image/jpeg",
            estadoSubida = EstadoSubida.COMPLETA,
            etiqueta = "e-docs",
            gps = null,
        ),
        Medio(
            id = "m2",
            reporteId = reporteActivo.id,
            tipo = TipoMedio.AUDIO,
            origen = OrigenMedio.GRABADORA,
            createdAtDevice = instante.plusSeconds(120L),
            contentType = "audio/mp4",
            estadoSubida = EstadoSubida.PENDIENTE,
            etiqueta = "e-viveres",
            durationMs = 138_000L,
        ),
        Medio(
            id = "m3",
            reporteId = reporteActivo.id,
            tipo = TipoMedio.FOTO,
            origen = OrigenMedio.CAMARA,
            createdAtDevice = instante.plusSeconds(300L),
            contentType = "image/jpeg",
            estadoSubida = EstadoSubida.PENDIENTE,
            etiqueta = null,
        ),
        Medio(
            id = "m4",
            reporteId = reporteActivo.id,
            tipo = TipoMedio.FOTO,
            origen = OrigenMedio.CAMARA,
            createdAtDevice = instante.plusSeconds(480L),
            contentType = "image/jpeg",
            estadoSubida = EstadoSubida.FALLIDA,
            etiqueta = "e-valores",
        ),
    )

    val mediosPendientes = listOf(
        Medio(
            id = "p1",
            reporteId = reporteActivo.id,
            tipo = TipoMedio.FOTO,
            origen = OrigenMedio.CAMARA,
            createdAtDevice = instante,
            contentType = "image/jpeg",
            estadoSubida = EstadoSubida.PENDIENTE,
            etiqueta = null,
        ),
        Medio(
            id = "p2",
            reporteId = reporteCerrado.id,
            tipo = TipoMedio.AUDIO,
            origen = OrigenMedio.GRABADORA,
            createdAtDevice = instante.minusSeconds(600L),
            contentType = "audio/mp4",
            estadoSubida = EstadoSubida.EN_PROGRESO,
            etiqueta = "e-docs",
            durationMs = 42_000L,
        ),
        Medio(
            id = "p3",
            reporteId = reporteCerrado.id,
            tipo = TipoMedio.FOTO,
            origen = OrigenMedio.CAMARA,
            createdAtDevice = instante.minusSeconds(1_500L),
            contentType = "image/jpeg",
            estadoSubida = EstadoSubida.FALLIDA,
            etiqueta = "e-viveres",
        ),
    )
}