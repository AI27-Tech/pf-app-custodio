package com.ai27.app.custodio

import com.ai27.app.custodio.data.repository.CATALOGO_ETIQUETAS
import com.ai27.app.custodio.domain.model.Etiqueta
import com.ai27.app.custodio.domain.repository.EtiquetaRepository

/**
 * Doble de [EtiquetaRepository] con catálogo configurable (por defecto el set curado
 * empaquetado). Permite simular el catálogo remoto de A4: pasar un catálogo con un id
 * de Situación nuevo y verificar que etiquetar/guard aceptan ese id sin recompilar.
 */
class FakeEtiquetaRepository(
    private val catalogo: List<Etiqueta> = CATALOGO_ETIQUETAS,
) : EtiquetaRepository {
    override suspend fun obtener(): List<Etiqueta> = catalogo
}