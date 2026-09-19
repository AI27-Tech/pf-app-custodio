package com.ai27.app.custodio

import com.ai27.app.custodio.domain.usecase.ObtenerEtiquetas
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class ObtenerEtiquetasTest {

    @Test
    fun `expone el catalogo de etiquetas activas`() = runBlocking {
        val etiquetas = ObtenerEtiquetas(FakeEtiquetaRepository())()

        assertTrue(etiquetas.isNotEmpty())
        assertTrue(etiquetas.all { it.activo })
        assertTrue(etiquetas.zipWithNext().all { (a, b) -> a.orden <= b.orden })
    }
}