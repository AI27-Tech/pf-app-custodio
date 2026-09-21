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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ai27.app.custodio.ui.components.BarraSuperior
import com.ai27.app.custodio.ui.components.BotonSecundario
import com.ai27.app.custodio.ui.components.DialogoCerrarSesion
import com.ai27.app.custodio.ui.components.DialogoError
import com.ai27.app.custodio.ui.components.FranjaAviso
import com.ai27.app.custodio.ui.components.RenglonResumen
import com.ai27.app.custodio.ui.components.RenglonSwitch
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
        BarraSuperior(titulo = "Ajustes")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TituloSeccion("Entorno y visualización")
            RenglonSwitch(
                titulo = "Modo nocturno",
                descripcion = "Aspecto oscuro de alto contraste para la cabina",
                marcado = oscuro,
                onCambiar = onCambiarTema,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            TituloSeccion("Cuenta y sincronización")
            RenglonResumen(
                etiqueta = "Sesión de custodio activa",
                valor = estado.custodio?.correo.orEmpty(),
            )
            RenglonResumen(
                etiqueta = "Archivos pendientes de subida",
                valor = "${estado.pendientesSinSubir}",
            )

            Spacer(Modifier.height(4.dp))

            if (estado.pendientesSinSubir > 0) {
                FranjaAviso(
                    texto = "Cierre de sesión deshabilitado: hay " +
                        "${estado.pendientesSinSubir} archivos pendientes de subida.",
                )
            }
            BotonSecundario(
                texto = "Cerrar sesión de custodio",
                onClick = onSolicitarCerrarSesion,
                habilitado = estado.pendientesSinSubir == 0,
            )
        }
    }

    DialogoCerrarSesion(
        visible = estado.confirmacionCerrarSesion,
        correo = estado.custodio?.correo.orEmpty(),
        pendientesSinSubir = estado.pendientesSinSubir,
        onConfirmar = onConfirmarCerrarSesion,
        onCancelar = onCancelarCerrarSesion,
    )
    estado.error?.let { mensaje ->
        DialogoError(mensaje = mensaje, onCerrar = onConsumirError)
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