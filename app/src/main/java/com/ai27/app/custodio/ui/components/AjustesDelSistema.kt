package com.ai27.app.custodio.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

// S9: lleva al custodio a los Ajustes del sistema para conceder un permiso que el
// diálogo ya no puede pedir (elige "No volver a preguntar") o que se denegó antes.
// Al volver a la app, cada pantalla re-chequea el permiso en ON_RESUME.
internal fun abrirAjustesDelSistema(context: Context) {
    val paquete = context.packageName
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", paquete, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}