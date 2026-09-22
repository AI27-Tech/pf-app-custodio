package com.ai27.app.custodio.domain.model

/**
 * Ejes de clasificación de la evidencia (PLAN §14).
 *
 * Un archivo describe **qué está pasando ahora** (Situación) y/o **qué muestra la
 * evidencia** (Evidencia). El modelo las separa porque no son lo mismo: "sin novedad"
 * no es "estado del transporte".
 */
enum class CategoriaEtiqueta { SITUACION, EVIDENCIA }

data class Etiqueta(
    val id: String,
    val nombre: String,
    val categoria: CategoriaEtiqueta,
    val orden: Int,
    val activo: Boolean,
)