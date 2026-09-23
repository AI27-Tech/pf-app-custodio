package com.ai27.app.custodio.domain.model

import com.ai27.app.custodio.domain.error.DominioError

/**
 * Eventos del flujo de verificación por SMS (PHONE_AUTH_PLAN Task 3).
 *
 * `SesionRepositorio.enviarCodigo`/`reenviarCodigo` entregan EXACTAMENTE un evento y
 * cierran el flujo: el SMS salió ([CodigoEnviado]), el custodio ya quedó con sesión
 * porque el sistema operativo leyó el SMS automáticamente ([VerificacionAutomatica]),
 * o el envío falló ([Fallo]).
 */
sealed interface EventoVerificacion {

    /** El código viajó por SMS; ahora hay que teclearlo contra [verificationId]. */
    data class CodigoEnviado(
        val verificationId: String,
        /** Llave opaca para reenviar el SMS (id del [com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken]). */
        val resendToken: Long,
    ) : EventoVerificacion

    /** Auto-retrieval: sin teclear nada el custodio cerró el OTP y quedó con sesión. */
    data class VerificacionAutomatica(val custodio: Custodio) : EventoVerificacion

    data class Fallo(val error: DominioError) : EventoVerificacion
}