package com.ai27.app.custodio.data.local

import android.content.SharedPreferences
import com.ai27.app.custodio.domain.model.Etiqueta

/** Almacenamiento de la copia local del catálogo de etiquetas (A4). */
interface CatalogoEtiquetasAlmacen {
    /** `null` si todavía no hay catálogo guardado o el guardado está corrupto. */
    fun leer(): List<Etiqueta>?

    fun guardar(etiquetas: List<Etiqueta>)
}

/**
 * Copia persistente del catálogo en SharedPreferences. La caché de Firestore solo sirve
 * cuando se cae la red; esta copia evita el round-trip de red de cada `get()` (que con red
 * espera al servidor) en el arranque siguiente, así la `HojaEtiqueta` abre con chips
 * presentes sin esperar.
 */
class CatalogoEtiquetasOffline(
    private val preferencias: SharedPreferences,
) : CatalogoEtiquetasAlmacen {

    override fun leer(): List<Etiqueta>? {
        val texto = preferencias.getString(CLAVE, null) ?: return null
        val catalogo = CatalogoEtiquetasCodificador.descodificar(texto)
        // Un catálogo guardado que ya no se descifra vale menos que nada: se limpia para
        // que el siguiente leer vuelva a caer en la red (o el respaldo empaquetado).
        if (catalogo == null) preferencias.edit().remove(CLAVE).apply()
        return catalogo
    }

    override fun guardar(etiquetas: List<Etiqueta>) {
        preferencias.edit()
            .putString(CLAVE, CatalogoEtiquetasCodificador.codificar(etiquetas))
            .apply()
    }

    private companion object {
        const val CLAVE = "etiquetas"
    }
}