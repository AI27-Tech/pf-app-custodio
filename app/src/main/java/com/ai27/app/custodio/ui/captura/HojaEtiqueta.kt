package com.ai27.app.custodio.ui.captura

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ai27.app.custodio.R
import com.ai27.app.custodio.domain.model.CategoriaEtiqueta
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.TipoMedio
import com.ai27.app.custodio.ui.components.AVISO_SUBIDA_DURACION_MS
import com.ai27.app.custodio.ui.components.AvisoSubida
import com.ai27.app.custodio.ui.components.Vitrina
import com.ai27.app.custodio.ui.previews.DatosDemo
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Predicado puro del cierre de la hoja (sub-bug de U1): la hoja se puede cerrar cuando hay
 * una Situación elegida, o cuando el catálogo no trae NINGUNA Situación —ahí no hay forma de
 * clasificar y el pin de guardar es inútil, así que se deja salir en vez de atascar al
 * custodio (la rejilla de Archivos lo reaparece después vía [retomarEtiquetado]).
 */
fun hojaPuedeCerrarse(idsSituacion: Set<String>, tieneSituacion: Boolean): Boolean =
    idsSituacion.isEmpty() || tieneSituacion

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HojaEtiqueta(
    estado: CapturaUiState,
    onGuardar: (medioId: String, etiquetas: List<String>) -> Unit,
    onUbicacionTextoCambio: (String) -> Unit,
    onCerrar: () -> Unit,
) {
    val medio = estado.medioPendienteDeEtiquetar ?: return
    // D-B1 (U2): arranca con la última Situación confirmada del viaje en vez de vacía; al
    // custodio suele bastarle confirmar de un toque. Al capturar el siguiente medio la hoja
    // la vuelve a preseleccionar (remember por medio.id).
    //
    // U11: un `Set<String>` crudo no es Bundle-saveable, así que la persistencia vive en una
    // Lista dentro de rememberSaveable (por medio.id, que reinicia la selección al cambiar
    // de medio) y `seleccionadas` se deriva como Set para que las operaciones abajo
    // (diferencia/unión/contiene) mantengan semántica de conjunto idéntica (AUDIT.md U11).
    var seleccionadasLista by rememberSaveable(medio.id) {
        mutableStateOf(estado.ultimaSituacion.toList())
    }
    val seleccionadas = seleccionadasLista.toSet()
    // La hoja se abre siempre totalmente expandida: con el aviso, las etiquetas y el
    // campo de ubicación, la versión parcialmente expandida ocultaría el botón de guardar
    // y obligaría a hacer scroll.
    //
    // Hay que elegir al menos una etiqueta de Situación (decisión A del PLAN §14); la
    // Evidencia es opcional. Sin Situación la hoja no se puede descartar: el estado de la
    // hoja rechaza el cambio a Hidden (frena el gesto de baja) y onDismissRequest ignora
    // el toque fuera de la hoja y el botón atrás; el botón de cerrar además se deshabilita.
    // Así la foto y la nota de voz recién capturadas siempre quedan clasificadas y pasan
    // a la cola de subida.
    val idsSituacion = remember(estado.etiquetas) {
        estado.etiquetas
            .filter { it.activo && it.categoria == CategoriaEtiqueta.SITUACION }
            .map { it.id }
            .toSet()
    }
    val tieneSituacion = seleccionadas.any { it in idsSituacion }
    // Sub-bug de U1: con idsSituacion vacío `tieneSituacion` no puede volverse nunca true, y
    // el pin de salida (gesto de baja, toque fuera, botón atrás y la X) quedaría bloqueado
    // para siempre. `hojaPuedeCerrarse` abre la puerta en ese caso (medio queda sin clasificar;
    // la rejilla de Archivos lo señala después vía retomarEtiquetado).
    val puedeCerrar = hojaPuedeCerrarse(idsSituacion, tieneSituacion)
    // rememberModalBottomSheetState enruta confirmValueChange por rememberSaveable: si la
    // lambda se re-creara en cada recomposición (su referencia cambia aunque el resultado
    // sea el mismo), el SheetState nacería de nuevo con valor inicial Hidden y ModalBottomSheet
    // volvería a lanzar show() — la hoja "aparecía dos veces" al elegir el primer chip de
    // Situación. Se recuerda una lambda estable que lee el dato vivo vía rememberUpdatedState.
    val permiteCerrar = rememberUpdatedState(puedeCerrar)
    val confirmarCierre = remember {
        { valor: SheetValue -> if (valor == SheetValue.Hidden) permiteCerrar.value else true }
    }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = confirmarCierre,
    )
    // U11: soporta recomposición y rotación sin perder la selección ni la hoja abierta (AUDIT U11).
    var avisoVisible by rememberSaveable { mutableStateOf(true) }
    val etiquetasActivas = estado.etiquetas.filter { it.activo }.sortedBy { it.orden }

    // La hoja se abre justo cuando un archivo queda capturado (foto tomada o nota de voz
    // terminada): el aviso confirma que quedó en la cola y se subirá en breve.
    LaunchedEffect(Unit) {
        delay(AVISO_SUBIDA_DURACION_MS.milliseconds)
        avisoVisible = false
    }

    ModalBottomSheet(
        onDismissRequest = { if (puedeCerrar) onCerrar() },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AnimatedVisibility(
                visible = avisoVisible,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                AvisoSubida(
                    texto = stringResource(R.string.aviso_subida_en_breve),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    text = when (medio.tipo) {
                        TipoMedio.FOTO -> stringResource(R.string.hoja_foto_capturada)
                        TipoMedio.AUDIO -> stringResource(R.string.nota_de_voz)
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
                IconButton(
                    onClick = onCerrar,
                    enabled = puedeCerrar,
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.hoja_descartar),
                    )
                }
            }

            CategoriaEtiqueta.entries.forEach { categoria ->
                Text(
                    text = stringResource(
                        when (categoria) {
                            CategoriaEtiqueta.SITUACION -> R.string.hoja_etiqueta_obligatoria
                            CategoriaEtiqueta.EVIDENCIA -> R.string.hoja_evidencia_opcional
                        },
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    etiquetasActivas
                        .filter { it.categoria == categoria }
                        .forEach { etiqueta ->
                            FilterChip(
                                selected = etiqueta.id in seleccionadas,
                                onClick = {
                                    seleccionadasLista = if (etiqueta.id in seleccionadas) {
                                        (seleccionadas - etiqueta.id).toList()
                                    } else {
                                        (seleccionadas + etiqueta.id).toList()
                                    }
                                },
                                label = { Text(etiqueta.nombre) },
                            )
                        }
                }
            }

            if (medio.gps == null) {
                OutlinedTextField(
                    value = estado.ubicacionTextoEscrita,
                    onValueChange = onUbicacionTextoCambio,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.hoja_ubicacion_sin_gps)) },
                    singleLine = true,
                )
            }

            Button(
                onClick = { onGuardar(medio.id, seleccionadas.toList()) },
                enabled = tieneSituacion,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.hoja_guardar))
            }
        }
    }
}

@Preview(name = "Claro")
@Composable
private fun HojaEtiquetaVistaClaro() {
    Vitrina(oscuro = false) {
        HojaEtiqueta(
            estado = CapturaUiState(
                custodio = DatosDemo.custodio,
                reporte = DatosDemo.reporteActivo,
                medioPendienteDeEtiquetar = DatosDemo.mediosViaje.first(),
                etiquetas = DatosDemo.etiquetas,
            ),
            onGuardar = { _, _ -> },
            onUbicacionTextoCambio = {},
            onCerrar = {},
        )
    }
}

@Preview(name = "Oscuro")
@Composable
private fun HojaEtiquetaVistaOscuro() {
    Vitrina(oscuro = true) {
        HojaEtiqueta(
            estado = CapturaUiState(
                custodio = DatosDemo.custodio,
                reporte = DatosDemo.reporteActivo,
                medioPendienteDeEtiquetar = DatosDemo.mediosViaje.first(),
                etiquetas = DatosDemo.etiquetas,
            ),
            onGuardar = { _, _ -> },
            onUbicacionTextoCambio = {},
            onCerrar = {},
        )
    }
}