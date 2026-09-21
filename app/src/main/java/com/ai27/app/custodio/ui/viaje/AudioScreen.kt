package com.ai27.app.custodio.ui.viaje

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ai27.app.custodio.R
import com.ai27.app.custodio.ui.captura.CapturaUiState
import com.ai27.app.custodio.ui.captura.HojaEtiqueta
import com.ai27.app.custodio.ui.components.AvisoSubidaFlotante
import com.ai27.app.custodio.ui.components.BarraSuperior
import com.ai27.app.custodio.ui.components.BotonGrabacion
import com.ai27.app.custodio.ui.components.BotonSecundario
import com.ai27.app.custodio.ui.components.DialogoError
import com.ai27.app.custodio.ui.components.FranjaAviso
import com.ai27.app.custodio.ui.components.Vitrina
import com.ai27.app.custodio.ui.previews.DatosDemo
import com.ai27.app.custodio.ui.theme.TabularNumberStyle

/**
 * Pantalla de grabación de nota de voz (`grabaci_n_de_audio_nota_de_voz`).
 *
 * El botón redondo ([BotonGrabacion]) alterna iniciar/detener; el cronómetro usa el
 * estilo tabular del sistema y se detiene solo a los 5 minutos. Al terminar, el medio
 * abre [HojaEtiqueta] con su etiqueta obligatoria.
 */
@Composable
fun AudioScreen(
    estado: CapturaUiState,
    onAtras: () -> Unit,
    onIniciarAudio: () -> Unit,
    onDetenerAudio: () -> Unit,
    onDescartarAudio: () -> Unit,
    onGuardarEtiqueta: (medioId: String, etiquetaId: String) -> Unit,
    onUbicacionTextoCambio: (String) -> Unit,
    onCerrarHoja: () -> Unit,
    onConsumirError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Column(modifier.fillMaxSize()) {
            BarraSuperior(titulo = stringResource(R.string.nota_de_voz), onAtras = onAtras)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                FranjaAviso(texto = stringResource(R.string.audio_aviso))
                Spacer(Modifier.height(32.dp))

                if (estado.grabandoAudio) {
                    Text(
                        text = formatearDuracion(estado.segundosAudio),
                        style = TabularNumberStyle.copy(
                            fontSize = MaterialTheme.typography.headlineMedium.fontSize,
                        ),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.audio_grabando),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else if (estado.capturando) {
                    Text(
                        text = stringResource(R.string.audio_guardando),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.audio_toca_para_iniciar),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(32.dp))

                BotonGrabacion(
                    grabando = estado.grabandoAudio,
                    onClick = if (estado.grabandoAudio) onDetenerAudio else onIniciarAudio,
                )

                if (estado.grabandoAudio) {
                    Spacer(Modifier.height(24.dp))
                    BotonSecundario(
                        texto = stringResource(R.string.comun_cancelar),
                        onClick = onDescartarAudio,
                    )
                } else {
                    Spacer(Modifier.height(24.dp))
                    Spacer(Modifier.size(48.dp))
                }
            }
        }

        AvisoSubidaFlotante(
            evento = estado.avisoEncolado,
            texto = stringResource(R.string.aviso_subida_en_breve),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 24.dp)
                .padding(top = 100.dp),
        )
    }

    estado.medioPendienteDeEtiquetar?.let {
        HojaEtiqueta(
            estado = estado,
            onGuardar = onGuardarEtiqueta,
            onUbicacionTextoCambio = onUbicacionTextoCambio,
            onCerrar = onCerrarHoja,
        )
    }
    estado.error?.let { error ->
        DialogoError(mensaje = stringResource(error), onCerrar = onConsumirError)
    }
}

private fun formatearDuracion(segundos: Long): String {
    val min = segundos / 60
    val seg = segundos % 60
    return "%d:%02d".format(min, seg)
}

@Preview(name = "Claro")
@Composable
private fun AudioVistaClaro() {
    Vitrina(oscuro = false) {
        AudioScreen(
            estado = CapturaUiState(
                custodio = DatosDemo.custodio,
                reporte = DatosDemo.reporteActivo,
                grabandoAudio = true,
                segundosAudio = 187L,
            ),
            onAtras = {},
            onIniciarAudio = {},
            onDetenerAudio = {},
            onDescartarAudio = {},
            onGuardarEtiqueta = { _, _ -> },
            onUbicacionTextoCambio = {},
            onCerrarHoja = {},
            onConsumirError = {},
        )
    }
}

@Preview(name = "Oscuro")
@Composable
private fun AudioVistaOscuro() {
    Vitrina(oscuro = true) {
        AudioScreen(
            estado = CapturaUiState(
                custodio = DatosDemo.custodio,
                reporte = DatosDemo.reporteActivo,
                grabandoAudio = true,
                segundosAudio = 187L,
            ),
            onAtras = {},
            onIniciarAudio = {},
            onDetenerAudio = {},
            onDescartarAudio = {},
            onGuardarEtiqueta = { _, _ -> },
            onUbicacionTextoCambio = {},
            onCerrarHoja = {},
            onConsumirError = {},
        )
    }
}