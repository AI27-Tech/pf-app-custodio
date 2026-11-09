package com.ai27.app.custodio.data.remote

import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.OrigenMedio
import com.ai27.app.custodio.domain.model.TipoMedio
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class RutasStorageTest {

    private val webp = "RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray(Charsets.US_ASCII)
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())

    private fun medio(tipo: TipoMedio, contentType: String) = Medio(
        id = "m-1",
        reporteId = "r-1",
        tipo = tipo,
        origen = if (tipo == TipoMedio.FOTO) OrigenMedio.CAMARA else OrigenMedio.GRABADORA,
        createdAtDevice = Instant.parse("2026-11-09T12:00:00Z"),
        contentType = contentType,
        estadoSubida = EstadoSubida.PENDIENTE,
    )

    @Test
    fun `la extension del original sale del contentType`() {
        assertEquals("medios/r-1/m-1.webp", RutasStorage.de(medio(TipoMedio.FOTO, "image/webp"), webp).media)
        assertEquals("medios/r-1/m-1.jpg", RutasStorage.de(medio(TipoMedio.FOTO, "image/jpeg"), jpeg).media)
        assertEquals("medios/r-1/m-1.m4a", RutasStorage.de(medio(TipoMedio.AUDIO, "audio/mp4"), null).media)
    }

    @Test
    fun `la miniatura toma tipo y extension de sus propios bytes`() {
        // Una pendiente del 1.0.3 revelada como WebP conserva su miniatura JPEG, y al revés.
        assertEquals("medios/r-1/m-1-min.jpg", RutasStorage.de(medio(TipoMedio.FOTO, "image/webp"), jpeg).miniatura)
        assertEquals("medios/r-1/m-1-min.webp", RutasStorage.de(medio(TipoMedio.FOTO, "image/jpeg"), webp).miniatura)
        assertEquals("image/webp", RutasStorage.contentTypeMiniatura(webp))
        assertEquals("image/jpeg", RutasStorage.contentTypeMiniatura(jpeg))
    }

    @Test
    fun `sin miniatura reconocible no hay ruta de miniatura`() {
        assertNull(RutasStorage.de(medio(TipoMedio.FOTO, "image/webp"), ByteArray(16)).miniatura)
        assertNull(RutasStorage.contentTypeMiniatura(ByteArray(16)))
        assertNull(RutasStorage.de(medio(TipoMedio.FOTO, "image/webp"), null).miniatura)
        assertNull(RutasStorage.de(medio(TipoMedio.AUDIO, "audio/mp4"), jpeg).miniatura)
    }

    @Test
    fun `las rutas son deterministas entre reintentos`() {
        val foto = medio(TipoMedio.FOTO, "image/webp")
        assertEquals(RutasStorage.de(foto, webp), RutasStorage.de(foto, webp.copyOf()))
    }

    @Test
    fun `un contentType desconocido es un error claro`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            RutasStorage.de(medio(TipoMedio.FOTO, "image/png"), null)
        }
        assertEquals("contentType sin extensión de Storage: image/png", error.message)
    }
}
