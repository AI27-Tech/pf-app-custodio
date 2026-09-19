package com.ai27.app.custodio.data.remote

import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
import com.ai27.app.custodio.domain.model.Custodio
import com.ai27.app.custodio.domain.repository.SesionRepositorio
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.suspendCancellableCoroutine

class FirebaseAuthSesionRepositorio(
    private val auth: FirebaseAuth,
) : SesionRepositorio {

    override fun observarCustodio(): Flow<Custodio?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(
                firebaseAuth.currentUser?.let { usuario ->
                    Custodio(uid = usuario.uid, correo = usuario.email.orEmpty())
                },
            )
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override suspend fun iniciarSesion(correo: String, contraseña: String): Result<Custodio> =
        try {
            val credenciales = auth.signInWithEmailAndPassword(correo, contraseña).esperar()
            val usuario = credenciales.user
                ?: return Result.Failure(DominioError.Inesperado(IllegalStateException("Auth sin usuario")))
            Result.Success(Custodio(uid = usuario.uid, correo = usuario.email.orEmpty()))
        } catch (e: FirebaseAuthException) {
            Result.Failure(codigoSesionAError(e.errorCode))
        } catch (e: Exception) {
            Result.Failure(DominioError.Inesperado(e))
        }

    override suspend fun cerrarSesion(): Result<Unit> {
        auth.signOut()
        return Result.Success(Unit)
    }
}

private suspend fun <T> Task<T>.esperar(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener(com.google.android.gms.tasks.OnCompleteListener<T> { resultado ->
        val excepcion = resultado.exception
        when {
            resultado.isSuccessful -> if (cont.isActive) cont.resume(resultado.result!!)
            excepcion != null -> if (cont.isActive) cont.resumeWithException(excepcion)
            else -> if (cont.isActive) {
                cont.resumeWithException(IllegalStateException("Tarea Auth sin resultado ni error"))
            }
        }
    })
}

internal fun codigoSesionAError(codigo: String): DominioError = when (codigo) {
    "ERROR_WRONG_PASSWORD",
    "ERROR_INVALID_CREDENTIAL",
    "ERROR_USER_NOT_FOUND",
    "ERROR_USER_DISABLED",
    "ERROR_INVALID_EMAIL",
    "ERROR_SIGN_IN_FAILED",
    -> DominioError.CredencialesInvalidas

    "ERROR_NETWORK_REQUEST_FAILED" -> DominioError.SinConexion

    "ERROR_TOO_MANY_REQUESTS" -> DominioError.Inesperado(
        IllegalStateException("Demasiados intentos de inicio de sesión"),
    )

    else -> DominioError.Inesperado(IllegalStateException("Error de Auth no clasificado: $codigo"))
}