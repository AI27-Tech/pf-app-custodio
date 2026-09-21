package com.ai27.app.custodio.ui.viaje

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ai27.app.custodio.ui.captura.CapturaUiState
import com.ai27.app.custodio.ui.components.BarraSuperior
import com.ai27.app.custodio.ui.components.BotonPrimario
import com.ai27.app.custodio.ui.components.BotonSecundario
import com.ai27.app.custodio.ui.components.DialogoConfirmacion
import com.ai27.app.custodio.ui.components.DialogoDescarte
import com.ai27.app.custodio.ui.components.DialogoError
import com.ai27.app.custodio.ui.components.FranjaAviso
import com.ai27.app.custodio.ui.components.RenglonResumen
import com.ai27.app.custodio.ui.components.RenglonSwitch

/**
 * Pantalla de cierre de viaje (`cerrar_reporte_estructura`).
 *
 * Resumen de lo capturado, interruptor de novedad y comentario opcionales, y las dos
 * salidas: cerrar (confirmación) o descartar (motivo obligatorio). Al cerrar, la novedad
 * y el comentario se persisten junto con el reporte; al cerrar/descartar con éxito el
 * reporte queda nulo y la pantalla vuelve a Reporte.
 */
@Composable
fun CierreScreen(
    estado: CapturaUiState,
    onAtras: () -> Unit,
    onCerrarViaje: (comentario: String?, hayNovedad: Boolean) -> Unit,
    onDescartarViaje: (motivo: String) -> Unit,
    onConsumirError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var novedad by remember { mutableStateOf(false) }
    var comentario by remember { mutableStateOf("") }
    var mostrarConfirmacionCierre by remember { mutableStateOf(false) }
    var mostrarDescarte by remember { mutableStateOf(false) }
    var motivoDescarte by remember { mutableStateOf("") }

    LaunchedEffect(estado.reporte) {
        if (estado.reporte == null) onAtras()
    }

    val resumen = estado.reporte?.resumenMedios

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        BarraSuperior(titulo = "Cerrar viaje", onAtras = onAtras)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Resumen del viaje",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            )
            RenglonResumen(etiqueta = "Fotografías", valor = "${resumen?.fotos ?: 0}")
            RenglonResumen(etiqueta = "Notas de voz", valor = "${resumen?.audios ?: 0}")
            if ((resumen?.durationMsTotal ?: 0L) > 0L) {
                val duracionMin =
                    (resumen?.durationMsTotal?.plus(999L) ?: 0L) / 1_000L
                RenglonResumen(
                    etiqueta = "Audio total",
                    valor = formatearCierre(duracionMin),
                    unidad = "min",
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            RenglonSwitch(
                titulo = "Novedad",
                descripcion = "Marca si en el viaje hubo alguna incidencia",
                marcado = novedad,
                onCambiar = { novedad = it },
            )

            OutlinedTextField(
                value = comentario,
                onValueChange = { comentario = it },
                label = { Text("Comentario (opcional)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            if (estado.pendientesSinSubir > 0) {
                FranjaAviso(
                    texto = "Quedan ${estado.pendientesSinSubir} archivos sin subir. " +
                        "La sincronización continúa en segundo plano.",
                )
            }

            BotonPrimario(
                texto = if (estado.cerrandoViaje) "Cerrando…" else "Cerrar viaje",
                onClick = { mostrarConfirmacionCierre = true },
                habilitado = !estado.cerrandoViaje && !estado.descartandoViaje,
            )
            BotonSecundario(
                texto = if (estado.descartandoViaje) "Descartando…" else "Descartar viaje",
                onClick = { mostrarDescarte = true },
                habilitado = !estado.cerrandoViaje && !estado.descartandoViaje,
            )
        }
    }

    DialogoConfirmacion(
        visible = mostrarConfirmacionCierre,
        titulo = "¿Cerrar el viaje?",
        texto = "El viaje quedará cerrado con ${estado.medios.size} archivos.",
        textoConfirmar = "Cerrar",
        textoConfirmarProcesando = if (estado.cerrandoViaje) "Cerrando…" else null,
        confirmarHabilitado = !estado.cerrandoViaje,
        onConfirmar = {
            onCerrarViaje(comentario.trim().ifEmpty { null }, novedad)
            mostrarConfirmacionCierre = false
        },
        onCancelar = { mostrarConfirmacionCierre = false },
    )

    DialogoDescarte(
        visible = mostrarDescarte,
        motivo = motivoDescarte,
        descartando = estado.descartandoViaje,
        onMotivoCambio = { motivoDescarte = it },
        onConfirmar = { motivo ->
            onDescartarViaje(motivo)
            mostrarDescarte = false
            motivoDescarte = ""
        },
        onCancelar = {
            mostrarDescarte = false
            motivoDescarte = ""
        },
    )

    estado.error?.let { mensaje ->
        DialogoError(mensaje = mensaje, onCerrar = onConsumirError)
    }
}

private fun formatearCierre(segundos: Long): String {
    val min = segundos / 60
    val seg = segundos % 60
    return "%d:%02d".format(min, seg)
}