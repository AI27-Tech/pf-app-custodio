package com.ai27.app.custodio

import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.OrigenMedio
import com.ai27.app.custodio.domain.model.TipoMedio
import com.ai27.app.custodio.domain.usecase.ReintentarSubida
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReintentarSubidaTest {

    private val fijo = Instant.parse("2026-09-18T15:00:00Z")
    private lateinit var repoMedios: FakeMedioRepository
    private lateinit var medioId: String

    @Before
    fun setUp() = runBlocking {
        repoMedios = FakeMedioRepository()
        val medio = (repoMedios.guardar(
            com.ai27.app.custodio.domain.model.Medio(
                id = "m-1",
                reporteId = "r-1",
                tipo = TipoMedio.FOTO,
                origen = OrigenMedio.CAMARA,
                createdAtDevice = fijo,
                contentType = "image/jpeg",
                estadoSubida = EstadoSubida.FALLIDA,
            ),
            ByteArray(8),
        ) as Result.Success).value
        medioId = medio.id
    }

    @Test
    fun `reprograma una subida fallida a pendiente`() = runBlocking {
        val resultado = ReintentarSubida(repoMedios)(medioId)

        assertTrue(resultado is Result.Success)
        assertEquals(EstadoSubida.PENDIENTE, (resultado as Result.Success).value.estadoSubida)
    }

    @Test
    fun `no reintenta un medio en progreso`() = runBlocking {
        repoMedios.actualizarEstadoSubida(medioId, EstadoSubida.EN_PROGRESO)

        val resultado = ReintentarSubida(repoMedios)(medioId)

        assertTrue(resultado is Result.Failure)
        assertEquals(DominioError.MedioEnSubida, (resultado as Result.Failure).error)
    }

    @Test
    fun `no reintenta un medio ya subido`() = runBlocking {
        repoMedios.actualizarEstadoSubida(medioId, EstadoSubida.COMPLETA)

        val resultado = ReintentarSubida(repoMedios)(medioId)

        assertTrue(resultado is Result.Failure)
        assertEquals(DominioError.MedioYaSubido, (resultado as Result.Failure).error)
    }

    @Test
    fun `error si el medio no existe`() = runBlocking {
        val resultado = ReintentarSubida(repoMedios)("no-existe")

        assertTrue(resultado is Result.Failure)
        assertEquals(DominioError.MedioNoEncontrado, (resultado as Result.Failure).error)
    }
}