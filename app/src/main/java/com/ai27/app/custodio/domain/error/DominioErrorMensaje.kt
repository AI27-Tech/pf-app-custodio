package com.ai27.app.custodio.domain.error

fun DominioError.aMensajeUsuario(): String = when (this) {
    is DominioError.PlacaVacia -> "La placa no es válida: ${placas ?: "escribe un número de placa"}."
    is DominioError.YaExisteReporteActivo -> "Ya hay un viaje en curso en este teléfono."
    DominioError.NoHayReporteActivo -> "No hay un viaje en curso."
    DominioError.ReporteNoEncontrado -> "No se encontró el viaje."
    DominioError.ReporteCerradoInmutable -> "El viaje ya está cerrado."
    DominioError.ReporteYaDescartado -> "El viaje ya fue descartado."
    DominioError.ReporteSinMedios -> "El viaje debe tener al menos un archivo capturado."
    DominioError.MotivoDescarteVacio -> "Escribe el motivo del descarte."
    DominioError.LimiteDeMediosAlcanzado -> "Límite alcanzado: 50 archivos por viaje."
    DominioError.SinContenido -> "El archivo capturado está vacío."
    DominioError.AudioExcedeCincoMinutos -> "La nota de voz no puede exceder 5 minutos."
    is DominioError.EtiquetaInvalida -> "La etiqueta seleccionada no es válida."
    DominioError.MedioNoEncontrado -> "El archivo ya no existe."
    DominioError.MedioEnSubida -> "El archivo ya está en subida y no puede borrarse."
    DominioError.MedioYaSubido -> "El archivo ya fue subido."
    DominioError.CredencialesInvalidas -> "Correo o contraseña incorrectos."
    DominioError.SinConexion -> "Sin conexión. Revisa tu red e inténtalo de nuevo."
    is DominioError.Inesperado -> "Algo salió mal. Intenta de nuevo."
}