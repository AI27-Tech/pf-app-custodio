package com.ai27.app.custodio.domain.error

sealed interface DominioError {
    data class PlacaVacia(val placas: String?) : DominioError
    data class YaExisteReporteActivo(val reporteId: String) : DominioError
    data object NoHayReporteActivo : DominioError
    data object ReporteNoEncontrado : DominioError
    data object ReporteCerradoInmutable : DominioError
    data object ReporteYaDescartado : DominioError
    data object ReporteSinMedios : DominioError
    data object MotivoDescarteVacio : DominioError
    data object LimiteDeMediosAlcanzado : DominioError
    data object SinContenido : DominioError
    data object SinEspacioEnDispositivo : DominioError
    data object AudioExcedeCincoMinutos : DominioError
    data class EtiquetaInvalida(val etiquetaId: String) : DominioError
    data object FaltaSituacion : DominioError
    data object MedioNoEncontrado : DominioError
    data object MedioEnSubida : DominioError
    data object MedioYaSubido : DominioError

    /** Errores de sesión por correo + contraseña (plan de correo, Task 5). */

    /**
     * Firebase Auth rechazó el intento. Se **fusiona** a propósito: el backend no distingue
     * entre correo inexistente y contraseña equivocada (y `ERROR_INVALID_EMAIL` cae aquí
     * también), así que el custodio no puede deducir qué parte falló ni el atacante puede
     * enumerar cuentas. El formato de correo se valida antes, en la pantalla.
     */
    data object CredencialesInvalidas : DominioError
    /** La cuenta existe pero está deshabilitada por el administrador. */
    data object CuentaDeshabilitada : DominioError
    data object DemasiadosIntentos : DominioError

    /** El correo autenticó pero no tiene documento en `correos_autorizados` (Task 4). */
    data object CorreoNoAutorizado : DominioError

    data object SinConexion : DominioError
    /** El servidor respondió pero estuvo temporalmente indisponible (UNAVAILABLE/DEADLINE_EXCEEDED). */
    data object ServidorIndisponible : DominioError
    /** Las reglas de Firestore/Storage denegaron la escritura: reintentar no lo arregla. */
    data object PermisoDenegado : DominioError
    /** El archivo local del medio ya no existe: no hay nada que subir. */
    data object ArchivoLocalAusente : DominioError
    /**
     * La foto sigue cruda porque el revelado no pudo escribirla (p. ej. disco lleno). No se
     * sube sin revelar; es transitorio porque el siguiente intento puede revelarla.
     */
    data object FotoSinRevelar : DominioError
    data class Inesperado(val causa: Throwable) : DominioError
}