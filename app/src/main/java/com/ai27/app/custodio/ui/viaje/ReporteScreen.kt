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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ai27.app.custodio.R
import com.ai27.app.custodio.ui.captura.CapturaUiState
import com.ai27.app.custodio.ui.components.BotonPendientes
import com.ai27.app.custodio.ui.components.BotonPrimario
import com.ai27.app.custodio.ui.components.BotonSecundario
import com.ai27.app.custodio.ui.components.CajaCargaReportes
import com.ai27.app.custodio.ui.components.CampoPlaca
import com.ai27.app.custodio.ui.components.DialogoError
import com.ai27.app.custodio.ui.components.EstadoVacio
import com.ai27.app.custodio.ui.components.FranjaAviso
import com.ai27.app.custodio.ui.components.HojaInferior
import com.ai27.app.custodio.ui.components.Vitrina
import com.ai27.app.custodio.ui.previews.DatosDemo

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
    conectado: Boolean?,
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
        when {
            // R2: mientras la raíz resuelve el primer valor real, no asumir "sin viaje".
            estado.cargando && reporte == null ->
                CajaCargaReportes(titulo = stringResource(R.string.reporte_cargando))

            reporte == null -> SinReporte(
                estado = estado,
                onPlacaCambio = onPlacaCambio,
                onIniciarViaje = onIniciarViaje,
            )

            else -> ReporteAbierto(
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

    estado.error?.let { error ->
        DialogoError(mensaje = stringResource(error), onCerrar = onConsumirError)
    }
}

@Composable
private fun SinReporte(
    estado: CapturaUiState,
    onPlacaCambio: (String) -> Unit,
    onIniciarViaje: () -> Unit,
) {
    // U11: la hoja de etiquetado abierta sobrevive a una rotación/recreación de la Activity
    // (AUDIT.md U11, ReporteScreen:96). El custodio puede estar a la mitad del etiquetado y
    // una rotación no debe cerrar la hoja ni perder lo que ya escribió arrastrándolo al texto.
    var mostrarHoja by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        EstadoVacio(
            titulo = stringResource(R.string.reporte_sin_activo),
            descripcion = stringResource(R.string.reporte_sin_activo_descripcion),
        )
        Spacer(Modifier.weight(1f))
        BotonPrimario(
            texto = stringResource(R.string.reporte_iniciar_nuevo),
            onClick = { mostrarHoja = true },
            habilitado = !estado.creandoViaje,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.reporte_protocolo),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    HojaInferior(
        visible = mostrarHoja,
        onOcultar = { mostrarHoja = false },
        titulo = stringResource(R.string.reporte_nuevo_viaje),
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
                texto = if (estado.creandoViaje) {
                    stringResource(R.string.reporte_creando_viaje)
                } else {
                    stringResource(R.string.reporte_comenzar_viaje)
                },
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
    conectado: Boolean?,
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
        if (conectado == false) {
            FranjaAviso(texto = stringResource(R.string.reporte_sin_conexion))
        }
        BloqueUnidad(
            placas = estado.reporte?.placasCapturadas.orEmpty(),
            contador = estado.medios.size,
            pendientesSinSubir = estado.pendientesSinSubir,
            onIrACierre = onIrACierre,
        )

        if (estado.limiteAlcanzado) {
            FranjaAviso(texto = stringResource(R.string.reporte_limite_alcanzado))
        }

        // RETIRO_ETIQUETADO: se retira el aviso de "sin clasificar" del hub. La captura
        // sube de inmediato sin etiquetar, así que ya no hay evidencia pendiente de
        // clasificar que anunciar. Comentado, no borrado.
        // if (estado.mediosSinEtiquetar.isNotEmpty()) {
        //     FranjaAviso(
        //         texto = pluralStringResource(
        //             R.plurals.archivos_aviso_sin_etiquetar,
        //             estado.mediosSinEtiquetar.size,
        //             estado.mediosSinEtiquetar.size,
        //         ),
        //     )
        // }

        BotonPrimario(
            texto = stringResource(R.string.captura_titulo),
            onClick = onIrACaptura,
            habilitado = !estado.limiteAlcanzado,
        )
        BotonSecundario(
            texto = stringResource(R.string.nota_de_voz),
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
    pendientesSinSubir: Int,
    onIrACierre: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.reporte_unidad_escoltada),
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
            // U15: el contador degradado ("x de 50") ya no compite con las celdas; lo que
            // se destaca es cuántas evidencias faltan por enviar (0 → "Todo enviado").
            EstadoDeEnvio(
                contador = contador,
                pendientesSinSubir = pendientesSinSubir,
            )
            BotonCerrarReporte(onClick = onIrACierre)
        }
        ChipEnCurso()
    }
}

@Composable
private fun EstadoDeEnvio(
    contador: Int,
    pendientesSinSubir: Int,
) {
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.padding(end = 8.dp),
    ) {
        Text(
            text = if (pendientesSinSubir > 0) {
                pluralStringResource(
                    R.plurals.reporte_faltan_subir,
                    pendientesSinSubir,
                    pendientesSinSubir,
                )
            } else {
                stringResource(R.string.reporte_todo_enviado)
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = if (pendientesSinSubir > 0) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Text(
            text = stringResource(R.string.conteo_de_50, contador),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChipEnCurso() {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = stringResource(R.string.reporte_en_curso),
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
            text = stringResource(R.string.cerrar_viaje),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
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
                text = pluralStringResource(
                    R.plurals.evidencias_capturadas,
                    cantidad,
                    cantidad,
                ),
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

@Preview(name = "Claro")
@Composable
private fun ReporteAbiertoVistaClaro() {
    Vitrina(oscuro = false) {
        ReporteScreen(
            estado = CapturaUiState(
                custodio = DatosDemo.custodio,
                reporte = DatosDemo.reporteActivo,
                etiquetas = DatosDemo.etiquetas,
                medios = DatosDemo.mediosViaje,
                pendientesSinSubir = 2,
            ),
            conectado = true,
            onPlacaCambio = {},
            onIniciarViaje = {},
            onIrACaptura = {},
            onIrAAudio = {},
            onIrAArchivos = {},
            onIrACierre = {},
            onIrAPendientes = {},
            onConsumirError = {},
        )
    }
}

@Preview(name = "Oscuro")
@Composable
private fun ReporteAbiertoVistaOscuro() {
    Vitrina(oscuro = true) {
        ReporteScreen(
            estado = CapturaUiState(
                custodio = DatosDemo.custodio,
                reporte = DatosDemo.reporteActivo,
                etiquetas = DatosDemo.etiquetas,
                medios = DatosDemo.mediosViaje,
                pendientesSinSubir = 2,
            ),
            conectado = true,
            onPlacaCambio = {},
            onIniciarViaje = {},
            onIrACaptura = {},
            onIrAAudio = {},
            onIrAArchivos = {},
            onIrACierre = {},
            onIrAPendientes = {},
            onConsumirError = {},
        )
    }
}

@Preview(name = "Sin viaje · Claro")
@Composable
private fun ReporteSinViajeVistaClaro() {
    Vitrina(oscuro = false) {
        ReporteScreen(
            estado = CapturaUiState(custodio = DatosDemo.custodio),
            conectado = true,
            onPlacaCambio = {},
            onIniciarViaje = {},
            onIrACaptura = {},
            onIrAAudio = {},
            onIrAArchivos = {},
            onIrACierre = {},
            onIrAPendientes = {},
            onConsumirError = {},
        )
    }
}

@Preview(name = "Sin viaje · Oscuro")
@Composable
private fun ReporteSinViajeVistaOscuro() {
    Vitrina(oscuro = true) {
        ReporteScreen(
            estado = CapturaUiState(custodio = DatosDemo.custodio),
            conectado = true,
            onPlacaCambio = {},
            onIniciarViaje = {},
            onIrACaptura = {},
            onIrAAudio = {},
            onIrAArchivos = {},
            onIrACierre = {},
            onIrAPendientes = {},
            onConsumirError = {},
        )
    }
}

@Preview(name = "Abierto sin conexión · Claro")
@Composable
private fun ReporteSinConexionVistaClaro() {
    Vitrina(oscuro = false) {
        ReporteScreen(
            estado = CapturaUiState(
                custodio = DatosDemo.custodio,
                reporte = DatosDemo.reporteActivo,
                etiquetas = DatosDemo.etiquetas,
                medios = DatosDemo.mediosViaje,
                pendientesSinSubir = 2,
            ),
            conectado = false,
            onPlacaCambio = {},
            onIniciarViaje = {},
            onIrACaptura = {},
            onIrAAudio = {},
            onIrAArchivos = {},
            onIrACierre = {},
            onIrAPendientes = {},
            onConsumirError = {},
        )
    }
}

@Preview(name = "Abierto sin conexión · Oscuro")
@Composable
private fun ReporteSinConexionVistaOscuro() {
    Vitrina(oscuro = true) {
        ReporteScreen(
            estado = CapturaUiState(
                custodio = DatosDemo.custodio,
                reporte = DatosDemo.reporteActivo,
                etiquetas = DatosDemo.etiquetas,
                medios = DatosDemo.mediosViaje,
                pendientesSinSubir = 2,
            ),
            conectado = false,
            onPlacaCambio = {},
            onIniciarViaje = {},
            onIrACaptura = {},
            onIrAAudio = {},
            onIrAArchivos = {},
            onIrACierre = {},
            onIrAPendientes = {},
            onConsumirError = {},
        )
    }
}