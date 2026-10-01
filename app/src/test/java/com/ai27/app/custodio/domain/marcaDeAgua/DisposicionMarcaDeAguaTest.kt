package com.ai27.app.custodio.domain.marcaDeAgua

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DisposicionMarcaDeAguaTest {

    @Test
    fun `el texto escala con el lado menor del bitmap`() {
        val chico = DisposicionMarcaDeAgua.tamanoTextoPx(1080, 1920)
        val grande = DisposicionMarcaDeAgua.tamanoTextoPx(2160, 3840)

        assertTrue("Una foto más grande lleva texto más grande", grande > chico)
        // El lado menor de 1080 → ~28 px (1080 * 0.026).
        assertEquals(28.08f, chico, 0.01f)
    }

    @Test
    fun `el texto respeta un minimo para fotos muy chicas`() {
        // Lado menor 100: 100 * 0.026 = 2.6 px, por debajo del mínimo de 18.
        assertEquals(18f, DisposicionMarcaDeAgua.tamanoTextoPx(100, 100), 0.001f)
    }

    @Test
    fun `el tile del mapa es cuadrado y proporcional al lado menor`() {
        // Lado menor 1080 → 1080 * 0.22 = 237.
        assertEquals(237, DisposicionMarcaDeAgua.ladoMapaPx(1920, 1080))
    }

    @Test
    fun `el tile del mapa respeta un minimo para fotos muy chicas`() {
        assertEquals(96, DisposicionMarcaDeAgua.ladoMapaPx(100, 100))
    }

    @Test
    fun `el margen escala con el lado menor y respeta su minimo`() {
        assertEquals(21.6f, DisposicionMarcaDeAgua.margenPx(1920, 1080), 0.01f)
        assertEquals(12f, DisposicionMarcaDeAgua.margenPx(100, 100), 0.001f)
    }
}
