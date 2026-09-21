package com.ai27.app.custodio.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ai27.app.custodio.R

/**
 * Caja de carga para el historial y el detalle: indicador circular centrado con un título
 * opcional. [HistorialScreen] la usa sin llamar al título (usa el valor por defecto) y
 * [DetalleReporteScreen] la llama con un título propio ("Cargando detalle…").
 */
@Composable
fun CajaCargaReportes(
    titulo: String = stringResource(R.string.caja_cargando_historial),
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = titulo,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Caja de error para el historial y el detalle: mensaje junto a un botón de reintento.
 * Se usa desde [HistorialScreen] y [DetalleReporteScreen] cuando la carga falla.
 */
@Composable
fun CajaErrorHistorial(
    mensaje: String,
    onReintentar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = mensaje,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onReintentar) {
            Text(stringResource(R.string.comun_reintentar))
        }
    }
}

private val CajasEstadoOscura: Boolean = true

@Preview(name = "CajaCargaReportes · oscuro", showBackground = true, backgroundColor = 0xFF1C1B1F)
@Composable
private fun CajaCargaReportesVistaOscura() {
    CajaCargaReportes()
}

@Preview(name = "CajaCargaReportes · claro", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun CajaCargaReportesVistaClara() {
    CajaCargaReportes(titulo = "Cargando detalle…")
}

@Preview(name = "CajaErrorHistorial · oscuro", showBackground = true, backgroundColor = 0xFF1C1B1F)
@Composable
private fun CajaErrorHistorialVistaOscura() {
    CajaErrorHistorial(mensaje = "No se pudo cargar.", onReintentar = {})
}

@Preview(name = "CajaErrorHistorial · claro", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun CajaErrorHistorialVistaClara() {
    CajaErrorHistorial(mensaje = "No se pudo cargar.", onReintentar = {})
}
