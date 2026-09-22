package com.ai27.app.custodio.data.diagnostico

import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Implementación real de [RegistroDeFallos] sobre Crashlytics, como no fatal.
 *
 * El `contexto` va como clave personalizada y no como parte del mensaje, para que la
 * consola agrupe por tipo de excepción y siga siendo posible filtrar por dónde ocurrió.
 * No se añade nada del custodio ni del reporte: la política de PII de S13 sigue vigente.
 */
class RegistroDeFallosCrashlytics(
    private val crashlytics: FirebaseCrashlytics,
) : RegistroDeFallos {

    override fun registrar(contexto: String, causa: Throwable) {
        crashlytics.setCustomKey(CLAVE_CONTEXTO, contexto)
        crashlytics.recordException(causa)
    }

    private companion object {
        const val CLAVE_CONTEXTO = "contexto"
    }
}
