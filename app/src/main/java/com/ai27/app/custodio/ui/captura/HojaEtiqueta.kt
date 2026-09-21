package com.ai27.app.custodio.ui.captura

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ai27.app.custodio.R
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.TipoMedio
import com.ai27.app.custodio.ui.components.AVISO_SUBIDA_DURACION_MS
import com.ai27.app.custodio.ui.components.AvisoSubida
import com.ai27.app.custodio.ui.components.Vitrina
import com.ai27.app.custodio.ui.previews.DatosDemo
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HojaEtiqueta(
    estado: CapturaUiState,
    onGuardar: (medioId: String, etiquetaId: String) -> Unit,
    onUbicacionTextoCambio: (String) -> Unit,
    onCerrar: () -> Unit,
) {
    val medio = estado.medioPendienteDeEtiquetar ?: return
    val sheetState = rememberModalBottomSheetState()
    var seleccionada by remember(medio.id) { mutableStateOf<String?>(null) }
    var avisoVisible by remember { mutableStateOf(true) }
    val etiquetasActivas = estado.etiquetas.filter { it.activo }.sortedBy { it.orden }

    // La hoja se abre justo cuando un archivo queda capturado (foto tomada o nota de voz
    // terminada): el aviso confirma que quedó en la cola y se subirá en breve.
    LaunchedEffect(Unit) {
        delay(AVISO_SUBIDA_DURACION_MS)
        avisoVisible = false
    }

    ModalBottomSheet(
        onDismissRequest = onCerrar,
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
                IconButton(onClick = onCerrar) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.hoja_descartar),
                    )
                }
            }

            Text(
                text = stringResource(R.string.hoja_etiqueta_obligatoria),
                style = MaterialTheme.typography.labelLarge,
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                etiquetasActivas.forEach { etiqueta ->
                    FilterChip(
                        selected = seleccionada == etiqueta.id,
                        onClick = { seleccionada = etiqueta.id },
                        label = { Text(etiqueta.nombre) },
                    )
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
                onClick = { seleccionada?.let { onGuardar(medio.id, it) } },
                enabled = seleccionada != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (seleccionada == null) {
                        stringResource(R.string.hoja_selecciona_etiqueta)
                    } else {
                        stringResource(R.string.hoja_guardar)
                    },
                )
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