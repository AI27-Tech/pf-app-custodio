package com.ai27.app.custodio.data.repository

import com.ai27.app.custodio.domain.model.Etiqueta
import com.ai27.app.custodio.domain.repository.EtiquetaRepository

class CatalogoEtiquetasRepository : EtiquetaRepository {

    // Catálogo provisional mientras no exista la carga desde Firebase (B5).
    private val catalogo = listOf(
        Etiqueta(id = "dano", nombre = "Daño", orden = 0, activo = true),
        Etiqueta(id = "devolucion", nombre = "Devolución", orden = 1, activo = true),
        Etiqueta(id = "novedad", nombre = "Novedad", orden = 2, activo = true),
        Etiqueta(id = "contraentrega", nombre = "Contraentrega", orden = 3, activo = true),
        Etiqueta(id = "otro", nombre = "Otro", orden = 4, activo = true),
    )

    override suspend fun obtener(): List<Etiqueta> = catalogo
}