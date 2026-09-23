package com.ai27.app.custodio.data.mapper

import com.ai27.app.custodio.data.remote.RutasRemotas
import com.ai27.app.custodio.domain.model.Ciclo
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.OrigenMedio
import com.ai27.app.custodio.domain.model.Reporte
import com.ai27.app.custodio.domain.model.ResumenMedios
import com.ai27.app.custodio.domain.model.TipoMedio
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Contrato del documento de `medios` en Firestore. Existe por el hallazgo **C2** de
 * `docs/AUDIT.md`: el mapper escribía `"sha256" to null` porque el pipeline le pasaba el
 * medio leído de Room ANTES de subir, y como `medios` es create-only en las reglas, el
 * hash quedaba nulo e irrellenable para siempre.
 */
class DocumentoMappersTest {

    private val fijo = Instant.parse("2026-09-18T15:00:00Z")
    private val rutas = RutasRemotas("medios/r-1/m-1.jpg", "medios/r-1/m-1-min.jpg")

    @Test
    fun `el documento del medio escribe el sha256 cuando el medio lo lleva`() {
        val documento = medio(sha256 = "a1b2c3").aDocumentoFirestore(reporte(), rutas)

        assertEquals(
            "C2: el sha debe viajar en el documento, no quedarse solo en Room",
            "a1b2c3",
            documento["sha256"],
        )
    }

    @Test
    fun `sin sha256 el campo se escribe nulo`() {
        val documento = medio(sha256 = null).aDocumentoFirestore(reporte(), rutas)

        assertNull(documento["sha256"])
    }

    @Test
    fun `el documento lleva las rutas de storage`() {
        val documento = medio(sha256 = "a1b2c3").aDocumentoFirestore(reporte(), rutas)

        assertEquals("medios/r-1/m-1.jpg", documento["storagePath"])
        assertEquals("medios/r-1/m-1-min.jpg", documento["thumbnailPath"])
    }

    private fun medio(sha256: String?): Medio = Medio(
        id = "m-1",
        reporteId = "r-1",
        tipo = TipoMedio.FOTO,
        origen = OrigenMedio.CAMARA,
        createdAtDevice = fijo,
        contentType = "image/jpeg",
        estadoSubida = EstadoSubida.PENDIENTE,
        etiquetas = listOf("sin-novedad"),
        sha256 = sha256,
    )

    private fun reporte(): Reporte = Reporte(
        id = "r-1",
        placas = "ABC-123",
        placasCapturadas = "ABC-123",
        custodioUid = "c-1",
        custodioTelefono = "a@b.cl",
        clienteId = "cli-tmp",
        deviceId = "dev-1",
        fechaLocal = "2026-09-18",
        createdAtDevice = fijo,
        ciclo = Ciclo.ACTIVO,
        subida = EstadoSubida.PENDIENTE,
        completo = false,
        mediosEsperados = 1,
        mediosSubidos = 0,
        resumenMedios = ResumenMedios(),
    )
}