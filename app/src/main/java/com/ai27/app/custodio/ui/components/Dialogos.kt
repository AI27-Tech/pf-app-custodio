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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ai27.app.custodio.R
import com.ai27.app.custodio.ui.previews.DatosDemo

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
            TextButton(onClick = onCancelar) { Text(stringResource(R.string.comun_cancelar)) }
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
        title = { Text(stringResource(R.string.descarte_titulo)) },
        text = {
            Column {
                Text(stringResource(R.string.descarte_texto))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = motivo,
                    onValueChange = onMotivoCambio,
                    label = { Text(stringResource(R.string.descarte_motivo_rotulo)) },
                    placeholder = { Text(stringResource(R.string.descarte_motivo_placeholder)) },
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
                Text(
                    if (descartando) {
                        stringResource(R.string.descarte_descartando)
                    } else {
                        stringResource(R.string.descarte_confirmar)
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) { Text(stringResource(R.string.comun_cancelar)) }
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
    telefono: String,
    pendientesSinSubir: Int,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    val archivosSinSubir = pluralStringResource(
        R.plurals.archivos_sin_subir,
        pendientesSinSubir,
        pendientesSinSubir,
    )
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(stringResource(R.string.cierre_sesion_titulo)) },
        text = {
            Column {
                Text(stringResource(R.string.cierre_sesion_telefono, telefono))
                if (pendientesSinSubir > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.cierre_sesion_pendientes, archivosSinSubir),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirmar) {
                Text(
                    if (pendientesSinSubir > 0) {
                        stringResource(R.string.cierre_sesion_salir_de_todos_modos)
                    } else {
                        stringResource(R.string.cierre_sesion_salir)
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) { Text(stringResource(R.string.comun_cancelar)) }
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
        title = { Text(stringResource(R.string.comun_aviso)) },
        text = { Text(mensaje) },
        confirmButton = {
            TextButton(onClick = onCerrar) { Text(stringResource(R.string.comun_entendido)) }
        },
        modifier = modifier,
    )
}

@Preview(name = "Confirmación · Claro")
@Composable
private fun DialogoConfirmacionVistaClaro() {
    Vitrina(oscuro = false) {
        DialogoConfirmacion(
            visible = true,
            titulo = stringResource(R.string.archivos_borrar_titulo),
            texto = stringResource(R.string.archivos_borrar_texto),
            textoConfirmar = stringResource(R.string.archivos_borrar),
            onConfirmar = {},
            onCancelar = {},
        )
    }
}

@Preview(name = "Confirmación · Oscuro")
@Composable
private fun DialogoConfirmacionVistaOscuro() {
    Vitrina(oscuro = true) {
        DialogoConfirmacion(
            visible = true,
            titulo = stringResource(R.string.archivos_borrar_titulo),
            texto = stringResource(R.string.archivos_borrar_texto),
            textoConfirmar = stringResource(R.string.archivos_borrar),
            onConfirmar = {},
            onCancelar = {},
        )
    }
}

@Preview(name = "Descarte · Claro")
@Composable
private fun DialogoDescarteVistaClaro() {
    Vitrina(oscuro = false) {
        DialogoDescarte(
            visible = true,
            motivo = "Placa registrada equivocada.",
            descartando = false,
            onMotivoCambio = {},
            onConfirmar = {},
            onCancelar = {},
        )
    }
}

@Preview(name = "Descarte · Oscuro")
@Composable
private fun DialogoDescarteVistaOscuro() {
    Vitrina(oscuro = true) {
        DialogoDescarte(
            visible = true,
            motivo = "Placa registrada equivocada.",
            descartando = false,
            onMotivoCambio = {},
            onConfirmar = {},
            onCancelar = {},
        )
    }
}

@Preview(name = "Cerrar sesión · Claro")
@Composable
private fun DialogoCerrarSesionVistaClaro() {
    Vitrina(oscuro = false) {
        DialogoCerrarSesion(
            visible = true,
            telefono = DatosDemo.custodio.telefono,
            pendientesSinSubir = 2,
            onConfirmar = {},
            onCancelar = {},
        )
    }
}

@Preview(name = "Cerrar sesión · Oscuro")
@Composable
private fun DialogoCerrarSesionVistaOscuro() {
    Vitrina(oscuro = true) {
        DialogoCerrarSesion(
            visible = true,
            telefono = DatosDemo.custodio.telefono,
            pendientesSinSubir = 2,
            onConfirmar = {},
            onCancelar = {},
        )
    }
}

@Preview(name = "Error · Claro")
@Composable
private fun DialogoErrorVistaClaro() {
    Vitrina(oscuro = false) {
        DialogoError(
            mensaje = stringResource(R.string.error_sin_conexion),
            onCerrar = {},
        )
    }
}

@Preview(name = "Error · Oscuro")
@Composable
private fun DialogoErrorVistaOscuro() {
    Vitrina(oscuro = true) {
        DialogoError(
            mensaje = stringResource(R.string.error_sin_conexion),
            onCerrar = {},
        )
    }
}