package com.ai27.app.custodio.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ai27.app.custodio.ui.theme.AppCustodioTheme

/**
 * Lienzo de las vistas previas de la biblioteca de componentes.
 *
 * Cada componente de `ui/components` se declara dos veces, una por tema, con
 * `@Preview(name = "Oscuro")` y `@Preview(name = "Claro")`. El fondo de la vista
 * usa el lienzo del tema correspondiente, no el gris neutro del studio, para que
 * la comparación contra los PNG de `mock-app-design-language` sea fiel.
 */
@Composable
internal fun Vitrina(
    oscuro: Boolean,
    contenido: @Composable () -> Unit,
) {
    AppCustodioTheme(oscuro = oscuro) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(Modifier.fillMaxSize()) { contenido() }
        }
    }
}