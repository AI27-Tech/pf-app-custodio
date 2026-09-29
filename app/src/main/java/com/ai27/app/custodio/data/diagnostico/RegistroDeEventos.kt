package com.ai27.app.custodio.data.diagnostico

import com.ai27.app.custodio.domain.error.DominioError

/**
 * Registra eventos de producto para poder medir el embudo del login.
 *
 * El login por correo + contraseña tiene desenlaces distinguibles (credenciales
 * rechazadas, correo fuera de la allowlist, demasiados intentos, sin conexión) y cada uno
 * es una fuga distinta que se arregla distinto. Sin eventos, el paso en el que se pierde el
 * custodio es invisible (`docs/PLAN_ANALYTICS_LOGIN.md`).
 *
 * Es una interfaz y no una llamada directa a Analytics por las dos mismas razones que
 * en [RegistroDeFallos]: para que `FirebaseAnalytics.getInstance()` no aparezca dentro
 * de un ViewModel, y para poder falsearla en pruebas de JVM. La tercera razón es propia
 * de aquí: **el conjunto de desenlaces es cerrado**, y por eso son enums y no cadenas.
 * El valor ES la dimensión por la que se va a filtrar en la consola, así que un `String`
 * libre permitiría emitir `login_result="ok"` y `login_result="OK"` como dos dimensiones
 * distintas sin que nada avise.
 *
 * **Sin datos del custodio.** Ningún parámetro lleva el correo, el `uid` ni nada
 * identificable: el método es una constante y el desenlace es un valor de un conjunto
 * cerrado. Es la política de PII de S13 (la misma que gobierna [RegistroDeFallos]) y es
 * la razón por la que este evento es publicable tal cual — un método o un desenlace que
 * llevaran el correo lo arruinaría. La allowlist vive en `correos_autorizados/{email}`
 * y el correo es PII por definición.
 *
 * Todos los valores son ASCII y de menos de 100 caracteres, respetando el límite de
 * longitud de valor de parámetro de evento de GA4 (100 para el valor, 40 para el nombre
 * del parámetro y del evento; 25 parámetros por evento; aquí van dos).
 */
interface RegistroDeEventos {
    /**
     * Registra el desenlace de un intento de autenticación.
     *
     * @param metodo método de autenticación usado; hoy solo existe [MetodoAuth.EMAIL]
     *   porque el único método vigente es correo + contraseña, pero el enum existe para que
     *   agregar otro sea cambiar un `when` y no una firma de interfaz más cada punto de
     *   llamada.
     * @param resultado cómo terminó el intento; nunca nulo ni un valor desconocido.
     */
    fun registroLogin(metodo: MetodoAuth, resultado: ResultadoLogin)
}

/** Método de autenticación. Nace con un solo caso: hoy el login es solo por correo. */
enum class MetodoAuth(val valor: String) {
    EMAIL("EMAIL"),
}

/**
 * Cómo termina un intento de autenticación. Los [valor] son los que viajan al `Bundle`
 * y son exactamente los que se van a ver como dimensión en la consola.
 *
 * Son siete, no nueve: al retirar el OTP desaparecieron sus desenlaces (código inválido,
 * código expirado, número mal compuesto) y el broker de GMS mudo dejó de existir, porque
 * `signInWithEmailAndPassword` no pasa por el broker de SMS. **El conjunto se recorta, no
 * se renombra**: los valores viejos siguen vivos como dimensiones en la consola y volver a
 * emitirlos metería ruido en reportes que ya se vienen leyendo.
 */
enum class ResultadoLogin(val valor: String) {
    /** El correo autenticó y la allowlist autorizó al custodio. */
    OK("ok"),

    /** El correo no compone una dirección válida (falló la validación local de la pantalla). */
    CORREO_INVALIDO("correo_invalido"),

    /** Firebase Auth rechazó el par correo/contraseña. */
    CREDENCIALES_INVALIDAS("credenciales_invalidas"),

    /** El backend bloqueó el intento por reintentos. */
    DEMASIADOS_INTENTOS("demasiados_intentos"),

    /** El correo autenticó pero no tiene documento en `correos_autorizados`. */
    CORREO_NO_AUTORIZADO("correo_no_autorizado"),

    /** Sin red, o el servidor respondió temporalmente indisponible. */
    SIN_CONEXION("sin_conexion"),

    /** Cualquier otro desenlace, incluidos los errores que el login no puede producir. */
    INESPERADO("inesperado"),
}

/**
 * Traduce un error de dominio al desenlace que se registra, como la que ya existe hacia
 * strings en `ui/error/DominioErrorRecurso.kt`.
 *
 * Vive en `data/diagnostico/` (y no en `ui/`) para respetar las dos direcciones que el
 * proyecto ya tiene cubiertas: `data` → `domain` está permitida, y `ui` → `data.diagnostico`
 * es el precedente de `ui/captura/CapturaViewModel.kt` inyectando [RegistroDeFallos].
 * Ubicarlo en la capa de presentación obligaría a la UI a conocer el vocabulario de la
 * analítica.
 *
 * Se escribe **exhaustivo y sin `else`**, igual que `aRecurso()`: el compilador avisa
 * cuando se agrega una variante nueva a [DominioError], mientras que con `else` el error
 * se descubriría en silencio. Las variantes que el login no puede producir (los errores de
 * reporte y de medio, más `PermisoDenegado` y `ArchivoLocalAusente`) caen todas en
 * [ResultadoLogin.INESPERADO], que es exactamente lo que significan en contexto: si alguna
 * llegara, sería una fuga de capas. `PermisoDenegado` es el caso real y frecuente — un
 * `PERMISSION_DENIED` produce [DominioError.PermisoDenegado], y en la pantalla se ve como
 * "Algo salió mal"; aquí se ve como `inesperado`.
 *
 * `CuentaDeshabilitada` cae igual en `INESPERADO` a propósito: no es un paso del embudo
 * que el custodio pueda corregir, y la cuenta deshabilitada es un dato de provisioning que
 * la analítica de producto no debe medir.
 */
fun DominioError.aResultadoLogin(): ResultadoLogin = when (this) {
    // Los del login, uno a uno.
    DominioError.CredencialesInvalidas -> ResultadoLogin.CREDENCIALES_INVALIDAS
    DominioError.DemasiadosIntentos -> ResultadoLogin.DEMASIADOS_INTENTOS
    DominioError.CorreoNoAutorizado -> ResultadoLogin.CORREO_NO_AUTORIZADO
    DominioError.SinConexion -> ResultadoLogin.SIN_CONEXION
    // El servidor "caído" es el mismo paso medible que "sin red": el custodio ve lo mismo.
    DominioError.ServidorIndisponible -> ResultadoLogin.SIN_CONEXION
    is DominioError.Inesperado -> ResultadoLogin.INESPERADO
    // Los que el login no puede producir. Si alguno apareciera, sería una fuga de capas.
    is DominioError.PlacaVacia -> ResultadoLogin.INESPERADO
    is DominioError.YaExisteReporteActivo -> ResultadoLogin.INESPERADO
    DominioError.NoHayReporteActivo -> ResultadoLogin.INESPERADO
    DominioError.ReporteNoEncontrado -> ResultadoLogin.INESPERADO
    DominioError.ReporteCerradoInmutable -> ResultadoLogin.INESPERADO
    DominioError.ReporteYaDescartado -> ResultadoLogin.INESPERADO
    DominioError.ReporteSinMedios -> ResultadoLogin.INESPERADO
    DominioError.MotivoDescarteVacio -> ResultadoLogin.INESPERADO
    DominioError.LimiteDeMediosAlcanzado -> ResultadoLogin.INESPERADO
    DominioError.SinContenido -> ResultadoLogin.INESPERADO
    DominioError.SinEspacioEnDispositivo -> ResultadoLogin.INESPERADO
    DominioError.AudioExcedeCincoMinutos -> ResultadoLogin.INESPERADO
    is DominioError.EtiquetaInvalida -> ResultadoLogin.INESPERADO
    DominioError.FaltaSituacion -> ResultadoLogin.INESPERADO
    DominioError.MedioNoEncontrado -> ResultadoLogin.INESPERADO
    DominioError.MedioEnSubida -> ResultadoLogin.INESPERADO
    DominioError.MedioYaSubido -> ResultadoLogin.INESPERADO
    DominioError.CuentaDeshabilitada -> ResultadoLogin.INESPERADO
    DominioError.PermisoDenegado -> ResultadoLogin.INESPERADO
    DominioError.ArchivoLocalAusente -> ResultadoLogin.INESPERADO
}
