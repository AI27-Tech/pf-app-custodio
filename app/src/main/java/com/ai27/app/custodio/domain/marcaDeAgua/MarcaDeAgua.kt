package com.ai27.app.custodio.domain.marcaDeAgua

/**
 * Datos de la marca de agua que se graba sobre el JPEG original de una foto de evidencia,
 * ya formateados como texto. Es un modelo puro de dominio: no sabe nada de `Bitmap`, de
 * `Canvas` ni del mapa (ese `Bitmap?` lo aporta la capa Android al dibujar).
 *
 * - [fechaHora]: siempre presente, patrón `YYYY/MM/DD HH:MM:SS` en hora local del
 *   dispositivo. Va en la esquina superior izquierda.
 * - [coordenadas]: grados decimales con signo (p. ej. `19.432608, -99.133209`) o `null`
 *   cuando no hubo fix de GPS. Va en la esquina inferior derecha.
 * - [llevaMapa]: pista de intención para la capa de dibujo. Es `true` solo cuando hay
 *   coordenadas Y se espera un snapshot de mapa; que el snapshot llegue o no es decisión de
 *   la capa Android (si viene `null`, se omite el tile pero el resto se graba igual).
 */
data class MarcaDeAgua(
    val fechaHora: String,
    val coordenadas: String?,
    val llevaMapa: Boolean,
)
