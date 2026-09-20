package com.ai27.app.custodio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Hoja inferior del sistema: contenedor que envuelve la `ModalBottomSheet` de M3 con la
 * geometría de Nocturne.
 *
 * La hoja es el nivel 3 de superficie ([MaterialTheme.colorScheme.surfaceContainerHigh])
 * con radio de [MaterialTheme.shapes.extraLarge] (12dp) y mango de [MaterialTheme.shapes.small]
 * (rectangular, 40x4): el gutter nativo de M3 es una píldora, que el sistema prohíbe.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaInferior(
    visible: Boolean,
    onOcultar: () -> Unit,
    modifier: Modifier = Modifier,
    titulo: String? = null,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return
    val estadoHoja = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onOcultar,
        modifier = modifier,
        sheetState = estadoHoja,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.extraLarge,
        dragHandle = {
            Box(
                modifier = Modifier.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small),
                )
            }
        },
    ) {
        if (titulo != null) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
            )
        }
        contenido()
    }
}

@Preview(name = "Oscuro", showBackground = true)
@Composable
private fun HojaInferiorVistaOscuro() {
    Vitrina(oscuro = true) {
        HojaInferiorAbierta()
    }
}

@Preview(name = "Claro", showBackground = true)
@Composable
private fun HojaInferiorVistaClaro() {
    Vitrina(oscuro = false) {
        HojaInferiorAbierta()
    }
}

@Composable
private fun HojaInferiorAbierta() {
    HojaInferior(
        visible = true,
        onOcultar = {},
        titulo = "Etiquetar medio",
        contenido = {
            Text(
                text = "Contenido de la hoja",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(20.dp),
            )
        },
    )
}