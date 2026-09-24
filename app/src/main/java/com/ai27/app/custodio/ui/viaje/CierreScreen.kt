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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ai27.app.custodio.R
import com.ai27.app.custodio.domain.model.ResumenMedios
import com.ai27.app.custodio.domain.model.TipoMedio
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
import com.ai27.app.custodio.ui.components.Vitrina
import com.ai27.app.custodio.ui.previews.DatosDemo

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
    var novedad by rememberSaveable { mutableStateOf(false) }
    var comentario by rememberSaveable { mutableStateOf("") }
    var mostrarConfirmacionCierre by rememberSaveable { mutableStateOf(false) }
    var mostrarDescarte by rememberSaveable { mutableStateOf(false) }
    var motivoDescarte by rememberSaveable { mutableStateOf("") }
    // U11: si el custodio escribe/activa algo y pulsa atrás, se le pregunta si de verdad quiere
    // salir: el texto del cierre a medias se pierde silenciosamente hoy (AUDIT.md U11).
    var mostrarConfirmacionDescarteSalida by rememberSaveable { mutableStateOf(false) }
    val hayCambiosEnCierre = novedad || comentario.isNotBlank()

    LaunchedEffect(estado.reporte) {
        if (estado.reporte == null) onAtras()
    }

    val resumen = ResumenMedios(
        fotos = estado.medios.count { it.tipo == TipoMedio.FOTO },
        audios = estado.medios.count { it.tipo == TipoMedio.AUDIO },
        durationMsTotal = estado.medios.sumOf { it.durationMs ?: 0L },
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        BarraSuperior(
            titulo = stringResource(R.string.cerrar_viaje),
            // U11: si el custodio toca atrás con novedad o comentario escritos no se pierden
            // en silencio: atrás abre la confirmación en lugar de salir de una (AUDIT.md U11).
            onAtras = {
                if (hayCambiosEnCierre) {
                    mostrarConfirmacionDescarteSalida = true
                } else {
                    onAtras()
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.cierre_resumen),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            )
            RenglonResumen(
                etiqueta = stringResource(R.string.cierre_fotografias),
                valor = "${resumen.fotos}",
            )
            RenglonResumen(
                etiqueta = stringResource(R.string.cierre_notas_voz),
                valor = "${resumen.audios}",
            )
            if (resumen.durationMsTotal > 0L) {
                val duracionMin = resumen.durationMsTotal.plus(999L) / 1_000L
                RenglonResumen(
                    etiqueta = stringResource(R.string.cierre_audio_total),
                    valor = formatearCierre(duracionMin),
                    unidad = stringResource(R.string.cierre_minutos),
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            RenglonSwitch(
                titulo = stringResource(R.string.cierre_novedad),
                descripcion = stringResource(R.string.cierre_novedad_descripcion),
                marcado = novedad,
                onCambiar = { novedad = it },
            )

            OutlinedTextField(
                value = comentario,
                onValueChange = { comentario = it },
                label = { Text(stringResource(R.string.cierre_comentario)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            if (estado.pendientesSinSubir > 0) {
                val pendientes = pluralStringResource(
                    R.plurals.archivos_sin_subir,
                    estado.pendientesSinSubir,
                    estado.pendientesSinSubir,
                )
                FranjaAviso(
                    texto = stringResource(R.string.cierre_pendientes, pendientes),
                )
            }

            BotonPrimario(
                texto = if (estado.cerrandoViaje) {
                    stringResource(R.string.cierre_cerrando)
                } else {
                    stringResource(R.string.cerrar_viaje)
                },
                onClick = { mostrarConfirmacionCierre = true },
                habilitado = !estado.cerrandoViaje && !estado.descartandoViaje,
            )
            BotonSecundario(
                texto = if (estado.descartandoViaje) {
                    stringResource(R.string.descarte_descartando)
                } else {
                    stringResource(R.string.cierre_descartar_viaje)
                },
                onClick = { mostrarDescarte = true },
                habilitado = !estado.cerrandoViaje && !estado.descartandoViaje,
            )
        }
    }

    val archivosAlCerrar = pluralStringResource(
        R.plurals.cantidad_archivos,
        estado.medios.size,
        estado.medios.size,
    )
    DialogoConfirmacion(
        visible = mostrarConfirmacionCierre,
        titulo = stringResource(R.string.cierre_confirmar_titulo),
        texto = stringResource(R.string.cierre_confirmar_texto, archivosAlCerrar),
        textoConfirmar = stringResource(R.string.cierre_confirmar),
        textoConfirmarProcesando = if (estado.cerrandoViaje) {
            stringResource(R.string.cierre_cerrando)
        } else {
            null
        },
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

    // U11: el custodio tocó atrás con novedad o comentario sin terminar: confirmación de salida
    // que descarta los cambios escritos (AUDIT.md U11, D-E3).
    DialogoConfirmacion(
        visible = mostrarConfirmacionDescarteSalida,
        titulo = stringResource(R.string.cierre_salir_titulo),
        texto = stringResource(R.string.cierre_salir_texto),
        textoConfirmar = stringResource(R.string.cierre_salir_confirmar),
        onConfirmar = {
            mostrarConfirmacionDescarteSalida = false
            onAtras()
        },
        onCancelar = { mostrarConfirmacionDescarteSalida = false },
    )

    estado.error?.let { error ->
        DialogoError(mensaje = stringResource(error), onCerrar = onConsumirError)
    }
}

private fun formatearCierre(segundos: Long): String {
    val min = segundos / 60
    val seg = segundos % 60
    return "%d:%02d".format(min, seg)
}

@Preview(name = "Claro")
@Composable
private fun CierreVistaClaro() {
    Vitrina(oscuro = false) {
        CierreScreen(
            estado = CapturaUiState(
                custodio = DatosDemo.custodio,
                reporte = DatosDemo.reporteActivo,
                etiquetas = DatosDemo.etiquetas,
                medios = DatosDemo.mediosViaje,
                pendientesSinSubir = 1,
            ),
            onAtras = {},
            onCerrarViaje = { _, _ -> },
            onDescartarViaje = {},
            onConsumirError = {},
        )
    }
}

@Preview(name = "Oscuro")
@Composable
private fun CierreVistaOscuro() {
    Vitrina(oscuro = true) {
        CierreScreen(
            estado = CapturaUiState(
                custodio = DatosDemo.custodio,
                reporte = DatosDemo.reporteActivo,
                etiquetas = DatosDemo.etiquetas,
                medios = DatosDemo.mediosViaje,
                pendientesSinSubir = 1,
            ),
            onAtras = {},
            onCerrarViaje = { _, _ -> },
            onDescartarViaje = {},
            onConsumirError = {},
        )
    }
}