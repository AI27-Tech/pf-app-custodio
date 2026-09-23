package com.ai27.app.custodio.data.remote

import android.app.Activity
import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
import com.ai27.app.custodio.domain.model.Custodio
import com.ai27.app.custodio.domain.model.EventoVerificacion
import com.ai27.app.custodio.domain.repository.SesionRepositorio
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthMissingActivityForRecaptchaException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Sesión por SMS (PHONE_AUTH_PLAN Task 3): sign in con Firebase Phone Auth.
 *
 * - `enviarCodigo`/`reenviarCodigo` usan [PhoneAuthProvider.verifyPhoneNumber] vía
 *   [PhoneAuthOptions]. El Activity agendado se usa para la auto-lectura del SMS y el
 *   fallback reCAPTCHA; el callback `onVerificationCompleted` firma en nombre del usuario
 *   ([EventoVerificacion.VerificacionAutomatica]).
 * - El `ForceResendingToken` de Firebase no sale del repo: se guarda en un registro local
 *   y el dominio recibe una llave numérica opaca en [EventoVerificacion.CodigoEnviado.resendToken].
 */
class FirebaseAuthSesionRepositorio(
    private val auth: FirebaseAuth,
) : SesionRepositorio {

    override fun observarCustodio(): Flow<Custodio?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(
                firebaseAuth.currentUser?.let { usuario ->
                    Custodio(uid = usuario.uid, telefono = usuario.phoneNumber.orEmpty())
                },
            )
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override fun enviarCodigo(activity: Activity?, telefono: String): Flow<EventoVerificacion> {
        ultimoTelefono = telefono
        return flujoVerificacion(activity) { opciones ->
            opciones.setPhoneNumber(telefono)
        }
    }

    override fun reenviarCodigo(
        activity: Activity?,
        verificationId: String,
        resendToken: Long,
    ): Flow<EventoVerificacion> {
        val telefono = ultimoTelefono
        return if (telefono == null) {
            flowOf(
                EventoVerificacion.Fallo(
                    DominioError.Inesperado(IllegalStateException("No hay teléfono pendiente para reenviar")),
                ),
            )
        } else {
            flujoVerificacion(activity) { opciones ->
                // El builder de PhoneAuthOptions EXIGE `setPhoneNumber` aunque el reenvío
                // solo aporte el ForceResendingToken; sin él, `build()` lanza
                // IllegalArgumentException en el hilo principal (crash real en campo).
                val conTelefono = opciones.setPhoneNumber(telefono)
                tokensDeReenvio[resendToken]?.let(conTelefono::setForceResendingToken) ?: conTelefono
            }
        }
    }

    private fun flujoVerificacion(
        activity: Activity?,
        configurar: (PhoneAuthOptions.Builder) -> PhoneAuthOptions.Builder,
    ): Flow<EventoVerificacion> = callbackFlow {
        if (activity == null) {
            trySend(
                EventoVerificacion.Fallo(
                    DominioError.Inesperado(IllegalStateException("Phone Auth sin Activity")),
                ),
            )
            close()
            return@callbackFlow
        }
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            override fun onVerificationCompleted(credencial: PhoneAuthCredential) {
                launch {
                    trySend(firmarConCredencial(credencial))
                    close()
                }
            }

            override fun onVerificationFailed(error: FirebaseException) {
                trySend(EventoVerificacion.Fallo(excepcionSesionAError(error)))
                close()
            }

            override fun onCodeSent(verificationId: String, token: ForceResendingToken) {
                trySend(EventoVerificacion.CodigoEnviado(verificationId, registrarToken(token)))
                close()
            }
        }

        val opciones: PhoneAuthOptions = try {
            configurar(
                PhoneAuthOptions.newBuilder()
                    .setActivity(activity)
                    .setTimeout(TIEMPO_ESPERA_SMS_SEGUNDOS, TimeUnit.SECONDS)
                    .setCallbacks(callbacks),
            ).build()
        } catch (e: IllegalArgumentException) {
            // P. ej. teléfono vacío en el builder: se reporta como Fallo en vez de
            // reventar el hilo principal (el flujo corre en un build de interface).
            trySend(
                EventoVerificacion.Fallo(
                    DominioError.Inesperado(IllegalStateException("Opciones de Phone Auth inválidas", e)),
                ),
            )
            close()
            return@callbackFlow
        }

        // Guardia contra el broker de Phone Auth que se queda mudo (p. ej.
        // "Failed to get service from broker"): ni onCodeSent ni onVerificationFailed
        // llegan y el flujo colgaría para siempre. Un tope de tiempo lo convierte en
        // error visible para que el usuario pueda reintentar.
        launch {
            delay(TIEMPO_MAXIMO_ESPERA_BROKER_MS)
            trySend(EventoVerificacion.Fallo(DominioError.SinRespuestaSms))
            close()
        }

        PhoneAuthProvider.verifyPhoneNumber(opciones)
        awaitClose { }
    }

    override suspend fun verificarCodigo(verificationId: String, codigo: String): Result<Custodio> =
        try {
            val credencial = PhoneAuthProvider.getCredential(verificationId, codigo)
            val usuario = auth.signInWithCredential(credencial).await().user
                ?: return Result.Failure(
                    DominioError.Inesperado(IllegalStateException("Auth sin usuario")),
                )
            Result.Success(Custodio(uid = usuario.uid, telefono = usuario.phoneNumber.orEmpty()))
        } catch (e: Exception) {
            Result.Failure(excepcionSesionAError(e))
        }

    override suspend fun cerrarSesion(): Result<Unit> {
        auth.signOut()
        return Result.Success(Unit)
    }

    private suspend fun firmarConCredencial(credencial: PhoneAuthCredential): EventoVerificacion =
        try {
            val usuario = auth.signInWithCredential(credencial).await().user
                ?: return EventoVerificacion.Fallo(
                    DominioError.Inesperado(IllegalStateException("Auth sin usuario")),
                )
            EventoVerificacion.VerificacionAutomatica(
                Custodio(uid = usuario.uid, telefono = usuario.phoneNumber.orEmpty()),
            )
        } catch (e: Exception) {
            EventoVerificacion.Fallo(excepcionSesionAError(e))
        }

    private val tokensDeReenvio = ConcurrentHashMap<Long, ForceResendingToken>()
    private val proximoIdToken = AtomicLong(0L)

    // El reenvío necesita el número en el builder (obligatorio); se recuerda del último
    // `enviarCodigo` para no obligar al ViewModel a guardarlo dos veces.
    private var ultimoTelefono: String? = null

    private fun registrarToken(token: ForceResendingToken): Long {
        val id = proximoIdToken.getAndIncrement()
        tokensDeReenvio[id] = token
        return id
    }

    private companion object {
        const val TIEMPO_ESPERA_SMS_SEGUNDOS = 60L
        const val TIEMPO_MAXIMO_ESPERA_BROKER_MS = 30_000L
    }
}

/**
 * Traduce los errores de Firebase Phone Auth a un [DominioError] puro.
 *
 * Phone Auth falla por dos vías: callbacks de [PhoneAuthProvider] y `signInWithCredential`.
 * Se combina la **clase** de la excepción (red / límite de intentos) con el `errorCode`
 * (formato `ERROR_*`) que entrega el backend. La clasificación pura vive en
 * [mapearErrorAutenticacion] para poder probarse en JVM sin instanciar las clases de
 * Firebase (el android.jar de pruebas no las soporta).
 */
internal fun excepcionSesionAError(error: Throwable): DominioError {
    if (error is FirebaseAuthMissingActivityForRecaptchaException) {
        return DominioError.Inesperado(
            IllegalStateException("Verificación por reCAPTCHA sin Activity", error),
        )
    }
    return mapearErrorAutenticacion(
        tipo = when {
            error is FirebaseNetworkException -> TipoErrorAuth.RED
            error is FirebaseTooManyRequestsException -> TipoErrorAuth.DEMASIADOS
            else -> TipoErrorAuth.AUTENTICACION
        },
        codigo = (error as? FirebaseAuthException)?.errorCode,
    )
}

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
            ERROR_INVALID_PHONE_NUMBER -> DominioError.NumeroInvalido
            ERROR_INVALID_VERIFICATION_CODE -> DominioError.CodigoInvalido
            ERROR_SESSION_EXPIRED -> DominioError.CodigoExpirado
            ERROR_TOO_MANY_REQUESTS -> DominioError.DemasiadosIntentos
            ERROR_NETWORK_REQUEST_FAILED -> DominioError.SinConexion
            else -> DominioError.Inesperado(
                IllegalStateException("Error de Auth no clasificado: $codigo"),
            )
        }
    }

private const val ERROR_INVALID_PHONE_NUMBER = "ERROR_INVALID_PHONE_NUMBER"
private const val ERROR_INVALID_VERIFICATION_CODE = "ERROR_INVALID_VERIFICATION_CODE"
private const val ERROR_SESSION_EXPIRED = "ERROR_SESSION_EXPIRED"
private const val ERROR_TOO_MANY_REQUESTS = "ERROR_TOO_MANY_REQUESTS"
private const val ERROR_NETWORK_REQUEST_FAILED = "ERROR_NETWORK_REQUEST_FAILED"