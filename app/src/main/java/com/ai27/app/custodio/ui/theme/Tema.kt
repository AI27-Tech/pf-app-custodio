package com.ai27.app.custodio.ui.theme

import androidx.compose.runtime.compositionLocalOf

/**
 * Estado del conmutador de tema de la pantalla de Ajustes.
 *
 * La decisión de aspecto vive en `MainActivity` (donde se lee y persiste la preferencia);
 * estos dos valores la propagan hasta Ajustes sin atravesar el grafo de navegación:
 * [LocalTemaOscuro] es la lectura y [LocalCambiarTemaOscuro] el mutador.
 */
val LocalTemaOscuro = compositionLocalOf { true }

val LocalCambiarTemaOscuro = compositionLocalOf<(Boolean) -> Unit> { {} }