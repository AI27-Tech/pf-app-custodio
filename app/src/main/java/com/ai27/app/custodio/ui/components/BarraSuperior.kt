package com.ai27.app.custodio.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ai27.app.custodio.R

/**
 * Barra superior de las pantallas secundarias.
 *
 * Sustituye a la `TopAppBar` de Material 3, cuyo manifiesto usa píldoras y estados de
 * superficie que el sistema de diseño no comparte. Es una `Surface` de nivel [MaterialTheme.colorScheme.surface]
 * con trazo inferior, [Column] opcional de flecha de regreso (oculta en la raíz de
 * Reporte) y un espacio de acciones libres al final.
 */
@Composable
fun BarraSuperior(
    titulo: String,
    onAtras: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    acciones: @Composable RowScope.() -> Unit = {},
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onAtras != null) {
                IconButton(onClick = onAtras) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_atras),
                        contentDescription = stringResource(R.string.barra_superior_volver),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(22.dp),
                    )
                }
            }
            Text(
                text = titulo,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            acciones()
        }
    }
}

@Preview(name = "Oscuro", showBackground = true)
@Composable
private fun BarraSuperiorVistaOscuro() {
    Vitrina(oscuro = true) {
        Column {
            BarraSuperior(titulo = "Historial", onAtras = {})
            FakeSuperficie()
        }
    }
}

@Preview(name = "Claro", showBackground = true)
@Composable
private fun BarraSuperiorVistaClaro() {
    Vitrina(oscuro = false) {
        Column {
            BarraSuperior(titulo = "Historial", onAtras = {})
            FakeSuperficie()
        }
    }
}

@Preview(name = "Oscuro — sin regreso", showBackground = true)
@Composable
private fun BarraSuperiorSinRegresoVistaOscuro() {
    Vitrina(oscuro = true) {
        Column {
            BarraSuperior(titulo = "Reporte")
            FakeSuperficie()
        }
    }
}

@Composable
private fun ColumnScope.FakeSuperficie() {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth().weight(1f),
    ) {}
}

@Preview(name = "Oscuro — acciones y botón de regreso", showBackground = true)
@Composable
private fun BarraSuperiorConAccionesVistaOscuro() {
    Vitrina(oscuro = true) {
        Column {
            BarraSuperior(titulo = "Detalle del viaje", onAtras = {}) {
                IconButton(onClick = {}) {
                    Icon(
                        painter = painterResource(R.drawable.ic_ajustes),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            FakeSuperficie()
        }
    }
}