package com.ai27.app.custodio.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ai27.app.custodio.R

/**
 * Miniatura de medio de la rejilla de captura: un cuadro de proporción 1:1 con borde,
 * degradado inferior y chip de etiqueta pegada al borde.
 *
 * La imagen viene por slot ([imagen]): en las pantallas la provee Coil con el archivo
 * local; aquí el componente no conoce el mecanismo de carga. Sin imagen, se dibuja un
 * marcador de posición con barba.
 */
@Composable
fun MiniaturaMedio(
    etiqueta: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    imagen: (@Composable BoxScope.() -> Unit)? = null,
) {
    val borde = BorderStroke(
        width = 1.dp,
        color = if (onClick != null) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
    )
    val contenido: @Composable () -> Unit = {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (imagen != null) {
                imagen()
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_archivo),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                            1f to MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        ),
                    ),
            )
            Text(
                text = etiqueta,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 6.dp, end = 6.dp, bottom = 6.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f),
                        shape = MaterialTheme.shapes.small,
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .widthIn(max = 140.dp),
            )
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface,
            border = borde,
            modifier = modifier.aspectRatio(1f),
        ) {
            contenido()
        }
    } else {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface,
            border = borde,
            modifier = modifier.aspectRatio(1f),
        ) {
            contenido()
        }
    }
}

@Preview(name = "Oscuro", showBackground = true)
@Composable
private fun MiniaturaMedioVistaOscuro() {
    Vitrina(oscuro = true) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RowMinia(etiqueta = "ABEJO", conBorde = false)
            RowMinia(etiqueta = "CARGA", conBorde = true)
        }
    }
}

@Composable
private fun RowMinia(etiqueta: String, conBorde: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MiniaturaMedio(
            etiqueta = etiqueta,
            onClick = if (conBorde) ({}) else null,
            modifier = Modifier.size(96.dp),
        )
        MiniaturaMedio(
            etiqueta = "AUDIO",
            onClick = null,
            modifier = Modifier.size(96.dp),
        )
    }
}

@Preview(name = "Claro", showBackground = true)
@Composable
private fun MiniaturaMedioVistaClaro() {
    Vitrina(oscuro = false) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RowMinia(etiqueta = "ABEJO", conBorde = false)
            RowMinia(etiqueta = "CARGA", conBorde = true)
        }
    }
}