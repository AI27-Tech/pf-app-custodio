package com.ai27.app.custodio.ui.ajustes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ai27.app.custodio.ui.components.BarraSuperior
import com.ai27.app.custodio.ui.components.EstadoVacio

/**
 * Pestaña de Ajustes. En C3 el destino ya existe en la barra inferior; el contenido real
 * (sesión, tema, subida automática) se construye en C4.
 */
@Composable
fun AjustesScreen(
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        BarraSuperior(titulo = "Ajustes")
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            EstadoVacio(
                titulo = "Próximamente",
                descripcion = "La configuración de la sesión y\ndel tema llega en la siguiente fase.",
                modifier = Modifier.padding(32.dp),
            )
        }
    }
}