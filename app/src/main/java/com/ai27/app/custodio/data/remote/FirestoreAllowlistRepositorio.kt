package com.ai27.app.custodio.data.remote

import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.repository.AllowlistRepositorio
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Allowlist de teléfonos autorizados: un **documento por teléfono** en
 * `telefonos_autorizados/{e164}` (el id del documento ES el número en E.164), poblada
 * con el Admin SDK desde `pf-infra-firebase/provision/provisionar.js`.
 *
 * Sustituye al documento único `config/telefonos` con un arreglo enumerable, que dejaba
 * que cualquier sesión autenticada leyera los teléfonos de todos los custodios. La regla
 * vigente solo permite leer el documento del propio número
 * (`request.auth.token.phone_number == e164`), así que comprobar la pertenencia no
 * filtra PII de terceros: por eso la consulta se hace DESPUÉS del OTP, cuando el token ya
 * trae el `phone_number`.
 *
 * Solo se necesita la existencia del documento, no sus campos. Cierre seguro por omisión:
 * si el documento no existe, nadie está autorizado.
 */
class FirestoreAllowlistRepositorio(
    private val firestore: FirebaseFirestore,
) : AllowlistRepositorio {

    override suspend fun contiene(telefono: String): Result<Boolean> = ejecutarRemoto {
        firestore.collection(TELEFONOS_AUTORIZADOS).document(telefono).get().await().exists()
    }

    private companion object {
        const val TELEFONOS_AUTORIZADOS = "telefonos_autorizados"
    }
}