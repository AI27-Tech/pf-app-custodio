package com.ai27.app.custodio.data.remote

import com.ai27.app.custodio.domain.model.CategoriaEtiqueta
import com.ai27.app.custodio.domain.model.Etiqueta

/**
 * Parsea `catalogos/etiquetas.valores` (PLAN §5.1 / A4): arreglo de
 * `{ id, nombre, categoria, orden, activo }`.
 *
 * Devuelve `null` si la forma no es la esperada (campo que falta, tipo incorrecto,
 * categoría desconocida o lista vacía): la app cae entonces al catálogo empaquetado
 * (`data/repository/CatalogoEtiquetasRepository.CATALOGO_ETIQUETAS`). Un catálogo vacío
 * es un estado de fallo, no un valor válido:
 * sin chips la hoja no se puede cerrar (trampa sin salida del AUDIT §11).
 *
 * El `orden` puede llegar como `Int` o `Long` (Firestore Android suele devolver enteros
 * como `Long`), por eso se admiten ambos.
 */
fun valoresDelCatalogo(valores: Any?): List<Etiqueta>? {
    val lista = valores as? List<*> ?: return null
    val etiquetas = mutableListOf<Etiqueta>()
    for (valor in lista) {
        val mapa = valor as? Map<*, *> ?: return null
        val id = mapa["id"] as? String ?: return null
        val nombre = mapa["nombre"] as? String ?: return null
        val categoria = (mapa["categoria"] as? String)
            ?.let { texto -> runCatching { CategoriaEtiqueta.valueOf(texto) }.getOrNull() }
            ?: return null
        val orden = when (val o = mapa["orden"]) {
            is Int -> o
            is Long -> o.toInt()
            else -> return null
        }
        val activo = mapa["activo"] as? Boolean ?: return null
        etiquetas += Etiqueta(id, nombre, categoria, orden, activo)
    }
    return etiquetas.takeIf { it.isNotEmpty() }
}