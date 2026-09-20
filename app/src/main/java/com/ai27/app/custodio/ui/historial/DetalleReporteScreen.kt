package com.ai27.app.custodio.ui.historial

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ai27.app.custodio.R
import com.ai27.app.custodio.domain.model.Etiqueta
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.Reporte
import com.ai27.app.custodio.domain.model.TipoMedio
import com.ai27.app.custodio.ui.components.ChipEstadoSubida

@Composable
fun DetalleReporteRoute(
    onAtras: () -> Unit,
    viewModel: DetalleReporteViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsState()
    DetalleReporteScreen(
        estado = estado,
        onAtras = onAtras,
    )
}

@Composable
fun DetalleReporteScreen(
    estado: DetalleReporteUiState,
    onAtras: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
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
                    text = "Detalle del viaje",
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            val reporte = estado.reporte
            if (reporte == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No se encontró el viaje.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "cabeza") { CabezaDetalle(reporte) }
                    item(key = "titulo-medios") {
                        Text(
                            text = "Archivos (${estado.medios.size})",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    if (estado.medios.isEmpty()) {
                        item(key = "sin-medios") {
                            Text(
                                text = "Sin archivos en este viaje.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    } else {
                        items(estado.medios, key = { it.id }) { medio ->
                            FilaMedio(
                                medio = medio,
                                nombreEtiqueta = nombreEtiqueta(estado.etiquetas, medio.etiqueta),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CabezaDetalle(reporte: Reporte) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = reporte.placasCapturadas,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Viaje del ${reporte.fechaLocal}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChipCiclo(reporte.ciclo)
                Spacer(Modifier.width(6.dp))
                ChipEstadoSubida(reporte.subida)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${reporte.mediosSubidos} de ${reporte.mediosEsperados ?: 0} enviados",
                style = MaterialTheme.typography.bodyMedium,
            )
            reporte.comentario?.let { comentario ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Comentario: $comentario",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            reporte.motivoDescarte?.let { motivo ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Motivo del descarte: $motivo",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun FilaMedio(
    medio: Medio,
    nombreEtiqueta: String?,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (medio.tipo == TipoMedio.FOTO && medio.thumbnailPath != null) {
                AsyncImage(
                    model = medio.thumbnailPath,
                    contentDescription = "Miniatura",
                    modifier = Modifier.size(56.dp),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier.size(56.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mic),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = nombreEtiqueta ?: medio.etiqueta ?: "Sin etiqueta",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = if (medio.tipo == TipoMedio.FOTO) "Foto" else "Nota de voz",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            ChipEstadoSubida(medio.estadoSubida)
        }
    }
}

private fun nombreEtiqueta(etiquetas: List<Etiqueta>, id: String?): String? =
    id?.let { idBuscado -> etiquetas.firstOrNull { it.id == idBuscado }?.nombre }