package com.ai27.app.custodio.data.worker

import com.ai27.app.custodio.data.repository.CATALOGO_ETIQUETAS
import com.ai27.app.custodio.domain.model.Etiqueta
import com.ai27.app.custodio.domain.repository.EtiquetaRepository

/** Doble residente del source set de instrumentación (no ve el Fake de unit tests)
 *  que siempre devuelve el catálogo curado empaquetado. */
class EtiquetasFijas : EtiquetaRepository {
    override suspend fun obtener(): List<Etiqueta> = CATALOGO_ETIQUETAS
}