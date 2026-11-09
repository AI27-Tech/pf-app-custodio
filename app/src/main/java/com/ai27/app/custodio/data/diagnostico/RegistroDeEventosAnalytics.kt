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

    // GA4 no tiene parámetros booleanos: las banderas van como "true"/"false", que sí sirven
    // de dimensión para filtrar. ms_disparo se omite (no se manda 0) cuando no hubo obturador,
    // para no bajar la mediana con un tiempo que no existió.
    override fun fotoRevelada(evento: EventoFotoRevelada) {
        val bundle = Bundle()
        bundle.putString(CLAVE_ORIGEN, evento.origen.valor)
        bundle.putString(CLAVE_RESULTADO_FOTO, evento.resultado.valor)
        evento.msDisparo?.let { bundle.putLong(CLAVE_MS_DISPARO, it) }
        bundle.putLong(CLAVE_MS_REVELADO, evento.msRevelado)
        bundle.putString(CLAVE_CON_GPS, evento.conGps.toString())
        bundle.putString(CLAVE_CON_MAPA, evento.conMapa.toString())
        bundle.putLong(CLAVE_KB_FINAL, evento.kbFinal.toLong())
        analytics.logEvent(EVENTO_FOTO_REVELADA, bundle)
    }

    private companion object {
        const val CLAVE_RESULTADO = "login_result"

        const val EVENTO_FOTO_REVELADA = "foto_revelada"
        const val CLAVE_ORIGEN = "origen"
        const val CLAVE_RESULTADO_FOTO = "resultado"
        const val CLAVE_MS_DISPARO = "ms_disparo"
        const val CLAVE_MS_REVELADO = "ms_revelado"
        const val CLAVE_CON_GPS = "con_gps"
        const val CLAVE_CON_MAPA = "con_mapa"
        const val CLAVE_KB_FINAL = "kb_final"
    }
}
