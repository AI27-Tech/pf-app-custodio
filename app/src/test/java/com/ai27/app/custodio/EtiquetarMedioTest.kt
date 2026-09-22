package com.ai27.app.custodio

import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.OrigenMedio
import com.ai27.app.custodio.domain.model.TipoMedio
import com.ai27.app.custodio.domain.usecase.EtiquetarMedio
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EtiquetarMedioTest {

    private suspend fun medioGuardado(repoMedios: FakeMedioRepository, id: String = "m-1") =
        repoMedios.guardar(
            com.ai27.app.custodio.domain.model.Medio(
                id = id,
                reporteId = "r-1",
                tipo = TipoMedio.FOTO,
                origen = OrigenMedio.CAMARA,
                createdAtDevice = Instant.parse("2026-09-18T15:00:00Z"),
                contentType = "image/jpeg",
                estadoSubida = EstadoSubida.PENDIENTE,
            ),
            ByteArray(8),
        )

    @Test
    fun `asigna situacion y evidencia a un medio pendiente`() = runBlocking {
        val repoMedios = FakeMedioRepository()
        medioGuardado(repoMedios)

        val resultado = EtiquetarMedio(repoMedios, FakeEtiquetaRepository())(
            "m-1",
            listOf("sin-novedad", "accidente"),
        )

        assertTrue(resultado is Result.Success)
        assertEquals(
            listOf("sin-novedad", "accidente"),
            (resultado as Result.Success).value.etiquetas,
        )
        assertEquals(
            listOf("sin-novedad", "accidente"),
            repoMedios.medios.first().etiquetas,
        )
    }

    @Test
    fun `rechaza etiqueta inexistente`() = runBlocking {
        val repoMedios = FakeMedioRepository()
        medioGuardado(repoMedios)

        val resultado = EtiquetarMedio(repoMedios, FakeEtiquetaRepository())(
            "m-1",
            listOf("sin-novedad", "no-existe"),
        )

        assertTrue(resultado is Result.Failure)
        assertEquals(DominioError.EtiquetaInvalida("no-existe"), (resultado as Result.Failure).error)
        assertNull(repoMedios.medios.first().etiquetas.firstOrNull())
    }

    @Test
    fun `rechaza la lista si no hay ninguna situacion`() = runBlocking {
        val repoMedios = FakeMedioRepository()
        medioGuardado(repoMedios)

        val resultado = EtiquetarMedio(repoMedios, FakeEtiquetaRepository())(
            "m-1",
            listOf("accidente", "otro"),
        )

        assertTrue(resultado is Result.Failure)
        assertEquals(DominioError.FaltaSituacion, (resultado as Result.Failure).error)
        assertNull(repoMedios.medios.first().etiquetas.firstOrNull())
    }

    @Test
    fun `rechaza la lista vacia`() = runBlocking {
        val repoMedios = FakeMedioRepository()
        medioGuardado(repoMedios)

        val resultado = EtiquetarMedio(repoMedios, FakeEtiquetaRepository())(
            "m-1",
            emptyList(),
        )

        assertTrue(resultado is Result.Failure)
        assertEquals(DominioError.FaltaSituacion, (resultado as Result.Failure).error)
    }

    @Test
    fun `rechaza etiquetar medio inexistente`() = runBlocking {
        val resultado = EtiquetarMedio(FakeMedioRepository(), FakeEtiquetaRepository())(
            "m-x",
            listOf("sin-novedad"),
        )

        assertTrue(resultado is Result.Failure)
        assertEquals(DominioError.MedioNoEncontrado, (resultado as Result.Failure).error)
    }
}