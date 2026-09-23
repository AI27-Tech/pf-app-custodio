package com.ai27.app.custodio.data.remote

import com.ai27.app.custodio.domain.model.CategoriaEtiqueta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contrato de `catalogos/etiquetas.valores` (A4): `{ id, nombre, categoria, orden, activo }`. */
class CatalogoEtiquetasDocumentoTest {

    @Test
    fun `parsea una lista valida`() {
        val valores: List<Any> = listOf(
            mapOf("id" to "sin-novedad", "nombre" to "Sin novedad", "categoria" to "SITUACION", "orden" to 0, "activo" to true),
            mapOf("id" to "accidente", "nombre" to "Accidente", "categoria" to "EVIDENCIA", "orden" to 1, "activo" to true),
        )

        val etiquetas = valoresDelCatalogo(valores)

        assertEquals(2, etiquetas!!.size)
        assertEquals("sin-novedad", etiquetas[0].id)
        assertEquals("Sin novedad", etiquetas[0].nombre)
        assertEquals(CategoriaEtiqueta.SITUACION, etiquetas[0].categoria)
        assertEquals(0, etiquetas[0].orden)
        assertTrue(etiquetas[0].activo)
    }

    @Test
    fun `el orden llega como Long desde Firestore y aun asi parsea`() {
        val valores: List<Any> = listOf(
            mapOf(
                "id" to "apagon",
                "nombre" to "Apagón",
                "categoria" to "SITUACION",
                "orden" to 42L,
                "activo" to true,
            ),
        )

        val etiquetas = valoresDelCatalogo(valores)

        assertEquals(42, etiquetas!!.single().orden)
    }

    @Test
    fun `una lista vacia no es un catalogo valido`() {
        assertNull(valoresDelCatalogo(emptyList<Any>()))
    }

    @Test
    fun `una entrada rota invalida todo el catalogo`() {
        val valores: List<Any> = listOf(
            mapOf("id" to "sin-novedad", "nombre" to "Sin novedad", "categoria" to "SITUACION", "orden" to 0, "activo" to true),
            mapOf("id" to "accidente", "nombre" to "Accidente", "categoria" to "SITUACION", "orden" to 1), // sin activo
        )

        assertNull(valoresDelCatalogo(valores))
    }

    @Test
    fun `una categoria desconocida invalida el catalogo`() {
        val valores: List<Any> = listOf(
            mapOf("id" to "extraño", "nombre" to "Extraño", "categoria" to "ALGO_MAS", "orden" to 0, "activo" to true),
        )

        assertNull(valoresDelCatalogo(valores))
    }

    @Test
    fun `un tipo de dato equivocado o un documento no lista invalida el catalogo`() {
        assertNull(valoresDelCatalogo(null))
        assertNull(valoresDelCatalogo("no soy una lista"))
        assertNull(valoresDelCatalogo(listOf("no soy un mapa")))
    }
}