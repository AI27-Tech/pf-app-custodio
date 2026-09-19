package com.ai27.app.custodio

import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.OrigenMedio
import com.ai27.app.custodio.domain.model.TipoMedio
import com.ai27.app.custodio.domain.usecase.EtiquetarMedio
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EtiquetarMedioTest {

    @Test
    fun `asigna etiqueta valida a un medio pendiente`() = runBlocking {
        val repoMedios = FakeMedioRepository()
        repoMedios.guardar(
            com.ai27.app.custodio.domain.model.Medio(
                id = "m-1",
                reporteId = "r-1",
                tipo = TipoMedio.FOTO,
                origen = OrigenMedio.CAMARA,
                createdAtDevice = java.time.Instant.parse("2026-09-18T15:00:00Z"),
                contentType = "image/jpeg",
                estadoSubida = EstadoSubida.PENDIENTE,
            ),
            ByteArray(8),
        )

        val resultado = EtiquetarMedio(repoMedios, FakeEtiquetaRepository())("m-1", "dano")

        assertTrue(resultado is Result.Success)
        assertEquals("dano", (resultado as Result.Success).value.etiqueta)
        assertEquals("dano", repoMedios.medios.first().etiqueta)
    }

    @Test
    fun `rechaza etiqueta inexistente`() = runBlocking {
        val repoMedios = FakeMedioRepository()
        repoMedios.guardar(
            com.ai27.app.custodio.domain.model.Medio(
                id = "m-1",
                reporteId = "r-1",
                tipo = TipoMedio.FOTO,
                origen = OrigenMedio.CAMARA,
                createdAtDevice = java.time.Instant.parse("2026-09-18T15:00:00Z"),
                contentType = "image/jpeg",
                estadoSubida = EstadoSubida.PENDIENTE,
            ),
            ByteArray(8),
        )

        val resultado = EtiquetarMedio(repoMedios, FakeEtiquetaRepository())("m-1", "no-existe")

        assertTrue(resultado is Result.Failure)
        assertEquals(DominioError.EtiquetaInvalida("no-existe"), (resultado as Result.Failure).error)
        assertEquals(null, repoMedios.medios.first().etiqueta)
    }

    @Test
    fun `rechaza etiquetar medio inexistente`() = runBlocking {
        val resultado = EtiquetarMedio(FakeMedioRepository(), FakeEtiquetaRepository())("m-x", "dano")

        assertTrue(resultado is Result.Failure)
        assertEquals(DominioError.MedioNoEncontrado, (resultado as Result.Failure).error)
    }
}