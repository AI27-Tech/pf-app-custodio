package com.ai27.app.custodio.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ai27.app.custodio.R

/**
 * Los tres destinos de la barra inferior. La pantalla de Ajustes se construye en
 * C4; el destino ya existe en la navegación desde C3.
 */
enum class DestinoInferior(
    @StringRes val nombre: Int,
) {
    REPORTE(R.string.destino_reporte),
    HISTORIAL(R.string.destino_historial),
    AJUSTES(R.string.destino_ajustes),
}

/**
 * Barra de navegación inferior de tres destinos: Reporte, Historial y Ajustes.
 *
 * El destino activo carga un contenedor de icono con relleno de `primary` al 20%
 * y una testiga superior de `4dp` de alto, como manda el mockup
 * `barra_de_navegaci_n_base_3_items_slate_oscuro`. El resto de destinos se pinta en
 * `onSurfaceVariant`.
 *
 * El badge de pendientes (ámbar, `CircleShape`, reutiliza [BadgePendientes] de
 * `Botones.kt` — la misma fuente que la pantalla de Pendientes) es una de las dos
 * excepciones circulares que el sistema de diseño concede — D3 las reserva para
 * contadores pequeños — y recae sobre el icono de Reporte.
 */
@Composable
fun BarraNavegacion(
    seleccionado: DestinoInferior,
    pendientesSinSubir: Int,
    onSeleccionar: (DestinoInferior) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DestinoInferior.entries.forEach { destino ->
                ItemBarra(
                    destino = destino,
                    seleccionado = destino == seleccionado,
                    onSeleccionar = { onSeleccionar(destino) },
                    pendientes = if (destino == DestinoInferior.REPORTE) pendientesSinSubir else 0,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ItemBarra(
    destino: DestinoInferior,
    seleccionado: Boolean,
    pendientes: Int,
    onSeleccionar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(56.dp)
            .clickable { onSeleccionar() }
            .padding(top = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(64.dp)
                .height(32.dp),
        ) {
            Surface(
                color = if (seleccionado) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                } else {
                    Color.Transparent
                },
                shape = MaterialTheme.shapes.small,
                border = if (seleccionado) {
                    androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                    )
                } else {
                    null
                },
                modifier = Modifier.matchParentSize(),
            ) {}
            if (seleccionado) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth(0.44f)
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
                )
            }
            Icon(
                painter = painterResource(icono(destino)),
                contentDescription = null,
                tint = if (seleccionado) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp),
            )
            if (pendientes > 0) {
                BadgePendientes(
                    conteo = pendientes,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 3.dp, end = 4.dp),
                )
            }
        }
        Text(
            text = stringResource(destino.nombre),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (seleccionado) FontWeight.SemiBold else FontWeight.Normal,
            color = if (seleccionado) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
    }
}

private fun icono(destino: DestinoInferior): Int = when (destino) {
    DestinoInferior.REPORTE -> R.drawable.ic_archivo
    DestinoInferior.HISTORIAL -> R.drawable.ic_reloj
    DestinoInferior.AJUSTES -> R.drawable.ic_ajustes
}

@Preview(name = "Oscuro", showBackground = true)
@Composable
private fun BarraNavegacionVistaOscuro() {
    Vitrina(oscuro = true) {
        BarraNavegacionMuestra()
    }
}

@Preview(name = "Claro", showBackground = true)
@Composable
private fun BarraNavegacionVistaClaro() {
    Vitrina(oscuro = false) {
        BarraNavegacionMuestra()
    }
}

@Composable
private fun BarraNavegacionMuestra() {
    var seleccionado by remember { mutableStateOf(DestinoInferior.REPORTE) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Bottom,
    ) {
        BarraNavegacion(
            seleccionado = seleccionado,
            pendientesSinSubir = 3,
            onSeleccionar = { seleccionado = it },
        )
    }
}