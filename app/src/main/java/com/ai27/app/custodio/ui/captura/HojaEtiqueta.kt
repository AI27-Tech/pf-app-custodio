package com.ai27.app.custodio.ui.captura

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.TipoMedio

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
    val etiquetasActivas = estado.etiquetas.filter { it.activo }.sortedBy { it.orden }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    text = when (medio.tipo) {
                        TipoMedio.FOTO -> "Foto capturada"
                        TipoMedio.AUDIO -> "Nota de voz"
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
                IconButton(onClick = onCerrar) {
                    Icon(Icons.Filled.Close, contentDescription = "Descartar")
                }
            }

            Text(
                text = "Etiqueta obligatoria:",
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
                    label = { Text("Ubicación (sin GPS)") },
                    singleLine = true,
                )
            }

            Button(
                onClick = { seleccionada?.let { onGuardar(medio.id, it) } },
                enabled = seleccionada != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (seleccionada == null) "Selecciona una etiqueta" else "Guardar")
            }
        }
    }
}