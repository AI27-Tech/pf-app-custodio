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

    /** Error de sesión por SMS (PHONE_AUTH_PLAN Task 3). */
    data object NumeroInvalido : DominioError
    data object CodigoInvalido : DominioError
    data object CodigoExpirado : DominioError
    data object DemasiadosIntentos : DominioError
    /** El envío del SMS no obtuvo respuesta a tiempo (p. ej. el broker de Phone Auth no responde). */
    data object SinRespuestaSms : DominioError

    /** El número verificó el OTP pero no tiene documento en `telefonos_autorizados` (Task 4). */
    data object TelefonoNoAutorizado : DominioError

    data object SinConexion : DominioError
    /** El servidor respondió pero estuvo temporalmente indisponible (UNAVAILABLE/DEADLINE_EXCEEDED). */
    data object ServidorIndisponible : DominioError
    /** Las reglas de Firestore/Storage denegaron la escritura: reintentar no lo arregla. */
    data object PermisoDenegado : DominioError
    /** El archivo local del medio ya no existe: no hay nada que subir. */
    data object ArchivoLocalAusente : DominioError
    data class Inesperado(val causa: Throwable) : DominioError
}