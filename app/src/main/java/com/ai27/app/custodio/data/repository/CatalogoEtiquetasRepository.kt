package com.ai27.app.custodio.data.repository

import com.ai27.app.custodio.domain.model.CategoriaEtiqueta
import com.ai27.app.custodio.domain.model.Etiqueta
import com.ai27.app.custodio.domain.repository.EtiquetaRepository

// Catálogo fijo en la app (PLAN §14). Es el vocabulario curado a partir del codebook de
// radio (Media.jpeg); será configurable desde Firebase cuando llegue el gancho remoto
// (B5), y la forma del modelo ya no cambiará entonces (R2).
//
// El criterio de subida es "tiene al menos una etiqueta de Situación" (decisión A de
// §14.2): este set es la fuente de verdad de qué ids cuentan, y también lo usan la
// migración de Room (D2) y los mappers que recalculan la columna denormalizada.
val CATALOGO_ETIQUETAS: List<Etiqueta> = listOf(
    Etiqueta(id = "sin-novedad", nombre = "Sin novedad", categoria = CategoriaEtiqueta.SITUACION, orden = 0, activo = true),
    Etiqueta(id = "en-recorrido", nombre = "En recorrido", categoria = CategoriaEtiqueta.SITUACION, orden = 1, activo = true),
    Etiqueta(id = "detenido", nombre = "Detenido", categoria = CategoriaEtiqueta.SITUACION, orden = 2, activo = true),
    Etiqueta(id = "parada-bano", nombre = "Parada por baño", categoria = CategoriaEtiqueta.SITUACION, orden = 3, activo = true),
    Etiqueta(id = "con-novedad", nombre = "Con novedad", categoria = CategoriaEtiqueta.SITUACION, orden = 4, activo = true),
    Etiqueta(id = "estado-transporte", nombre = "Estado del transporte", categoria = CategoriaEtiqueta.EVIDENCIA, orden = 0, activo = true),
    Etiqueta(id = "falla-mecanica", nombre = "Falla mecánica", categoria = CategoriaEtiqueta.EVIDENCIA, orden = 1, activo = true),
    Etiqueta(id = "accidente", nombre = "Accidente", categoria = CategoriaEtiqueta.EVIDENCIA, orden = 2, activo = true),
    Etiqueta(id = "robo-asalto", nombre = "Robo o asalto", categoria = CategoriaEtiqueta.EVIDENCIA, orden = 3, activo = true),
    Etiqueta(id = "desvio-ruta", nombre = "Desvío de ruta", categoria = CategoriaEtiqueta.EVIDENCIA, orden = 4, activo = true),
    Etiqueta(id = "otro", nombre = "Otro", categoria = CategoriaEtiqueta.EVIDENCIA, orden = 5, activo = true),
)

/** Ids que cuentan como Situación para la cola de subida (PLAN §14 D1). */
val ETIQUETAS_SITUACION_IDS: Set<String> = CATALOGO_ETIQUETAS
    .filter { it.categoria == CategoriaEtiqueta.SITUACION }
    .map { it.id }
    .toSet()

class CatalogoEtiquetasRepository : EtiquetaRepository {
    override suspend fun obtener(): List<Etiqueta> = CATALOGO_ETIQUETAS
}