package com.ai27.app.custodio.data.local

import com.ai27.app.custodio.data.repository.CATALOGO_ETIQUETAS
import com.ai27.app.custodio.domain.model.CategoriaEtiqueta
import com.ai27.app.custodio.domain.model.Etiqueta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogoEtiquetasCodificadorTest {

    @Test
    fun `redondea el catalogo empaquetado sin perder campos`() {
        val texto = CatalogoEtiquetasCodificador.codificar(CATALOGO_ETIQUETAS)

        assertEquals(CATALOGO_ETIQUETAS, CatalogoEtiquetasCodificador.descodificar(texto))
    }

    @Test
    fun `escapa caracters que colmarian los separadores`() {
        val sucia = listOf(
            Etiqueta("id|raro", "Nombre con | tubería", CategoriaEtiqueta.SITUACION, 0, true),
            Etiqueta("id-n", "Salto de\nlínea y \\ barra", CategoriaEtiqueta.EVIDENCIA, 1, false),
        )

        val texto = CatalogoEtiquetasCodificador.codificar(sucia)

        assertEquals(sucia, CatalogoEtiquetasCodificador.descodificar(texto))
    }

    @Test
    fun `texto vacio se considera sin catalogo`() {
        assertNull(CatalogoEtiquetasCodificador.descodificar(""))
        assertNull(CatalogoEtiquetasCodificador.descodificar("   \n "))
    }

    @Test
    fun `formato desconocido devuelve null en lugar de reventar`() {
        assertNull(CatalogoEtiquetasCodificador.descodificar("no-es-un-catalogo"))
        assertNull(CatalogoEtiquetasCodificador.descodificar("un-solo|campo"))
        assertNull(CatalogoEtiquetasCodificador.descodificar("a|b|CATEGORIA_INE|1|true"))
        assertNull(CatalogoEtiquetasCodificador.descodificar("a|b|SITUACION|no-numero|true"))
    }
}