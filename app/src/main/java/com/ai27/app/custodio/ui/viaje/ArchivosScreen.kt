package com.ai27.app.custodio.ui.viaje

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ai27.app.custodio.R
import com.ai27.app.custodio.domain.model.CategoriaEtiqueta
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.TipoMedio
import com.ai27.app.custodio.ui.captura.CapturaUiState
import com.ai27.app.custodio.ui.captura.HojaEtiqueta
import com.ai27.app.custodio.ui.components.BarraSuperior
import com.ai27.app.custodio.ui.components.ChipEstadoSubida
import com.ai27.app.custodio.ui.components.DialogoConfirmacion
import com.ai27.app.custodio.ui.components.DialogoError
import com.ai27.app.custodio.ui.components.EstadoVacio
import com.ai27.app.custodio.ui.components.FranjaAviso
import com.ai27.app.custodio.ui.components.TicDeEncolado
import com.ai27.app.custodio.ui.components.Vitrina
import com.ai27.app.custodio.ui.components.recordarRetroalimentacion
import com.ai27.app.custodio.ui.previews.DatosDemo
import java.io.File

/**
 * Rejilla de archivos del reporte abierto (`archivos_del_reporte`), en 3 columnas.
 *
 * Las acciones que la pantalla única tenía inline se conservan: borrado sólo pre-subida
 * (PENDIENTE, con confirmación) y reintento manual de la subida fallida.
 *
 * Desde §13 esta pantalla es también **la salida** de la evidencia que quedó sin
 * clasificar (hallazgo E3): la hoja de clasificación vive en memoria, así que si el
 * custodio la cierra con la X o el proceso muere con ella abierta, el medio queda sin
 * situación y sin situación no se sube. Aquí esas celdas se marcan, se pueden tocar para
 * clasificarlas, y una franja arriba dice cuántas faltan.
 */
@Composable
fun ArchivosScreen(
    estado: CapturaUiState,
    onAtras: () -> Unit,
    onBorrarPendiente: (medioId: String) -> Unit,
    onReintentar: (medioId: String) -> Unit,
    onRetomarEtiquetado: (medioId: String) -> Unit,
    onGuardarEtiqueta: (medioId: String, etiquetas: List<String>) -> Unit,
    onUbicacionTextoCambio: (String) -> Unit,
    onCerrarHoja: () -> Unit,
    onConsumirError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var medioABorrar by remember { mutableStateOf<Medio?>(null) }
    // U10: id del medio cuyo reintento está en vuelo. Da señal inmediata (el botón de la
    // celda se vuelve un indicador de progreso) para que un segundo toque no produzca el
    // diálogo "ya está en subida". Se limpia solo cuando el medio deja de estar FALLIDA.
    var reintentandoId by remember { mutableStateOf<String?>(null) }
    // U3: confirmación no visual del encolado (la rejilla también hospeda la HojaEtiqueta,
    // así que guardar una etiqueta retomada también produce el tic).
    val retroalimentacion = recordarRetroalimentacion()
    val sinEtiquetar = estado.mediosSinEtiquetar.size
    val idsSituacion = estado.etiquetas
        .filter { it.activo && it.categoria == CategoriaEtiqueta.SITUACION }
        .map { it.id }
        .toSet()
    val nombresEtiqueta = estado.etiquetas.associate { it.id to it.nombre }

    Column(modifier.fillMaxSize()) {
        BarraSuperior(
            titulo = stringResource(R.string.archivos_con_conteo, estado.medios.size),
            onAtras = onAtras,
        )

        if (sinEtiquetar > 0) {
            FranjaAviso(
                texto = pluralStringResource(
                    R.plurals.archivos_aviso_sin_etiquetar,
                    sinEtiquetar,
                    sinEtiquetar,
                ),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

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
                    LaunchedEffect(medio.id, medio.estadoSubida) {
                        if (reintentandoId == medio.id && medio.estadoSubida != EstadoSubida.FALLIDA) {
                            reintentandoId = null
                        }
                    }
                    CeldaArchivo(
                        medio = medio,
                        idsSituacion = idsSituacion,
                        nombresEtiqueta = nombresEtiqueta,
                        onConfirmarBorrar = { medioABorrar = medio },
                        onReintentar = {
                            reintentandoId = medio.id
                            onReintentar(medio.id)
                        },
                        enReintento = reintentandoId == medio.id,
                        onEtiquetar = { onRetomarEtiquetado(medio.id) },
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
    estado.medioPendienteDeEtiquetar?.let {
        HojaEtiqueta(
            estado = estado,
            onGuardar = onGuardarEtiqueta,
            onUbicacionTextoCambio = onUbicacionTextoCambio,
            onCerrar = onCerrarHoja,
        )
    }
    TicDeEncolado(avisoEncolado = estado.avisoEncolado, retroalimentacion = retroalimentacion)
    estado.error?.let { error ->
        DialogoError(mensaje = stringResource(error), onCerrar = onConsumirError)
    }
}

@Composable
private fun CeldaArchivo(
    medio: Medio,
    idsSituacion: Set<String>,
    nombresEtiqueta: Map<String, String>,
    onConfirmarBorrar: () -> Unit,
    onReintentar: () -> Unit,
    onEtiquetar: () -> Unit,
    enReintento: Boolean = false,
) {
    val faltaSituacion = medio.etiquetas.none { it in idsSituacion }
    val etiquetarAhora = stringResource(R.string.archivos_etiquetar_ahora)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                // Sin etiqueta la celda es el botón: tocarla reabre la hoja de etiquetado.
                // Es la única forma de rescatar evidencia que quedó sin clasificar (E3).
                // Solo se hace clickeable cuando hay algo que hacer: una celda ya
                // etiquetada no debe anunciarse a TalkBack como un botón deshabilitado.
                .then(
                    if (faltaSituacion) {
                        Modifier.clickable(
                            onClickLabel = etiquetarAhora,
                            onClick = onEtiquetar,
                        )
                    } else {
                        Modifier
                    },
                ),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = if (faltaSituacion) {
                BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary)
            } else {
                null
            },
        ) {
            if (medio.tipo == TipoMedio.FOTO) {
                AsyncImage(
                    model = medio.thumbnailPath?.let { File(it) },
                    contentDescription = if (faltaSituacion) {
                        etiquetarAhora
                    } else {
                        stringResource(R.string.comun_miniatura)
                    },
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_mic),
                        contentDescription = if (faltaSituacion) {
                            etiquetarAhora
                        } else {
                            stringResource(R.string.archivos_nota)
                        },
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

        // U5: "Enviado" ya no es ausencia de adorno. Se pinta el chip de estado de la
        // biblioteca para todo lo que dejó de ser PENDIENTE; el PENDIENTE se distingue por
        // el borde/franja de "sin situación" o por el botón de borrar.
        when (medio.estadoSubida) {
            EstadoSubida.COMPLETA, EstadoSubida.EN_PROGRESO, EstadoSubida.FALLIDA ->
                ChipEstadoSubida(
                    estado = medio.estadoSubida,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp),
                )
            EstadoSubida.PENDIENTE -> Unit
        }

        if (medio.estadoSubida == EstadoSubida.FALLIDA) {
            if (enReintento) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
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
        }

        val nombres = medio.etiquetas.mapNotNull { nombresEtiqueta[it] }
        if (nombres.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp),
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
            ) {
                Text(
                    text = nombres.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        } else {
            // El hueco donde van las etiquetas no puede quedar vacío: un hueco vacío se lee
            // como "ya está" y el custodio nunca vuelve. Se dice qué falta (E3).
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp),
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Text(
                    text = stringResource(R.string.archivos_sin_etiqueta),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
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
            onRetomarEtiquetado = {},
            onGuardarEtiqueta = { _, _ -> },
            onUbicacionTextoCambio = {},
            onCerrarHoja = {},
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
            onRetomarEtiquetado = {},
            onGuardarEtiqueta = { _, _ -> },
            onUbicacionTextoCambio = {},
            onCerrarHoja = {},
            onConsumirError = {},
        )
    }
}

@Preview(name = "Sin etiquetar · Claro")
@Composable
private fun ArchivosSinEtiquetarVistaClaro() {
    Vitrina(oscuro = false) {
        ArchivosScreen(
            estado = CapturaUiState(
                custodio = DatosDemo.custodio,
                reporte = DatosDemo.reporteActivo,
                etiquetas = DatosDemo.etiquetas,
                medios = DatosDemo.mediosViaje.mapIndexed { indice, medio ->
                    if (indice < 2) medio.copy(etiquetas = emptyList()) else medio
                },
            ),
            onAtras = {},
            onBorrarPendiente = {},
            onReintentar = {},
            onRetomarEtiquetado = {},
            onGuardarEtiqueta = { _, _ -> },
            onUbicacionTextoCambio = {},
            onCerrarHoja = {},
            onConsumirError = {},
        )
    }
}

@Preview(name = "Sin etiquetar · Oscuro")
@Composable
private fun ArchivosSinEtiquetarVistaOscuro() {
    Vitrina(oscuro = true) {
        ArchivosScreen(
            estado = CapturaUiState(
                custodio = DatosDemo.custodio,
                reporte = DatosDemo.reporteActivo,
                etiquetas = DatosDemo.etiquetas,
                medios = DatosDemo.mediosViaje.mapIndexed { indice, medio ->
                    if (indice < 2) medio.copy(etiquetas = emptyList()) else medio
                },
            ),
            onAtras = {},
            onBorrarPendiente = {},
            onReintentar = {},
            onRetomarEtiquetado = {},
            onGuardarEtiqueta = { _, _ -> },
            onUbicacionTextoCambio = {},
            onCerrarHoja = {},
            onConsumirError = {},
        )
    }
}