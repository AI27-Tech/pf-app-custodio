package com.ai27.app.custodio.ui.viaje

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai27.app.custodio.ui.captura.CapturaUiState
import com.ai27.app.custodio.ui.captura.HojaEtiqueta
import com.ai27.app.custodio.ui.components.BarraSuperior
import com.ai27.app.custodio.ui.components.BotonGrabacion
import com.ai27.app.custodio.ui.components.BotonSecundario
import com.ai27.app.custodio.ui.components.DialogoError
import com.ai27.app.custodio.ui.components.FranjaAviso
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
    Column(modifier.fillMaxSize()) {
        BarraSuperior(titulo = "Nota de voz", onAtras = onAtras)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            FranjaAviso(texto = "Máximo 5:00 por nota. Habla claro y cerca del micrófono.")
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
                    text = "GRABANDO",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            } else if (estado.capturando) {
                Text(
                    text = "Guardando nota…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "Toca el botón para iniciar",
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
                    texto = "Cancelar",
                    onClick = onDescartarAudio,
                )
            } else {
                Spacer(Modifier.height(24.dp))
                Spacer(Modifier.size(48.dp))
            }
        }
    }

    estado.medioPendienteDeEtiquetar?.let {
        HojaEtiqueta(
            estado = estado,
            onGuardar = onGuardarEtiqueta,
            onUbicacionTextoCambio = onUbicacionTextoCambio,
            onCerrar = onCerrarHoja,
        )
    }
    estado.error?.let { mensaje ->
        DialogoError(mensaje = mensaje, onCerrar = onConsumirError)
    }
}

private fun formatearDuracion(segundos: Long): String {
    val min = segundos / 60
    val seg = segundos % 60
    return "%d:%02d".format(min, seg)
}