package com.ai27.app.custodio.data.repository

import com.ai27.app.custodio.domain.model.CategoriaEtiqueta
import com.ai27.app.custodio.domain.model.Etiqueta
import com.ai27.app.custodio.domain.repository.EtiquetaRepository

// Catálogo embebido en la app (PLAN §14), el vocabulario curado a partir del codebook de
// radio (Media.jpeg). Desde A4 el catálogo vivo es `catalogos/etiquetas` en Firestore y
// este set es el **respaldo offline**: mismo contenido que se provisionó en el remoto, de
// forma que etiquetar nunca se rompe sin conexión y el primer arranque sin el doc remoto
// (o con él malformado) cae aquí. La forma del modelo coincide con la del remoto (R2).
//
// El criterio de subida es "tiene al menos una etiqueta de Situación" (decisión A de
// §14.2): qué ids cuentan lo define el catálogo activo (remoto con fallback) en
// RoomMedioRepository.etiquetar y en el guard de SubidaMedioProceso. ESTE set se queda
// para dos sitios históricos que no deben seguir el catálogo dinámico: la migración de
// Room (D2, recalcula la columna de filas viejas) y MedioMappers.aEntidad (escribe los
// mismos ids al guardar, cuando la etiquetación aún no ha ocurrido).
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