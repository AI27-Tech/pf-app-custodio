package com.ai27.app.custodio.ui.ajustes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ai27.app.custodio.R
import com.ai27.app.custodio.ui.components.BarraSuperior
import com.ai27.app.custodio.ui.components.BotonSecundario
import com.ai27.app.custodio.ui.components.DialogoCerrarSesion
import com.ai27.app.custodio.ui.components.DialogoError
import com.ai27.app.custodio.ui.components.FranjaAviso
import com.ai27.app.custodio.ui.components.RenglonResumen
import com.ai27.app.custodio.ui.components.RenglonSwitch
import com.ai27.app.custodio.ui.components.Vitrina
import com.ai27.app.custodio.ui.previews.DatosDemo
import com.ai27.app.custodio.ui.theme.LocalCambiarTemaOscuro
import com.ai27.app.custodio.ui.theme.LocalTemaOscuro

@Composable
fun AjustesRoute(
    modifier: Modifier = Modifier,
    viewModel: AjustesViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsState()
    val oscuro = LocalTemaOscuro.current
    val cambiarTema = LocalCambiarTemaOscuro.current
    AjustesScreen(
        estado = estado,
        oscuro = oscuro,
        onCambiarTema = cambiarTema,
        onSolicitarCerrarSesion = viewModel::solicitarCerrarSesion,
        onCancelarCerrarSesion = viewModel::cancelarCerrarSesion,
        onConfirmarCerrarSesion = viewModel::confirmarCerrarSesion,
        onConsumirError = viewModel::consumirError,
        modifier = modifier,
    )
}

/**
 * Pestaña de Ajustes (`ajustes_*`): tema claro/oscuro, sesión de custodio, pendientes de
 * subida y cierre de sesión.
 *
 * El botón de salida hereda la regla del producto: con archivos sin subir queda
 * deshabilitado (variante del mockup) para no orfanar la evidencia de su cola.
 */
@Composable
fun AjustesScreen(
    estado: AjustesUiState,
    oscuro: Boolean,
    onCambiarTema: (Boolean) -> Unit,
    onSolicitarCerrarSesion: () -> Unit,
    onCancelarCerrarSesion: () -> Unit,
    onConfirmarCerrarSesion: () -> Unit,
    onConsumirError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        BarraSuperior(titulo = stringResource(R.string.destino_ajustes))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TituloSeccion(stringResource(R.string.ajustes_entorno))
            RenglonSwitch(
                titulo = stringResource(R.string.ajustes_modo_nocturno),
                descripcion = stringResource(R.string.ajustes_modo_nocturno_descripcion),
                marcado = oscuro,
                onCambiar = onCambiarTema,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            TituloSeccion(stringResource(R.string.ajustes_cuenta))
            RenglonResumen(
                etiqueta = stringResource(R.string.ajustes_sesion_activa),
                valor = estado.custodio?.telefono.orEmpty(),
            )
            RenglonResumen(
                etiqueta = stringResource(R.string.ajustes_pendientes_subida),
                valor = "${estado.pendientesSinSubir}",
            )

            Spacer(Modifier.height(4.dp))

            if (estado.pendientesSinSubir > 0) {
                FranjaAviso(
                    texto = stringResource(
                        R.string.ajustes_cierre_deshabilitado,
                        pluralStringResource(
                            R.plurals.archivos_pendientes_de_subida,
                            estado.pendientesSinSubir,
                            estado.pendientesSinSubir,
                        ),
                    ),
                )
            }
            BotonSecundario(
                texto = stringResource(R.string.ajustes_cerrar_sesion_custodio),
                onClick = onSolicitarCerrarSesion,
                habilitado = estado.pendientesSinSubir == 0,
            )
        }
    }

    DialogoCerrarSesion(
        visible = estado.confirmacionCerrarSesion,
        telefono = estado.custodio?.telefono.orEmpty(),
        pendientesSinSubir = estado.pendientesSinSubir,
        onConfirmar = onConfirmarCerrarSesion,
        onCancelar = onCancelarCerrarSesion,
    )
    estado.error?.let { codigo ->
        // U7: un error al cerrar la sesión no debe quedar en callejón sin salida: la
        // acción repite el intento de cerrar en lugar de solo "Entendido".
        DialogoError(
            mensaje = stringResource(codigo),
            onCerrar = onConsumirError,
            textoAccion = stringResource(R.string.comun_reintentar),
            onAccion = onConfirmarCerrarSesion,
        )
    }
}

@Composable
private fun TituloSeccion(texto: String) {
    Text(
        text = texto.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Preview(name = "Claro")
@Composable
private fun AjustesVistaClaro() {
    Vitrina(oscuro = false) {
        AjustesScreen(
            estado = AjustesUiState(
                custodio = DatosDemo.custodio,
                pendientesSinSubir = 0,
            ),
            oscuro = false,
            onCambiarTema = {},
            onSolicitarCerrarSesion = {},
            onCancelarCerrarSesion = {},
            onConfirmarCerrarSesion = {},
            onConsumirError = {},
        )
    }
}

@Preview(name = "Oscuro")
@Composable
private fun AjustesVistaOscuro() {
    Vitrina(oscuro = true) {
        AjustesScreen(
            estado = AjustesUiState(
                custodio = DatosDemo.custodio,
                pendientesSinSubir = 0,
            ),
            oscuro = true,
            onCambiarTema = {},
            onSolicitarCerrarSesion = {},
            onCancelarCerrarSesion = {},
            onConfirmarCerrarSesion = {},
            onConsumirError = {},
        )
    }
}

@Preview(name = "Cierre deshabilitado · Claro")
@Composable
private fun AjustesCierreDeshabilitadoVistaClaro() {
    Vitrina(oscuro = false) {
        AjustesScreen(
            estado = AjustesUiState(
                custodio = DatosDemo.custodio,
                pendientesSinSubir = 3,
            ),
            oscuro = false,
            onCambiarTema = {},
            onSolicitarCerrarSesion = {},
            onCancelarCerrarSesion = {},
            onConfirmarCerrarSesion = {},
            onConsumirError = {},
        )
    }
}

@Preview(name = "Cierre deshabilitado · Oscuro")
@Composable
private fun AjustesCierreDeshabilitadoVistaOscuro() {
    Vitrina(oscuro = true) {
        AjustesScreen(
            estado = AjustesUiState(
                custodio = DatosDemo.custodio,
                pendientesSinSubir = 3,
            ),
            oscuro = true,
            onCambiarTema = {},
            onSolicitarCerrarSesion = {},
            onCancelarCerrarSesion = {},
            onConfirmarCerrarSesion = {},
            onConsumirError = {},
        )
    }
}