package com.ai27.app.custodio.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ai27.app.custodio.R

/**
 * Campo de placa con sugerencias de la última unidad en ruta.
 *
 * Todo el texto de entrada se fuerza a mayúsculas — las placas mexicanas se escriben
 * siempre en mayúsculas — sin permutar el valor hacia el llamador: [onValorCambia] se
 * invoca ya normalizado. El estilo del texto hereda las mayúsculas de Nocturne, sin
 * cursivas ni monospace.
 */
@Composable
fun CampoPlaca(
    valor: String,
    onValorCambia: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSugerencia: (String) -> Unit = {},
    suggestions: List<String> = emptyList(),
    siguiente: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.campo_placa_rotulo),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(
                1.dp,
                if (valor.isNotEmpty()) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
            ),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (valor.isEmpty()) {
                        Text(
                            text = stringResource(R.string.campo_placa_placeholder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                    BasicTextField(
                        value = valor,
                        onValueChange = { onValorCambia(soloPlacas(it)) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { siguiente?.invoke() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (valor.isNotEmpty()) {
                    IconButton(
                        onClick = { onValorCambia("") },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.campo_placa_limpiar),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.campo_placa_titulo),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                suggestions.forEach { placa ->
                    SugerenciaPlaca(
                        placa = placa,
                        onClick = { onSugerencia(placa) },
                    )
                }
            }
        }
    }
}

private fun soloPlacas(entrada: String): String {
    val normalizada = entrada.uppercase()
    val permitidos = normalizada.map { it }
        .filter { it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == ' ' }
        .joinToString("")
    return permitidos
}

@Composable
private fun SugerenciaPlaca(
    placa: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_placa),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = placa,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Preview(name = "Oscuro", showBackground = true)
@Composable
private fun CampoPlacaVistaOscuro() {
    Vitrina(oscuro = true) {
        CampoPlacaMuestra()
    }
}

@Preview(name = "Claro", showBackground = true)
@Composable
private fun CampoPlacaVistaClaro() {
    Vitrina(oscuro = false) {
        CampoPlacaMuestra()
    }
}

@Composable
private fun CampoPlacaMuestra() {
    var valor by remember { mutableStateOf("") }
    CampoPlaca(
        valor = valor,
        onValorCambia = { valor = it },
        suggestions = if (valor.isEmpty()) listOf("ABEJO", "CARGA", "INCENDIO") else emptyList(),
        onSugerencia = { valor = it },
        modifier = Modifier.padding(24.dp),
    )
}