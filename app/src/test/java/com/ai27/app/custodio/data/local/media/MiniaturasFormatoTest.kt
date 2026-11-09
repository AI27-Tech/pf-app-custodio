package com.ai27.app.custodio.data.local.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prueba JVM pura del detector de formato por magic bytes. `Bitmap.compress` no corre en
 * unit test JVM sin Robolectric (y el proyecto no lo usa), así que la cobertura del formato
 * WebP de la evidencia se afirma sobre esta función pura, que es la que define "es WebP".
 * El dibujo real sobre `Bitmap`/`Canvas` queda en `DibujanteMarcaDeAguaTest` (instrumentado).
 */
class MiniaturasFormatoTest {

    // "RIFF" (4) + tamaño (4) + "WEBP" (4): cabecera mínima de un contenedor WebP.
    private val cabeceraWebp = byteArrayOf(
        'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
        0x10, 0x00, 0x00, 0x00,
        'W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte(),
    )

    // SOI de JPEG: FF D8.
    private val cabeceraJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())

    @Test
    fun `reconoce un buffer WebP y no lo confunde con JPEG`() {
        assertTrue(Miniaturas.esWebp(cabeceraWebp))
        assertFalse(Miniaturas.esJpeg(cabeceraWebp))
    }

    @Test
    fun `reconoce un buffer JPEG y no lo confunde con WebP`() {
        assertTrue(Miniaturas.esJpeg(cabeceraJpeg))
        assertFalse(Miniaturas.esWebp(cabeceraJpeg))
    }

    @Test
    fun `RIFF sin WEBP no es WebP`() {
        // Un WAV también empieza con "RIFF"; solo el tag "WEBP" en offset 8 lo decide.
        val riffWav = byteArrayOf(
            'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
            0x10, 0x00, 0x00, 0x00,
            'W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte(),
        )
        assertFalse(Miniaturas.esWebp(riffWav))
    }

    @Test
    fun `un buffer corto o vacio no es ningun formato`() {
        assertFalse(Miniaturas.esWebp(ByteArray(0)))
        assertFalse(Miniaturas.esWebp(byteArrayOf('R'.code.toByte(), 'I'.code.toByte())))
        assertFalse(Miniaturas.esJpeg(ByteArray(0)))
    }
}
