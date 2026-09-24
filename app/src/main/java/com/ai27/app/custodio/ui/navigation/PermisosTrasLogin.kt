package com.ai27.app.custodio.ui.navigation

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

// Todos los permisos que el flujo de captura necesita, pedidos juntos en un solo diálogo
// del sistema (cámara, micrófono y ubicación fina + aproximada).
internal val PERMISOS_DE_CAPTURA = arrayOf(
    Manifest.permission.CAMERA,
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

private const val NOMBRE_PREFS = "preferencias"
private const val CLAVE_PERMISOS_SOLICITADOS = "permisos_solicitados"

/**
 * Pide los permisos de captura justo después del primer inicio de sesión, en lugar de
 * pedirlos por pantalla al abrir el visor. La bandera persistente hace que el diálogo
 * salga una sola vez por instalación; lo que quede denegado se cubre con los fallbacks de
 * cámara/audio (re-petición o Ajustes del sistema).
 */
@Composable
internal fun SolicitarPermisosTrasInicioDeSesion(sesionActiva: Boolean) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(NOMBRE_PREFS, Context.MODE_PRIVATE)
    }
    val lanzadorPermisos = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* resultado sin uso: solo se pregunta una vez por instalación */ }

    LaunchedEffect(sesionActiva) {
        if (sesionActiva && !prefs.getBoolean(CLAVE_PERMISOS_SOLICITADOS, false)) {
            // Se marca ANTES de lanzar para que un ciclo de vida interrumpido no vuelva
            // a abrir el diálogo (el registro de Activity Result reentregaría el resultado).
            prefs.edit().putBoolean(CLAVE_PERMISOS_SOLICITADOS, true).apply()
            lanzadorPermisos.launch(PERMISOS_DE_CAPTURA)
        }
    }
}