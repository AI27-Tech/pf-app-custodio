package com.ai27.app.custodio.data.diagnostico

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Implementación real de [RegistroDeEventos] sobre Firebase Analytics.
 *
 * El `method` se manda en el parámetro **reservado** que Google le da al método de
 * autenticación del evento `login`, y el desenlace va en un parámetro aparte
 * (`login_result`) precisamente para no vaciarle el sentido: si el desenlace se cargara
 * al `method`, la consola no podría desglosar la tasa de fallo por paso, que es lo único
 * que justifica emitir el evento.
 *
 * `FirebaseAnalytics.Param` no trae constante para el desenlace —no es un parámetro
 * reservado de `login`—, así que la cadena es nuestra y vive en un solo sitio, acá.
 *
 * Recibe el `FirebaseAnalytics` ya inyectado (y no llama `getInstance()` adentro) para
 * que el módulo de Hilt sea el único que habla con el singleton, igual que
 * [RegistroDeFallosCrashlytics] recibe su `FirebaseCrashlytics`.
 *
 * La interfaz **no** atrapa excepciones: `logEvent` no lanza y no bloquea, pero si el SDK
 * fallara el fallo se vería en el login. Se acepta a propósito — un `try/catch` vacío
 * alrededor de la instrumentación es peor, porque escondería justo el fallo que se está
 * intentando medir. Si algún día se decide lo contrario, ese fallo se reporta con
 * [RegistroDeFallos], no con un `catch` mudo.
 */
class RegistroDeEventosAnalytics(
    private val analytics: FirebaseAnalytics,
) : RegistroDeEventos {

    override fun registroLogin(metodo: MetodoAuth, resultado: ResultadoLogin) {
        val bundle = Bundle()
        bundle.putString(FirebaseAnalytics.Param.METHOD, metodo.valor)
        bundle.putString(CLAVE_RESULTADO, resultado.valor)
        analytics.logEvent(FirebaseAnalytics.Event.LOGIN, bundle)
    }

    private companion object {
        const val CLAVE_RESULTADO = "login_result"
    }
}
