package com.ai27.app.custodio.data.remote

import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.repository.AllowlistRepositorio
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Allowlist de correos autorizados: un **documento por correo** en
 * `correos_autorizados/{correo}` (el id del documento ES el correo en minúsculas),
 * poblada con el Admin SDK desde `pf-infra-firebase/provision/provisionar.js`.
 *
 * Sustituye a `telefonos_autorizados/{e164}` (que queda de baja con el corte a correo).
 * La regla vigente solo permite leer el documento del propio correo
 * (`request.auth.token.email == correo`), así que comprobar la pertenencia no filtra PII de
 * terceros: por eso la consulta se hace DESPUÉS de autenticar, cuando el token ya trae el
 * `email`.
 *
 * Solo se necesita la existencia del documento, no sus campos. Cierre seguro por omisión:
 * si el documento no existe, nadie está autorizado.
 */
class FirestoreAllowlistRepositorio(
    private val firestore: FirebaseFirestore,
) : AllowlistRepositorio {

    override suspend fun contiene(correo: String): Result<Boolean> = ejecutarRemoto {
        // El id del documento es el correo en minúsculas (así lo siembra el aprovisionamiento),
        // así que la normalización tiene que ocurrir aquí del lado del cliente: con el correo
        // tal como lo tecleó el custodio el `get()` no encontraría nada y cerraría su propia sesión.
        firestore.collection(CORREOS_AUTORIZADOS).document(correo.lowercase()).get().await().exists()
    }

    private companion object {
        const val CORREOS_AUTORIZADOS = "correos_autorizados"
    }
}
