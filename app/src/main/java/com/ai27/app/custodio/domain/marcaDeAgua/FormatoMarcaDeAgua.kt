package com.ai27.app.custodio.domain.marcaDeAgua

import com.ai27.app.custodio.domain.model.Gps
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formateador puro de los textos de la marca de agua. Sin dependencias de Android, para que
 * la lógica de formato (que es donde está el patrón de fecha y la precisión de las coords)
 * se pruebe en JVM sin dispositivo.
 */
object FormatoMarcaDeAgua {

    // Patrón fijo pedido por producto: `YYYY/MM/DD HH:MM:SS`. `uuuu` (no `yyyy`) es el año
    // de la era en java.time; con padding de ceros en todos los campos (01..12, 00..23...).
    private val PATRON_FECHA_HORA: DateTimeFormatter =
        DateTimeFormatter.ofPattern("uuuu/MM/dd HH:mm:ss", Locale.US)

    /**
     * Fecha y hora de captura en **hora local del dispositivo**. El [zona] por defecto es la
     * zona del sistema (`ZoneId.systemDefault()`); se deja inyectable para poder fijarla en
     * las pruebas.
     */
    fun fechaHora(instante: Instant, zona: ZoneId = ZoneId.systemDefault()): String =
        PATRON_FECHA_HORA.format(instante.atZone(zona))

    /**
     * Coordenadas en **grados decimales**, 6 decimales con signo (p. ej.
     * `19.432608, -99.133209`), o `null` cuando no hubo fix de GPS. `Locale.US` fuerza el
     * punto decimal: con una locale de coma el texto saldría `19,432608` y rompería la
     * lectura del par lat/lng.
     */
    fun coordenadas(gps: Gps?): String? {
        if (gps == null) return null
        return "%.6f, %.6f".format(Locale.US, gps.lat, gps.lng)
    }

    /**
     * Arma el modelo completo. [llevaMapa] solo puede ser `true` cuando hay coordenadas: sin
     * ubicación no hay punto que mapear.
     */
    fun de(
        instante: Instant,
        gps: Gps?,
        zona: ZoneId = ZoneId.systemDefault(),
    ): MarcaDeAgua {
        val coords = coordenadas(gps)
        return MarcaDeAgua(
            fechaHora = fechaHora(instante, zona),
            coordenadas = coords,
            llevaMapa = coords != null,
        )
    }
}
