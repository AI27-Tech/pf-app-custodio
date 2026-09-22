package com.ai27.app.custodio

import com.ai27.app.custodio.domain.model.CategoriaEtiqueta
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
        assertTrue("El catálogo debe tener etiquetas de Situación", etiquetas.any { it.categoria == CategoriaEtiqueta.SITUACION })
        assertTrue("El catálogo debe tener etiquetas de Evidencia", etiquetas.any { it.categoria == CategoriaEtiqueta.EVIDENCIA })
        // El orden es por categoría: cada grupo se muestra en su sección de la hoja.
        CategoriaEtiqueta.entries.forEach { categoria ->
            val delGrupo = etiquetas.filter { it.categoria == categoria } //.sortedBy { it.orden }
            assertTrue(
                "El orden del grupo $categoria debe ser estable",
                delGrupo.zipWithNext().all { (a, b) -> a.orden <= b.orden },
            )
        }
    }
}