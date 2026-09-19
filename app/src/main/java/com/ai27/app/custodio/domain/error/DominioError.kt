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
    data object AudioExcedeCincoMinutos : DominioError
    data class EtiquetaInvalida(val etiquetaId: String) : DominioError
    data object MedioNoEncontrado : DominioError
    data object MedioEnSubida : DominioError
    data object MedioYaSubido : DominioError
    data object CredencialesInvalidas : DominioError
    data object SinConexion : DominioError
    data class Inesperado(val causa: Throwable) : DominioError
}