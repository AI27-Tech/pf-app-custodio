package com.ai27.app.custodio.data.remote

import com.ai27.app.custodio.data.repository.CATALOGO_ETIQUETAS
import com.ai27.app.custodio.domain.model.Etiqueta
import com.ai27.app.custodio.domain.repository.EtiquetaRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Gancho de catálogo remoto de PLAN §3.1 / A4: el catálogo de etiquetas es **dato, no
 * código**, vive en `catalogos/etiquetas` (campo `valores`) y se cachea en el dispositivo
 * por la persistencia de Firestore. Cambiar los valores reales no requiere recompilar.
 *
 * Reglas de comportamiento:
 * - Si el documento no existe, falla la lectura (sin red, primer arranque) o está
 *   malformado → [respaldo]: el set curado empaquetado, que es el mismo contenido que
 *   provisionó el catálogo remoto. Etiquetar nunca se rompe por estar offline.
 * - La caché de Firestore hace que la primera lectura (online) deje el catálogo
 *   disponible para el resto del viaje, incluso en campo sin señal.
 */
class FirestoreCatalogoEtiquetasRepositorio(
    private val firestore: FirebaseFirestore,
    private val respaldo: List<Etiqueta> = CATALOGO_ETIQUETAS,
) : EtiquetaRepository {

    override suspend fun obtener(): List<Etiqueta> {
        val documento = try {
            firestore.collection(COLECCION).document(DOCUMENTO).get().await()
        } catch (_: Exception) {
            return respaldo
        }
        if (!documento.exists()) return respaldo
        return valoresDelCatalogo(documento[VALORES]) ?: respaldo
    }

    private companion object {
        const val COLECCION = "catalogos"
        const val DOCUMENTO = "etiquetas"
        const val VALORES = "valores"
    }
}