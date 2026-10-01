package com.ai27.app.custodio.domain.marcaDeAgua

import com.ai27.app.custodio.domain.model.Gps
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatoMarcaDeAguaTest {

    @Test
    fun `la fecha usa el patron YYYY-MM-DD HH-MM-SS con padding de ceros`() {
        // 2026-01-05T15:03:07Z; en una zona fija +00:00 debe salir tal cual, con los ceros
        // a la izquierda en mes, día, hora, minuto y segundo.
        val instante = Instant.parse("2026-01-05T09:03:07Z")

        val texto = FormatoMarcaDeAgua.fechaHora(instante, ZoneOffset.UTC)

        assertEquals("2026/01/05 09:03:07", texto)
    }

    @Test
    fun `la fecha se calcula en la zona local dada, no en UTC`() {
        // Medianoche UTC cae en el día anterior en Ciudad de México (-06:00): el formateo
        // debe reflejar la hora local, no la de UTC.
        val instante = Instant.parse("2026-03-10T05:30:00Z")
        val zonaMexico = ZoneId.of("America/Mexico_City")

        val texto = FormatoMarcaDeAgua.fechaHora(instante, zonaMexico)

        assertEquals("2026/03/09 23:30:00", texto)
    }

    @Test
    fun `las coordenadas van en grados decimales con 6 decimales y signo`() {
        val gps = Gps(lat = 19.432608, lng = -99.133209, accuracyM = 5.0, ageMs = 0L)

        val texto = FormatoMarcaDeAgua.coordenadas(gps)

        assertEquals("19.432608, -99.133209", texto)
    }

    @Test
    fun `las coordenadas se rellenan a 6 decimales cuando el valor trae menos`() {
        val gps = Gps(lat = 19.5, lng = -99.0, accuracyM = 5.0, ageMs = null)

        val texto = FormatoMarcaDeAgua.coordenadas(gps)

        assertEquals("19.500000, -99.000000", texto)
    }

    @Test
    fun `sin GPS las coordenadas son null`() {
        assertNull(FormatoMarcaDeAgua.coordenadas(null))
    }

    @Test
    fun `de() sin GPS arma fecha presente, coords null y sin mapa`() {
        val instante = Instant.parse("2026-01-05T09:03:07Z")

        val marca = FormatoMarcaDeAgua.de(instante, gps = null, zona = ZoneOffset.UTC)

        assertEquals("2026/01/05 09:03:07", marca.fechaHora)
        assertNull(marca.coordenadas)
        assertFalse("Sin GPS no hay punto que mapear", marca.llevaMapa)
    }

    @Test
    fun `de() con GPS arma coords y marca llevaMapa`() {
        val instante = Instant.parse("2026-01-05T09:03:07Z")
        val gps = Gps(lat = 19.432608, lng = -99.133209, accuracyM = 5.0, ageMs = 0L)

        val marca = FormatoMarcaDeAgua.de(instante, gps = gps, zona = ZoneOffset.UTC)

        assertEquals("19.432608, -99.133209", marca.coordenadas)
        assertTrue(marca.llevaMapa)
    }
}
