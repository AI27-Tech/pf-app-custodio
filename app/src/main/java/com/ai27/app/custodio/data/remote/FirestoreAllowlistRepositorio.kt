package com.ai27.app.custodio.data.remote

import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.repository.AllowlistRepositorio
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Lee la allowlist de teléfonos en `config/telefonos` (arreglo `telefonos`), administrada
 * con el Admin SDK / la consola (Task 8). Si el documento no existe, nadie está
 * autorizado: cierre seguro por omisión.
 */
class FirestoreAllowlistRepositorio(
    private val firestore: FirebaseFirestore,
) : AllowlistRepositorio {

    override suspend fun contiene(telefono: String): Result<Boolean> = ejecutarRemoto {
        val documento = firestore.collection(CONFIG).document(TELEFONOS).get().await()
        if (documento.exists()) {
            val telefonos = documento.get(TELEFONOS) as? List<*> ?: emptyList<Any?>()
            telefono in telefonos.filterIsInstance<String>()
        } else {
            false
        }
    }

    private companion object {
        const val CONFIG = "config"
        const val TELEFONOS = "telefonos"
    }
}