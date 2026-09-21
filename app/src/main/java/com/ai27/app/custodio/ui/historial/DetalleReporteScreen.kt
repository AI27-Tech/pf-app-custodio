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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ai27.app.custodio.R
import com.ai27.app.custodio.domain.model.Etiqueta
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.Reporte
import com.ai27.app.custodio.domain.model.TipoMedio
import com.ai27.app.custodio.ui.components.BarraSuperior
import com.ai27.app.custodio.ui.components.CajaCargaReportes
import com.ai27.app.custodio.ui.components.CajaErrorHistorial
import com.ai27.app.custodio.ui.components.ChipEstadoSubida
import com.ai27.app.custodio.ui.components.RenglonResumen
import com.ai27.app.custodio.ui.components.Vitrina
import com.ai27.app.custodio.ui.previews.DatosDemo

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
    onReintentar: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize()) {
        BarraSuperior(titulo = stringResource(R.string.detalle_titulo), onAtras = onAtras)

        if (estado.cargando) {
            CajaCargaReportes(titulo = stringResource(R.string.caja_cargando_detalle))
            return
        }

        val error = estado.error
        if (error != null) {
            CajaErrorHistorial(
                mensaje = stringResource(error),
                onReintentar = onReintentar,
            )
            return
        }

        val reporte = estado.reporte
        if (reporte == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.detalle_no_encontrado),
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
                        text = stringResource(R.string.archivos_con_conteo, estado.medios.size),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (estado.medios.isEmpty()) {
                    item(key = "sin-medios") {
                        Text(
                            text = stringResource(R.string.detalle_sin_archivos),
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
                text = stringResource(R.string.detalle_viaje_de_fecha, reporte.fechaLocal),
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
            RenglonResumen(
                etiqueta = stringResource(R.string.detalle_archivos_enviados),
                valor = "${reporte.mediosSubidos} de ${reporte.mediosEsperados ?: 0}",
            )
            reporte.comentario?.let { comentario ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.detalle_comentario, comentario),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            reporte.motivoDescarte?.let { motivo ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.detalle_motivo_descarte, motivo),
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
                    contentDescription = stringResource(R.string.comun_miniatura),
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
                    text = nombreEtiqueta ?: medio.etiqueta ?: stringResource(R.string.detalle_sin_etiqueta),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = if (medio.tipo == TipoMedio.FOTO) {
                        stringResource(R.string.foto)
                    } else {
                        stringResource(R.string.nota_de_voz)
                    },
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

@Preview(name = "Claro")
@Composable
private fun DetalleReporteVistaClaro() {
    Vitrina(oscuro = false) {
        DetalleReporteScreen(
            estado = DetalleReporteUiState(
                reporte = DatosDemo.reporteCerrado,
                medios = DatosDemo.mediosViaje,
                etiquetas = DatosDemo.etiquetas,
            ),
            onAtras = {},
        )
    }
}

@Preview(name = "Oscuro")
@Composable
private fun DetalleReporteVistaOscuro() {
    Vitrina(oscuro = true) {
        DetalleReporteScreen(
            estado = DetalleReporteUiState(
                reporte = DatosDemo.reporteCerrado,
                medios = DatosDemo.mediosViaje,
                etiquetas = DatosDemo.etiquetas,
            ),
            onAtras = {},
        )
    }
}