package com.ai27.app.custodio.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.ai27.app.custodio.R
import com.ai27.app.custodio.ui.theme.AppTheme
import kotlinx.coroutines.delay

/** Duración en pantalla de los avisos de subida ([AvisoSubidaFlotante] y la hoja). */
internal const val AVISO_SUBIDA_DURACION_MS = 3_000L

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
 * Aviso de subida programada: píldora verde que confirma que un archivo recién capturado
 * quedó en la cola y se subirá en breve. Reutiliza la receta "enviado" de
 * [AppTheme.coloresEstado] (relleno al 12%, borde al 25%, tinta a plena intensidad).
 */
@Composable
fun AvisoSubida(
    texto: String,
    modifier: Modifier = Modifier,
) {
    val color = AppTheme.coloresEstado.enviado
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.contenedor,
        border = BorderStroke(1.dp, color.borde),
        shadowElevation = 4.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_cheque),
                contentDescription = null,
                tint = color.contenido,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = texto,
                style = MaterialTheme.typography.bodyMedium,
                color = color.contenido,
            )
        }
    }
}

/**
 * Aviso de subida flotante y transitorio: muestra [AvisoSubida] cada vez que [evento]
 * crece (la pantalla notifica que acaba de encolarse un archivo) y lo oculta solo a los
 * [AVISO_SUBIDA_DURACION_MS]. El último evento visto se guarda con [rememberSaveable],
 * así no se vuelve a mostrar al regresar a la pantalla. La pantalla decide dónde anclarlo.
 */
@Composable
fun AvisoSubidaFlotante(
    evento: Int,
    texto: String,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    var ultimoEvento by rememberSaveable { mutableStateOf(evento) }
    LaunchedEffect(evento) {
        if (evento != ultimoEvento) {
            ultimoEvento = evento
            visible = true
            delay(AVISO_SUBIDA_DURACION_MS)
            visible = false
        }
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + shrinkVertically(),
    ) {
        AvisoSubida(texto = texto)
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
private fun AvisoSubidaVistaOscuro() {
    Vitrina(oscuro = true) {
        AvisoSubidaMuestra()
    }
}

@Preview(name = "Claro", showBackground = true)
@Composable
private fun AvisoSubidaVistaClaro() {
    Vitrina(oscuro = false) {
        AvisoSubidaMuestra()
    }
}

@Composable
private fun AvisoSubidaMuestra() {
    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AvisoSubida(texto = "Tu archivo se subirá en breve")
    }
}

@Preview(name = "Oscuro", showBackground = true)
@Composable
private fun AvisoSubidaFlotanteVistaOscuro() {
    Vitrina(oscuro = true) {
        AvisoSubidaFlotanteMuestra()
    }
}

@Preview(name = "Claro", showBackground = true)
@Composable
private fun AvisoSubidaFlotanteVistaClaro() {
    Vitrina(oscuro = false) {
        AvisoSubidaFlotanteMuestra()
    }
}

@Composable
private fun AvisoSubidaFlotanteMuestra() {
    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        AvisoSubidaFlotante(
            evento = 1,
            texto = "Tu archivo se subirá en breve",
        )
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
