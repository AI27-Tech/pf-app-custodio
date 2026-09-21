package com.ai27.app.custodio.ui.viaje

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ai27.app.custodio.R
import com.ai27.app.custodio.ui.captura.CapturaUiState
import com.ai27.app.custodio.ui.components.BotonPendientes
import com.ai27.app.custodio.ui.components.BotonPrimario
import com.ai27.app.custodio.ui.components.BotonSecundario
import com.ai27.app.custodio.ui.components.CampoPlaca
import com.ai27.app.custodio.ui.components.DialogoError
import com.ai27.app.custodio.ui.components.EstadoVacio
import com.ai27.app.custodio.ui.components.FranjaAviso
import com.ai27.app.custodio.ui.components.HojaInferior

/**
 * Pestaña Reporte: la raíz del flujo de captura (`reporte_abierto` / `reporte_sin_reporte_abierto`).
 *
 * Sin viaje abierto muestra el estado vacío con el botón que abre la hoja de nuevo viaje
 * (placa); con viaje abierto, el encabezado de la unidad, el contador y las tres llamadas
 * del sistema: capturar evidencia, nota de voz y archivos. La sesión (correo y salida)
 * vive en Ajustes desde C4.
 */
@Composable
fun ReporteScreen(
    estado: CapturaUiState,
    conectado: Boolean,
    onPlacaCambio: (String) -> Unit,
    onIniciarViaje: () -> Unit,
    onIrACaptura: () -> Unit,
    onIrAAudio: () -> Unit,
    onIrAArchivos: () -> Unit,
    onIrACierre: () -> Unit,
    onIrAPendientes: () -> Unit,
    onConsumirError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        val reporte = estado.reporte
        if (reporte == null) {
            SinReporte(
                estado = estado,
                onPlacaCambio = onPlacaCambio,
                onIniciarViaje = onIniciarViaje,
            )
        } else {
            ReporteAbierto(
                estado = estado,
                conectado = conectado,
                onIrACaptura = onIrACaptura,
                onIrAAudio = onIrAAudio,
                onIrAArchivos = onIrAArchivos,
                onIrACierre = onIrACierre,
                onIrAPendientes = onIrAPendientes,
            )
        }
    }

    estado.error?.let { mensaje ->
        DialogoError(mensaje = mensaje, onCerrar = onConsumirError)
    }
}

@Composable
private fun SinReporte(
    estado: CapturaUiState,
    onPlacaCambio: (String) -> Unit,
    onIniciarViaje: () -> Unit,
) {
    var mostrarHoja by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        EstadoVacio(
            titulo = "Sin reporte activo",
            descripcion = "Inicia un nuevo reporte de viaje para registrar evidencias y trayecto.",
        )
        Spacer(Modifier.weight(1f))
        BotonPrimario(
            texto = "Iniciar nuevo reporte",
            onClick = { mostrarHoja = true },
            habilitado = !estado.creandoViaje,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Protocolo AI27 Custodio",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    HojaInferior(
        visible = mostrarHoja,
        onOcultar = { mostrarHoja = false },
        titulo = "Nuevo viaje",
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CampoPlaca(
                valor = estado.placaEscrita,
                onValorCambia = onPlacaCambio,
            )
            BotonPrimario(
                texto = if (estado.creandoViaje) "Creando viaje…" else "Comenzar viaje",
                onClick = {
                    onIniciarViaje()
                    mostrarHoja = false
                },
                habilitado = !estado.creandoViaje && estado.placaEscrita.isNotBlank(),
            )
        }
    }
}

@Composable
private fun ReporteAbierto(
    estado: CapturaUiState,
    conectado: Boolean,
    onIrACaptura: () -> Unit,
    onIrAAudio: () -> Unit,
    onIrAArchivos: () -> Unit,
    onIrACierre: () -> Unit,
    onIrAPendientes: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!conectado) {
            FranjaAviso(texto = "Sin conexión: las capturas se guardan localmente en el dispositivo.")
        }
        BloqueUnidad(
            placas = estado.reporte?.placasCapturadas.orEmpty(),
            contador = estado.medios.size,
            onIrACierre = onIrACierre,
        )

        if (estado.limiteAlcanzado) {
            FranjaAviso(texto = "Alcanzaste el límite de 50 medios por viaje.")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CeldaDato(
                titulo = "Evidencias",
                valor = "${estado.medios.size}",
                modifier = Modifier.weight(1f),
            )
            CeldaDato(
                titulo = "Pendientes",
                valor = "${estado.pendientesSinSubir}",
                modifier = Modifier.weight(1f),
            )
        }

        BotonPrimario(
            texto = "Capturar evidencia",
            onClick = onIrACaptura,
            habilitado = !estado.limiteAlcanzado,
        )
        BotonSecundario(
            texto = "Nota de voz",
            onClick = onIrAAudio,
            habilitado = !estado.limiteAlcanzado,
        )

        FilaArchivos(
            cantidad = estado.medios.size,
            onClick = onIrAArchivos,
        )

        if (estado.pendientesSinSubir > 0) {
            BotonPendientes(
                pendientes = estado.pendientesSinSubir,
                onClick = onIrAPendientes,
            )
        }
    }
}

@Composable
private fun BloqueUnidad(
    placas: String,
    contador: Int,
    onIrACierre: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "UNIDAD ESCOLTADA",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Text(
                    text = placas,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    maxLines = 1,
                )
            }
            Text(
                text = "$contador de 50",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
            BotonCerrarReporte(onClick = onIrACierre)
        }
        ChipEnCurso()
    }
}

@Composable
private fun ChipEnCurso() {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = "EN CURSO",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun BotonCerrarReporte(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            text = "Cerrar viaje",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun CeldaDato(
    titulo: String,
    valor: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = titulo.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = valor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun FilaArchivos(
    cantidad: Int,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_adjunto),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "$cantidad evidencias capturadas",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(R.drawable.ic_chevron),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}