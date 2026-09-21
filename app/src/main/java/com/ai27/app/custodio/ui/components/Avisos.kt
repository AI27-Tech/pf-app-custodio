package com.ai27.app.custodio.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.ai27.app.custodio.R

/**
 * Franja de aviso: superficie de advertencia con borde y un renglón de texto.
 *
 * La superficie usa la ranura `tertiaryContainer` del tema (área ámbar al 12%) y el
 * borde el color al 30%. Sólo transmite un mensaje; las acciones de compensación viven
 * en el botón secundario que la acompaña en pantalla.
 */
@Composable
fun FranjaAviso(
    texto: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.30f),
        ),
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_alerta),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = texto,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Estado vacío de listas y rejillas: icono de carpeta en su módulo, título y texto de
 * apoyo, centrado.
 */
@Composable
fun EstadoVacio(
    titulo: String,
    descripcion: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.size(88.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_carpeta),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        Spacer(Modifier.size(16.dp))
        Text(
            text = titulo,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(4.dp))
        Text(
            text = descripcion,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(name = "Oscuro", showBackground = true)
@Composable
private fun FranjaAvisoVistaOscuro() {
    Vitrina(oscuro = true) {
        AvisoMuestra()
    }
}

@Preview(name = "Claro", showBackground = true)
@Composable
private fun FranjaAvisoVistaClaro() {
    Vitrina(oscuro = false) {
        AvisoMuestra()
    }
}

@Composable
private fun AvisoMuestra() {
    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FranjaAviso(texto = "Hay 3 archivos sin subir. Revisa tu conexión.")
    }
}

@Preview(name = "Oscuro", showBackground = true)
@Composable
private fun EstadoVacioVistaOscuro() {
    Vitrina(oscuro = true) {
        EstadoVacioMuestra()
    }
}

@Preview(name = "Claro", showBackground = true)
@Composable
private fun EstadoVacioVistaClaro() {
    Vitrina(oscuro = false) {
        EstadoVacioMuestra()
    }
}

@Composable
private fun EstadoVacioMuestra() {
    EstadoVacio(
        titulo = "Sin viajes cerrados",
        descripcion = "Cuando cierres tu primer viaje,\nlo verás aquí.",
        modifier = Modifier.padding(32.dp).size(280.dp),
    )
}
