package com.ai27.app.custodio.ui.historial

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ai27.app.custodio.domain.model.Ciclo
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Reporte
import com.ai27.app.custodio.ui.theme.AppTheme

@Composable
fun HistorialRoute(
    onAtras: () -> Unit,
    onVerDetalle: (String) -> Unit,
    viewModel: HistorialViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsState()
    HistorialScreen(
        estado = estado,
        onAtras = onAtras,
        onFiltroCambio = viewModel::onFiltroCambio,
        onVerDetalle = onVerDetalle,
    )
}

@Composable
fun HistorialScreen(
    estado: HistorialUiState,
    onAtras: () -> Unit,
    onFiltroCambio: (String) -> Unit,
    onVerDetalle: (String) -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            EncabezadoHistorial(
                pendientesSinSubir = estado.pendientesSinSubir,
                onAtras = onAtras,
            )
            OutlinedTextField(
                value = estado.filtroPlaca,
                onValueChange = onFiltroCambio,
                label = { Text("Buscar por placa") },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null)
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
            when {
                estado.reportesTotales == 0 -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Sin viajes en los últimos 30 días.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }

                estado.reportesVisible.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Ningún viaje coincide con «${estado.filtroPlaca}».",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(estado.reportesVisible, key = { it.id }) { reporte ->
                        TarjetaReporte(
                            reporte = reporte,
                            onClick = { onVerDetalle(reporte.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EncabezadoHistorial(
    pendientesSinSubir: Int,
    onAtras: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onAtras) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
        }
        Text(
            text = "Historial",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        if (pendientesSinSubir > 0) {
            ChipEstado(
                texto = "$pendientesSinSubir sin subir",
                contenedor = MaterialTheme.colorScheme.errorContainer,
                contenido = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.width(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TarjetaReporte(
    reporte: Reporte,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = reporte.placasCapturadas,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Viaje del ${reporte.fechaLocal}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                ChipCiclo(reporte.ciclo)
                Spacer(Modifier.width(6.dp))
                ChipEstadoSubida(reporte.subida)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${reporte.mediosSubidos} de ${reporte.mediosEsperados ?: 0} enviados" +
                    (reporte.motivoDescarte?.let { " · ${it}" }.orEmpty()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun ChipCiclo(ciclo: Ciclo) {
    when (ciclo) {
        Ciclo.CERRADO -> ChipEstado(
            texto = "Cerrado",
            contenedor = MaterialTheme.colorScheme.secondaryContainer,
            contenido = MaterialTheme.colorScheme.onSecondaryContainer,
        )

        Ciclo.DESCARTADO -> ChipEstado(
            texto = "Descartado",
            contenedor = MaterialTheme.colorScheme.errorContainer,
            contenido = MaterialTheme.colorScheme.onErrorContainer,
        )

        Ciclo.ACTIVO -> ChipEstado(
            texto = "Activo",
            contenedor = MaterialTheme.colorScheme.tertiaryContainer,
            contenido = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Composable
internal fun ChipEstadoSubida(estado: EstadoSubida) {
    val colores = AppTheme.coloresEstado
    val (texto, color) = when (estado) {
        EstadoSubida.COMPLETA -> "Enviado ✓" to colores.enviado
        EstadoSubida.EN_PROGRESO -> "Subiendo" to colores.subiendo
        EstadoSubida.PENDIENTE -> "Pendiente" to colores.pendiente
        EstadoSubida.FALLIDA -> "Error" to colores.fallida
    }
    ChipEstado(
        texto = texto,
        contenedor = color.contenedor,
        contenido = color.contenido,
        borde = color.borde,
    )
}

@Composable
internal fun ChipEstado(
    texto: String,
    contenedor: Color,
    contenido: Color,
    borde: Color? = null,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = contenedor,
        border = borde?.let { BorderStroke(width = 1.dp, color = it) },
    ) {
        Text(
            text = texto,
            style = MaterialTheme.typography.labelSmall,
            color = contenido,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}