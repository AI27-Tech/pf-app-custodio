package com.ai27.app.custodio.domain.util

import java.time.Instant
import java.time.ZoneId

private val ZONA_MEXICO = ZoneId.of("America/Mexico_City")

fun fechaLocalDe(instante: Instant): String =
    instante.atZone(ZONA_MEXICO).toLocalDate().toString()

fun fechaDesdeUltimos30Dias(hoy: Instant): String =
    hoy.atZone(ZONA_MEXICO).toLocalDate().minusDays(29).toString()

fun normalizarPlaca(placas: String): String =
    placas.uppercase().filter { it.isLetterOrDigit() }