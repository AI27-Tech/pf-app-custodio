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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ai27.app.custodio.R
import com.ai27.app.custodio.domain.model.Ciclo
import com.ai27.app.custodio.domain.model.Reporte
import com.ai27.app.custodio.ui.components.BarraSuperior
import com.ai27.app.custodio.ui.components.CajaCargaReportes
import com.ai27.app.custodio.ui.components.CajaErrorHistorial
import com.ai27.app.custodio.ui.components.RenglonReporte
import com.ai27.app.custodio.ui.components.Vitrina
import com.ai27.app.custodio.ui.previews.DatosDemo

@Composable
fun HistorialRoute(
    onAtras: () -> Unit,
    onVerDetalle: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistorialViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsState()
    HistorialScreen(
        estado = estado,
        onAtras = onAtras,
        onFiltroCambio = viewModel::onFiltroCambio,
        onVerDetalle = onVerDetalle,
        modifier = modifier,
    )
}

/**
 * Pestaña de historial: últimos 30 días en `CERRADO`/`DESCARTADO`, solo lectura, con
 * buscador por placa. Renglones reutilizando [RenglonReporte] con el ciclo como distintivo;
 * el conteo de pendientes vive en el badge de la barra inferior.
 */
@Composable
fun HistorialScreen(
    estado: HistorialUiState,
    onAtras: () -> Unit,
    onFiltroCambio: (String) -> Unit,
    onVerDetalle: (String) -> Unit,
    onReintentar: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        BarraSuperior(titulo = stringResource(R.string.destino_historial), onAtras = onAtras)

        OutlinedTextField(
            value = estado.filtroPlaca,
            onValueChange = onFiltroCambio,
            label = { Text(stringResource(R.string.historial_buscar_placa)) },
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
                    text = stringResource(R.string.historial_vacio),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            estado.reportesVisible.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.historial_sin_coincidencias, estado.filtroPlaca),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            estado.cargando -> CajaCargaReportes()

            estado.error != null -> CajaErrorHistorial(
                mensaje = stringResource(estado.error),
                onReintentar = onReintentar,
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(estado.reportesVisible, key = { it.id }) { reporte ->
                    RenglonReporte(
                        placas = reporte.placasCapturadas,
                        detalle = detalleDe(reporte),
                        estado = reporte.subida,
                        onClick = { onVerDetalle(reporte.id) },
                        distintivo = { ChipCiclo(reporte.ciclo) },
                    )
                }
            }
        }
    }
}

@Composable
private fun detalleDe(reporte: Reporte): String =
    stringResource(R.string.historial_detalle_viaje, reporte.fechaLocal, reporte.mediosSubidos, reporte.mediosEsperados ?: 0) +
        (reporte.motivoDescarte?.let { stringResource(R.string.historial_detalle_motivo, it) }.orEmpty())

@Composable
internal fun ChipCiclo(ciclo: Ciclo) {
    when (ciclo) {
        Ciclo.CERRADO -> ChipEstado(
            texto = stringResource(R.string.historial_estado_cerrado),
            contenedor = MaterialTheme.colorScheme.secondaryContainer,
            contenido = MaterialTheme.colorScheme.onSecondaryContainer,
            icono = R.drawable.ic_cheque,
        )

        Ciclo.DESCARTADO -> ChipEstado(
            texto = stringResource(R.string.historial_estado_descartado),
            contenedor = MaterialTheme.colorScheme.errorContainer,
            contenido = MaterialTheme.colorScheme.onErrorContainer,
            icono = R.drawable.ic_alerta,
        )

        Ciclo.ACTIVO -> ChipEstado(
            texto = stringResource(R.string.historial_estado_activo),
            contenedor = MaterialTheme.colorScheme.tertiaryContainer,
            contenido = MaterialTheme.colorScheme.onTertiaryContainer,
            icono = R.drawable.ic_reloj,
        )
    }
}

@Composable
internal fun ChipEstado(
    texto: String,
    contenedor: Color,
    contenido: Color,
    borde: Color? = null,
    icono: Int? = null,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = contenedor,
        border = borde?.let { BorderStroke(width = 1.dp, color = it) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icono != null) {
                Icon(
                    painter = painterResource(icono),
                    contentDescription = null,
                    tint = contenido,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = texto,
                style = MaterialTheme.typography.labelSmall,
                color = contenido,
            )
        }
    }
}

@Preview(name = "Claro")
@Composable
private fun HistorialVistaClaro() {
    Vitrina(oscuro = false) {
        HistorialScreen(
            estado = HistorialUiState(
                reportesVisible = listOf(DatosDemo.reporteCerrado, DatosDemo.reporteDescartado),
                reportesTotales = 2,
            ),
            onAtras = {},
            onFiltroCambio = {},
            onVerDetalle = {},
        )
    }
}

@Preview(name = "Oscuro")
@Composable
private fun HistorialVistaOscuro() {
    Vitrina(oscuro = true) {
        HistorialScreen(
            estado = HistorialUiState(
                reportesVisible = listOf(DatosDemo.reporteCerrado, DatosDemo.reporteDescartado),
                reportesTotales = 2,
            ),
            onAtras = {},
            onFiltroCambio = {},
            onVerDetalle = {},
        )
    }
}