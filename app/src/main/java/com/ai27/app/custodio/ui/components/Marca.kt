package com.ai27.app.custodio.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai27.app.custodio.R
import com.ai27.app.custodio.ui.theme.LocalTemaOscuro

/**
 * Marca de Echo dentro de la app: el glifo y las ondas del logo **sin** el fondo del
 * badge, sobre el lienzo del tema.
 *
 * Los dos PNG (`echo_logo` y `echo_logo_claro`) los genera `brand/generar_icono.py` a
 * partir de `brand/echo-logo.png`; no se editan a mano. Hace falta la variante clara
 * porque el glifo del maestro es blanco y sobre el lienzo claro (`#EEF1F7`) seria
 * invisible: la variante clara lo remapea al token de texto del tema claro (`#1A2130`)
 * y deja las ondas azules igual, que si se leen sobre los dos lienzos. La eleccion
 * sale de [LocalTemaOscuro], la misma preferencia que gobierna `EchoTheme`.
 *
 * El `contentDescription` es `null` a proposito: el nombre "Echo" se escribe justo
 * debajo como texto, y describir la imagen haria que el lector de pantalla lo dijera
 * dos veces.
 *
 * @param tamano lado del cuadro. El PNG es cuadrado con la marca inscrita, asi que
 *   `ContentScale.Fit` (el valor por defecto de `Image`) la dibuja 1:1 sin deformarla.
 */
@Composable
fun MarcaEcho(
    modifier: Modifier = Modifier,
    tamano: Dp = 84.dp,
) {
    val oscuro = LocalTemaOscuro.current
    Image(
        painter = painterResource(
            if (oscuro) R.drawable.echo_logo else R.drawable.echo_logo_claro,
        ),
        contentDescription = null,
        modifier = modifier.size(tamano),
    )
}

@Preview(name = "Oscuro", showBackground = true)
@Composable
private fun MarcaEchoVistaOscuro() {
    Vitrina(oscuro = true) {
        MarcaEchoMuestra()
    }
}

@Preview(name = "Claro", showBackground = true)
@Composable
private fun MarcaEchoVistaClaro() {
    Vitrina(oscuro = false) {
        MarcaEchoMuestra()
    }
}

@Composable
private fun MarcaEchoMuestra() {
    MarcaEcho(modifier = Modifier.padding(24.dp))
}
