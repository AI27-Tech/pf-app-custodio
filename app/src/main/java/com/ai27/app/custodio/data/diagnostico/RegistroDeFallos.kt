package com.ai27.app.custodio.data.diagnostico

/**
 * Registra fallos no fatales para poder diagnosticarlos desde el campo.
 *
 * Existe por el hallazgo **E2** de `docs/AUDIT.md`: los fallos de cámara y de procesamiento
 * de imagen se perdían en `catch` vacíos, así que un custodio podía tocar el disparador,
 * no obtener nada, y no quedaba ni un rastro para averiguar por qué.
 *
 * Es una interfaz y no una llamada directa a Crashlytics por dos razones: para que
 * `FirebaseCrashlytics.getInstance()` no aparezca dentro de un composable ni de un
 * ViewModel, y para poder falsearla en pruebas de JVM (decisión D1 de §13 del PLAN).
 */
interface RegistroDeFallos {
    /**
     * @param contexto etiqueta corta de dónde ocurrió, p. ej. `"captura_foto"`. Sirve para
     *   agrupar en la consola; **no** debe llevar datos del custodio ni del cliente.
     */
    fun registrar(contexto: String, causa: Throwable)
}
