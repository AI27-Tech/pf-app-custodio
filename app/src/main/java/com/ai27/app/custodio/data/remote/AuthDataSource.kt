package com.ai27.app.custodio.data.remote

import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
import com.ai27.app.custodio.domain.model.Custodio
import com.ai27.app.custodio.domain.repository.SesionRepositorio
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

/**
 * Sesión por correo + contraseña: sign in con `FirebaseAuth.signInWithEmailAndPassword`.
 *
 * Es de una sola fase y no necesita `Activity`: el reCAPTCHA y la auto-lectura del SMS
 * eran exclusivos de Phone Auth, así que no queda ni el watchdog del broker de GMS ni el
 * `ForceResendingToken` de reenvío.
 */
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

    override suspend fun iniciarSesion(correo: String, contrasena: String): Result<Custodio> =
        try {
            val usuario = auth.signInWithEmailAndPassword(correo, contrasena).await().user
                ?: return Result.Failure(
                    DominioError.Inesperado(IllegalStateException("Auth sin usuario")),
                )
            Result.Success(Custodio(uid = usuario.uid, correo = usuario.email.orEmpty()))
        } catch (e: Exception) {
            Result.Failure(excepcionSesionAError(e))
        }

    override suspend fun cerrarSesion(): Result<Unit> {
        auth.signOut()
        return Result.Success(Unit)
    }
}

/**
 * Traduce los errores de Firebase Auth a un [DominioError] puro.
 *
 * `signInWithEmailAndPassword` falla por dos vías que aquí se combinan: la **clase** de la
 * excepción (red / límite de intentos) y el `errorCode` (formato `ERROR_*`) que entrega el
 * backend. La clasificación pura vive en [mapearErrorAutenticacion] para poder probarse en
 * JVM sin instanciar las clases de Firebase (el android.jar de pruebas no las soporta).
 */
internal fun excepcionSesionAError(error: Throwable): DominioError =
    mapearErrorAutenticacion(
        tipo = when {
            error is FirebaseNetworkException -> TipoErrorAuth.RED
            error is FirebaseTooManyRequestsException -> TipoErrorAuth.DEMASIADOS
            else -> TipoErrorAuth.AUTENTICACION
        },
        codigo = (error as? FirebaseAuthException)?.errorCode,
    )

internal enum class TipoErrorAuth {
    RED,
    DEMASIADOS,
    AUTENTICACION,
}

/// Función pura: sin dependencias de las clases de Firebase, probable en JVM de host.
internal fun mapearErrorAutenticacion(tipo: TipoErrorAuth, codigo: String?): DominioError =
    when (tipo) {
        TipoErrorAuth.RED -> DominioError.SinConexion
        TipoErrorAuth.DEMASIADOS -> DominioError.DemasiadosIntentos
        TipoErrorAuth.AUTENTICACION -> when (codigo) {
            // El backend no distingue "correo que no existe" de "contrasecha incorrecta"
            // (`ERROR_INVALID_CREDENTIAL` cubre ambos desde la versión de cuentas
            //Enumeration-protection), y el formato también se junta aquí: los tres son
            // "el par que tecleaste no sirve".
            ERROR_INVALID_EMAIL,
            ERROR_WRONG_PASSWORD,
            ERROR_INVALID_LOGIN_CREDENTIALS,
            ERROR_USER_NOT_FOUND,
            -> DominioError.CredencialesInvalidas
            ERROR_USER_DISABLED -> DominioError.CuentaDeshabilitada
            ERROR_TOO_MANY_REQUESTS -> DominioError.DemasiadosIntentos
            ERROR_NETWORK_REQUEST_FAILED -> DominioError.SinConexion
            else -> DominioError.Inesperado(
                IllegalStateException("Error de Auth no clasificado: $codigo"),
            )
        }
    }

private const val ERROR_INVALID_EMAIL = "ERROR_INVALID_EMAIL"
private const val ERROR_WRONG_PASSWORD = "ERROR_WRONG_PASSWORD"
private const val ERROR_INVALID_LOGIN_CREDENTIALS = "ERROR_INVALID_LOGIN_CREDENTIALS"
private const val ERROR_USER_NOT_FOUND = "ERROR_USER_NOT_FOUND"
private const val ERROR_USER_DISABLED = "ERROR_USER_DISABLED"
private const val ERROR_TOO_MANY_REQUESTS = "ERROR_TOO_MANY_REQUESTS"
private const val ERROR_NETWORK_REQUEST_FAILED = "ERROR_NETWORK_REQUEST_FAILED"
