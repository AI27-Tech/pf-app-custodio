package com.ai27.app.custodio.ui.viaje

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ai27.app.custodio.R
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.TipoMedio
import com.ai27.app.custodio.ui.captura.CapturaUiState
import com.ai27.app.custodio.ui.components.BarraSuperior
import com.ai27.app.custodio.ui.components.DialogoConfirmacion
import com.ai27.app.custodio.ui.components.DialogoError
import com.ai27.app.custodio.ui.components.EstadoVacio
import com.ai27.app.custodio.ui.components.Vitrina
import com.ai27.app.custodio.ui.previews.DatosDemo
import java.io.File

/**
 * Rejilla de archivos del reporte abierto (`archivos_del_reporte`), en 3 columnas.
 *
 * Las acciones que la pantalla única tenía inline se conservan: borrado sólo pre-subida
 * (PENDIENTE, con confirmación) y reintento manual de la subida fallida. El etiquetado
 * pendiente ya no puede quedar huérfano: esta pantalla se abre desde el resumen.
 */
@Composable
fun ArchivosScreen(
    estado: CapturaUiState,
    onAtras: () -> Unit,
    onBorrarPendiente: (medioId: String) -> Unit,
    onReintentar: (medioId: String) -> Unit,
    onConsumirError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var medioABorrar by remember { mutableStateOf<Medio?>(null) }

    Column(modifier.fillMaxSize()) {
        BarraSuperior(
            titulo = stringResource(R.string.archivos_con_conteo, estado.medios.size),
            onAtras = onAtras,
        )

        if (estado.medios.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                EstadoVacio(
                    titulo = stringResource(R.string.archivos_sin_evidencias),
                    descripcion = stringResource(R.string.archivos_vacio_descripcion),
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(estado.medios, key = { it.id }) { medio ->
                    CeldaArchivo(
                        medio = medio,
                        onConfirmarBorrar = { medioABorrar = medio },
                        onReintentar = { onReintentar(medio.id) },
                    )
                }
            }
        }
    }

    medioABorrar?.let { medio ->
        DialogoConfirmacion(
            visible = true,
            titulo = stringResource(R.string.archivos_borrar_titulo),
            texto = stringResource(R.string.archivos_borrar_texto),
            textoConfirmar = stringResource(R.string.archivos_borrar),
            onConfirmar = {
                onBorrarPendiente(medio.id)
                medioABorrar = null
            },
            onCancelar = { medioABorrar = null },
        )
    }
    estado.error?.let { error ->
        DialogoError(mensaje = stringResource(error), onCerrar = onConsumirError)
    }
}

@Composable
private fun CeldaArchivo(
    medio: Medio,
    onConfirmarBorrar: () -> Unit,
    onReintentar: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            if (medio.tipo == TipoMedio.FOTO) {
                AsyncImage(
                    model = medio.thumbnailPath?.let { File(it) },
                    contentDescription = stringResource(R.string.comun_miniatura),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_mic),
                        contentDescription = stringResource(R.string.archivos_nota),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }

        if (medio.estadoSubida == EstadoSubida.PENDIENTE) {
            IconButton(
                onClick = onConfirmarBorrar,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.archivos_borrar_captura),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        if (medio.estadoSubida == EstadoSubida.FALLIDA) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp),
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(
                    text = stringResource(R.string.archivos_fallida),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            FilledIconButton(
                onClick = onReintentar,
                modifier = Modifier.align(Alignment.TopEnd),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.archivos_reintentar_subida),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        if (medio.etiqueta != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp),
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
            ) {
                Text(
                    text = medio.etiqueta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Preview(name = "Claro")
@Composable
private fun ArchivosVistaClaro() {
    Vitrina(oscuro = false) {
        ArchivosScreen(
            estado = CapturaUiState(
                custodio = DatosDemo.custodio,
                reporte = DatosDemo.reporteActivo,
                etiquetas = DatosDemo.etiquetas,
                medios = DatosDemo.mediosViaje,
            ),
            onAtras = {},
            onBorrarPendiente = {},
            onReintentar = {},
            onConsumirError = {},
        )
    }
}

@Preview(name = "Oscuro")
@Composable
private fun ArchivosVistaOscuro() {
    Vitrina(oscuro = true) {
        ArchivosScreen(
            estado = CapturaUiState(
                custodio = DatosDemo.custodio,
                reporte = DatosDemo.reporteActivo,
                etiquetas = DatosDemo.etiquetas,
                medios = DatosDemo.mediosViaje,
            ),
            onAtras = {},
            onBorrarPendiente = {},
            onReintentar = {},
            onConsumirError = {},
        )
    }
}