package com.ai27.app.custodio.ui.error

import androidx.annotation.StringRes
import com.ai27.app.custodio.R
import com.ai27.app.custodio.domain.error.DominioError

/**
 * Traducción de un error de dominio a un recurso de string usuario-visible.
 *
 * El dominio es kotlin puro y no conoce Android, así que el mensaje amable vive en la
 * capa de presentación. Los ViewModels guardan el recurso en [com.ai27.app.custodio.ui.login.LoginUiState.error]
 * y las pantallas lo resuelven con [androidx.compose.ui.res.stringResource]. No hay
 * argumentos de formato: cuando el error tiene datos (p. ej. [DominioError.PlacaVacia])
 * se elige aquí un mensaje estático.
 */
@StringRes
fun DominioError.aRecurso(): Int = when (this) {
    is DominioError.PlacaVacia -> R.string.error_placa_vacia_sin_valor
    is DominioError.YaExisteReporteActivo -> R.string.error_ya_existe_activo
    DominioError.NoHayReporteActivo -> R.string.error_no_hay_activo
    DominioError.ReporteNoEncontrado -> R.string.error_no_encontrado
    DominioError.ReporteCerradoInmutable -> R.string.error_cerrado_inmutable
    DominioError.ReporteYaDescartado -> R.string.error_ya_descartado
    DominioError.ReporteSinMedios -> R.string.error_sin_medios
    DominioError.MotivoDescarteVacio -> R.string.error_motivo_descarte_vacio
    DominioError.LimiteDeMediosAlcanzado -> R.string.error_limite_medios
    DominioError.SinContenido -> R.string.error_sin_contenido
    DominioError.SinEspacioEnDispositivo -> R.string.error_sin_espacio
    DominioError.AudioExcedeCincoMinutos -> R.string.error_audio_excede_max
    is DominioError.EtiquetaInvalida -> R.string.error_etiqueta_invalida
    DominioError.FaltaSituacion -> R.string.error_falta_situacion
    DominioError.MedioNoEncontrado -> R.string.error_medio_no_encontrado
    DominioError.MedioEnSubida -> R.string.error_medio_en_subida
    DominioError.MedioYaSubido -> R.string.error_medio_ya_subido
    DominioError.CredencialesInvalidas -> R.string.error_credenciales
    DominioError.SinConexion -> R.string.error_sin_conexion
    is DominioError.Inesperado -> R.string.error_inesperado
}