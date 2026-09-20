package com.ai27.app.custodio.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ai27.app.custodio.R
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.ui.theme.AppTheme
import com.ai27.app.custodio.ui.theme.ColorEstado

/**
 * Chip de selección de categoría.
 *
 * Sigue la receta de Nocturno para chips seleccionables: relleno de `primary` al 20% y
 * borde al 35% cuando está activo; superficie hueca y tinta apagada cuando no. Es
 * rectangular ([MaterialTheme.shapes.small]), nunca píldora.
 */
@Composable
fun ChipCategoria(
    texto: String,
    seleccionada: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (seleccionada) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            1.dp,
            if (seleccionada) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        modifier = modifier.height(40.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = texto,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (seleccionada) FontWeight.SemiBold else FontWeight.Normal,
                color = if (seleccionada) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
            )
        }
    }
}

/**
 * Chip de estado de subida, la versión de biblioteca del que `HistorialScreen` tenía
 * interno. Aúna las cuatro recetas de [AppTheme.coloresEstado] con un icono, para que el
 * color nunca sea el único portador del estado (a11y).
 */
@Composable
fun ChipEstadoSubida(
    estado: EstadoSubida,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.coloresEstado
    val (texto, color, icono) = when (estado) {
        EstadoSubida.COMPLETA -> Triple(
            stringResource(R.string.estado_enviado),
            colors.enviado,
            R.drawable.ic_cheque,
        )
        EstadoSubida.EN_PROGRESO -> Triple(
            stringResource(R.string.estado_subiendo),
            colors.subiendo,
            R.drawable.ic_reintento,
        )
        EstadoSubida.PENDIENTE -> Triple(
            stringResource(R.string.estado_pendiente),
            colors.pendiente,
            R.drawable.ic_reloj,
        )
        EstadoSubida.FALLIDA -> Triple(
            stringResource(R.string.estado_fallido),
            colors.fallida,
            R.drawable.ic_alerta,
        )
    }
    ChipEstadoTexto(
        texto = texto.uppercase(),
        color = color,
        icono = icono,
        modifier = modifier,
    )
}

@Composable
private fun ChipEstadoTexto(
    texto: String,
    color: ColorEstado,
    icono: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.contenedor,
        border = BorderStroke(1.dp, color.borde),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(icono),
                contentDescription = null,
                tint = color.contenido,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = texto,
                style = MaterialTheme.typography.labelSmall,
                color = color.contenido,
                maxLines = 1,
            )
        }
    }
}

@Preview(name = "Oscuro", showBackground = true)
@Composable
private fun ChipCategoriaVistaOscuro() {
    Vitrina(oscuro = true) {
        ChipCategoriaMuestra()
    }
}

@Preview(name = "Claro", showBackground = true)
@Composable
private fun ChipCategoriaVistaClaro() {
    Vitrina(oscuro = false) {
        ChipCategoriaMuestra()
    }
}

@Composable
private fun ChipCategoriaMuestra() {
    var seleccionado by remember { mutableStateOf("ABEJO") }
    Row(
        modifier = Modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf("ABEJO", "CARGA", "INCENDIO").forEach { etiqueta ->
            ChipCategoria(
                texto = etiqueta,
                seleccionada = seleccionado == etiqueta,
                onClick = { seleccionado = etiqueta },
            )
        }
    }
}

@Preview(name = "Oscuro", showBackground = true)
@Composable
private fun ChipEstadoSubidaVistaOscuro() {
    Vitrina(oscuro = true) {
        ChipEstadoMuestra()
    }
}

@Preview(name = "Claro", showBackground = true)
@Composable
private fun ChipEstadoSubidaVistaClaro() {
    Vitrina(oscuro = false) {
        ChipEstadoMuestra()
    }
}

@Composable
private fun ChipEstadoMuestra() {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChipEstadoSubida(estado = EstadoSubida.COMPLETA, modifier = Modifier.fillMaxWidth())
        ChipEstadoSubida(estado = EstadoSubida.EN_PROGRESO, modifier = Modifier.fillMaxWidth())
        ChipEstadoSubida(estado = EstadoSubida.PENDIENTE, modifier = Modifier.fillMaxWidth())
        ChipEstadoSubida(estado = EstadoSubida.FALLIDA, modifier = Modifier.fillMaxWidth())
    }
}