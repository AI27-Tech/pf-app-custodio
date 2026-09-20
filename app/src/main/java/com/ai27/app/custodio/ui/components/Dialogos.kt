package com.ai27.app.custodio.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Diálogo de confirmación de una decisión destructiva o de cierre.
 *
 * Los cinco `AlertDialog` que la pantalla de captura tenía inline (borrar archivo, cerrar
 * viaje, descartar viaje, cerrar sesión y aviso de error) salen a la biblioteca en C3.
 * La geometría es la que Material 3 ya ofrece; Nocturne no define superficie propia para
 * el diálogo, así que se conserva el contrato de M3.
 */
@Composable
fun DialogoConfirmacion(
    visible: Boolean,
    titulo: String,
    texto: String,
    textoConfirmar: String,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
    modifier: Modifier = Modifier,
    confirmarHabilitado: Boolean = true,
    textoConfirmarProcesando: String? = null,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(titulo) },
        text = { Text(texto) },
        confirmButton = {
            TextButton(
                onClick = onConfirmar,
                enabled = confirmarHabilitado,
            ) {
                Text(textoConfirmarProcesando ?: textoConfirmar)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) { Text("Cancelar") }
        },
        modifier = modifier,
    )
}

/**
 * Diálogo de descarte de viaje: exige un motivo obligatorio antes de permitir confirmar.
 */
@Composable
fun DialogoDescarte(
    visible: Boolean,
    motivo: String,
    descartando: Boolean,
    onMotivoCambio: (String) -> Unit,
    onConfirmar: (motivo: String) -> Unit,
    onCancelar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("¿Descartar el viaje?") },
        text = {
            Column {
                Text(
                    "Esto marca el viaje como descartado. Lo que ya se subió " +
                        "no se borra; se excluye de los reportes del analista.",
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = motivo,
                    onValueChange = onMotivoCambio,
                    label = { Text("Motivo del descarte") },
                    placeholder = { Text("Obligatorio") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirmar(motivo) },
                enabled = motivo.isNotBlank() && !descartando,
            ) {
                Text(if (descartando) "Descartando…" else "Descartar")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) { Text("Cancelar") }
        },
        modifier = modifier,
    )
}

/**
 * Diálogo de cierre de sesión con advertencia de archivos pendientes.
 */
@Composable
fun DialogoCerrarSesion(
    visible: Boolean,
    correo: String,
    pendientesSinSubir: Int,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("¿Cerrar sesión?") },
        text = {
            Column {
                Text("Sesión de $correo")
                if (pendientesSinSubir > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tienes $pendientesSinSubir archivos sin subir. " +
                            "Si cierras sesión, esa evidencia perderá su cola de subida.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirmar) {
                Text(if (pendientesSinSubir > 0) "Salir de todos modos" else "Salir")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) { Text("Cancelar") }
        },
        modifier = modifier,
    )
}

/**
 * Diálogo de aviso/error con un único botón de cierre.
 */
@Composable
fun DialogoError(
    mensaje: String,
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Aviso") },
        text = { Text(mensaje) },
        confirmButton = {
            TextButton(onClick = onCerrar) { Text("Entendido") }
        },
        modifier = modifier,
    )
}