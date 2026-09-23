package com.ai27.app.custodio.domain.repository

import android.app.Activity
import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.model.Custodio
import com.ai27.app.custodio.domain.model.EventoVerificacion
import kotlinx.coroutines.flow.Flow

/**
 * Sesión del custodio. Tras el PHONE_AUTH_PLAN la identidad es el número de teléfono
 * verificado por SMS (Firebase Phone Auth), no el correo del login previo por PIN.
 */
interface SesionRepositorio {

    fun observarCustodio(): Flow<Custodio?>

    /**
     * Dispara el SMS de verificación y entrega un único [EventoVerificacion].
     *
     * `activity` se usa SOLO para la auto-lectura del SMS (SMS Retriever) y para el
     * fallback de reCAPTCHA de Phone Auth; la verificación del código tecleado es
     * [verificarCodigo] y no toca el Activity. Es null en pruebas de JVM y en
     * composiciones sin host de actividad; la implementación real exige uno.
     */
    fun enviarCodigo(activity: Activity?, telefono: String): Flow<EventoVerificacion>

    /** Reenvía el SMS para el [com.ai27.app.custodio.domain.model.EventoVerificacion.CodigoEnviado.verificationId]. */
    fun reenviarCodigo(
        activity: Activity?,
        verificationId: String,
        resendToken: Long,
    ): Flow<EventoVerificacion>

    /** Verifica el OTP tecleado contra un [com.ai27.app.custodio.domain.model.EventoVerificacion.CodigoEnviado.verificationId]. */
    suspend fun verificarCodigo(verificationId: String, codigo: String): Result<Custodio>

    suspend fun cerrarSesion(): Result<Unit>
}