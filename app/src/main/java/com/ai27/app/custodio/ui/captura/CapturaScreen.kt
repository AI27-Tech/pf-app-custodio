package com.ai27.app.custodio.ui.captura

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import coil3.compose.AsyncImage
import com.ai27.app.custodio.R
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.TipoMedio
import java.io.File

@Composable
fun CapturaRoute(
    onVerHistorial: () -> Unit,
    viewModel: CapturaViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsState()
    CapturaScreen(
        estado = estado,
        onPlacaCambio = viewModel::onPlacaCambio,
        onIniciarViaje = viewModel::iniciarViaje,
        onTomarFoto = viewModel::capturarFoto,
        onIniciarAudio = viewModel::iniciarGrabacionDeAudio,
        onDetenerAudio = viewModel::detenerGrabacionDeAudio,
        onDescartarAudio = viewModel::descartarAudio,
        onGuardarEtiqueta = { medioId, etiquetaId -> viewModel.guardarEtiqueta(medioId, etiquetaId) },
        onUbicacionTextoCambio = viewModel::onUbicacionTextoCambio,
        onCerrarHoja = viewModel::descartarEtiquetado,
        onBorrarPendiente = viewModel::borrarPendiente,
        onReintentar = viewModel::reintentar,
        onCerrarViaje = viewModel::cerrarViaje,
        onDescartarViaje = viewModel::descartarViaje,
        onCerrarSesion = viewModel::solicitarCerrarSesion,
        onVerHistorial = onVerHistorial,
        onCancelarCerrarSesion = viewModel::cancelarCerrarSesion,
        onConfirmarCerrarSesion = viewModel::confirmarCerrarSesion,
        onConsumirError = viewModel::consumirError,
    )
}

private data class Permisos(
    val camara: Boolean,
    val mic: Boolean,
    val ubicacion: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapturaScreen(
    estado: CapturaUiState,
    onPlacaCambio: (String) -> Unit,
    onIniciarViaje: () -> Unit,
    onTomarFoto: (jpeg: ByteArray, miniatura: ByteArray) -> Unit,
    onIniciarAudio: () -> Unit,
    onDetenerAudio: () -> Unit,
    onDescartarAudio: () -> Unit,
    onGuardarEtiqueta: (medioId: String, etiquetaId: String) -> Unit,
    onUbicacionTextoCambio: (String) -> Unit,
    onCerrarHoja: () -> Unit,
    onBorrarPendiente: (medioId: String) -> Unit,
    onReintentar: (medioId: String) -> Unit,
    onCerrarViaje: () -> Unit,
    onDescartarViaje: (motivo: String) -> Unit,
    onCerrarSesion: () -> Unit,
    onVerHistorial: () -> Unit,
    onCancelarCerrarSesion: () -> Unit,
    onConfirmarCerrarSesion: () -> Unit,
    onConsumirError: () -> Unit,
) {
    val context = LocalContext.current

    fun permisosActuales(): Permisos = Permisos(
        camara = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED,
        mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED,
        ubicacion = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED,
    )

    var permisos by remember { mutableStateOf(permisosActuales()) }

    val lanzadorPermisos = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permisos = permisosActuales() }

    LaunchedEffect(Unit) {
        if (!permisos.camara || !permisos.mic) {
            lanzadorPermisos.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ),
            )
        }
    }

    var controllerCamara by remember { mutableStateOf<LifecycleCameraController?>(null) }
    var medioABorrar by remember { mutableStateOf<Medio?>(null) }
    var confirmarCierre by remember { mutableStateOf(false) }
    var confirmarDescarte by remember { mutableStateOf(false) }
    var motivoDescarte by remember { mutableStateOf("") }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            BarraSesion(
                correo = estado.custodio?.correo.orEmpty(),
                pendientesSinSubir = estado.pendientesSinSubir,
                onVerHistorial = onVerHistorial,
                onCerrarSesion = onCerrarSesion,
            )
            val reporte = estado.reporte
            if (reporte == null) {
                NuevoViaje(
                    estado = estado,
                    onPlacaCambio = onPlacaCambio,
                    onIniciarViaje = onIniciarViaje,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                ColumnaCaptura(
                    estado = estado,
                    controllerCamara = controllerCamara,
                    alTenerController = { controllerCamara = it },
                    permisos = permisos,
                    onSolicitarPermisos = {
                        lanzadorPermisos.launch(
                            arrayOf(
                                Manifest.permission.CAMERA,
                                Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                            ),
                        )
                    },
                    onTomarFoto = onTomarFoto,
                    onIniciarAudio = onIniciarAudio,
                    onDetenerAudio = onDetenerAudio,
                    onDescartarAudio = onDescartarAudio,
                    onConfirmarBorrar = { medioABorrar = it },
                    onReintentar = onReintentar,
                    onCerrarViaje = { confirmarCierre = true },
                    onSolicitarDescarte = { confirmarDescarte = true },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    estado.medioPendienteDeEtiquetar?.let {
        HojaEtiqueta(
            estado = estado,
            onGuardar = onGuardarEtiqueta,
            onUbicacionTextoCambio = onUbicacionTextoCambio,
            onCerrar = onCerrarHoja,
        )
    }

    medioABorrar?.let { medio ->
        AlertDialog(
            onDismissRequest = { medioABorrar = null },
            title = { Text("¿Borrar este archivo?") },
            text = { Text("El archivo aún no se ha subido. No podrás recuperarlo.") },
            confirmButton = {
                TextButton(onClick = {
                    onBorrarPendiente(medio.id)
                    medioABorrar = null
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { medioABorrar = null }) { Text("Cancelar") }
            },
        )
    }

    if (confirmarCierre) {
        AlertDialog(
            onDismissRequest = { confirmarCierre = false },
            title = { Text("¿Cerrar el viaje?") },
            text = { Text("El viaje quedará cerrado con ${estado.medios.size} archivos.") },
            confirmButton = {
                TextButton(onClick = {
                    onCerrarViaje()
                    confirmarCierre = false
                }) { Text("Cerrar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmarCierre = false }) { Text("Cancelar") }
            },
        )
    }

    if (confirmarDescarte) {
        AlertDialog(
            onDismissRequest = {
                confirmarDescarte = false
                motivoDescarte = ""
            },
            title = { Text("¿Descartar el viaje?") },
            text = {
                Column {
                    Text(
                        "Esto marca el viaje como descartado. Lo que ya se subió " +
                            "no se borra; se excluye de los reportes del analista.",
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = motivoDescarte,
                        onValueChange = { motivoDescarte = it },
                        label = { Text("Motivo del descarte") },
                        placeholder = { Text("Obligatorio") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDescartarViaje(motivoDescarte)
                        confirmarDescarte = false
                        motivoDescarte = ""
                    },
                    enabled = motivoDescarte.isNotBlank() && !estado.descartandoViaje,
                ) {
                    Text(if (estado.descartandoViaje) "Descartando…" else "Descartar")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmarDescarte = false
                    motivoDescarte = ""
                }) { Text("Cancelar") }
            },
        )
    }

    if (estado.confirmacionCerrarSesion) {
        AlertDialog(
            onDismissRequest = onCancelarCerrarSesion,
            title = { Text("¿Cerrar sesión?") },
            text = {
                Column {
                    Text("Sesión de ${estado.custodio?.correo.orEmpty()}")
                    if (estado.pendientesSinSubir > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Tienes ${estado.pendientesSinSubir} archivos sin subir. " +
                                "Si cierras sesión, esa evidencia perderá su cola de subida.",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirmarCerrarSesion) {
                    Text(if (estado.pendientesSinSubir > 0) "Salir de todos modos" else "Salir")
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelarCerrarSesion) { Text("Cancelar") }
            },
        )
    }

    estado.error?.let { mensaje ->
        AlertDialog(
            onDismissRequest = onConsumirError,
            title = { Text("Aviso") },
            text = { Text(mensaje) },
            confirmButton = {
                TextButton(onClick = onConsumirError) { Text("Entendido") }
            },
        )
    }
}

@Composable
private fun BarraSesion(
    correo: String,
    pendientesSinSubir: Int,
    onVerHistorial: () -> Unit,
    onCerrarSesion: () -> Unit,
) {
    if (correo.isBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = correo,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (pendientesSinSubir > 0) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.padding(end = 4.dp),
        ) {
            Text(
                text = "$pendientesSinSubir sin subir",
                style = MaterialTheme.typography.labelSmall,
                color = if (pendientesSinSubir > 0) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        IconButton(onClick = onVerHistorial) {
            Icon(
                Icons.AutoMirrored.Filled.List,
                contentDescription = "Historial de viajes",
                tint = MaterialTheme.colorScheme.outline,
            )
        }
        IconButton(onClick = onCerrarSesion) {
            Icon(
                Icons.Filled.ExitToApp,
                contentDescription = "Cerrar sesión",
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun NuevoViaje(
    estado: CapturaUiState,
    onPlacaCambio: (String) -> Unit,
    onIniciarViaje: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text(
            text = "App Custodio",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "Aún no hay un viaje en curso.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = estado.placaEscrita,
            onValueChange = onPlacaCambio,
            label = { Text("Placa") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onIniciarViaje,
            enabled = !estado.creandoViaje && estado.placaEscrita.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text(if (estado.creandoViaje) "Creando viaje…" else "Comenzar viaje")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnaCaptura(
    estado: CapturaUiState,
    controllerCamara: LifecycleCameraController?,
    alTenerController: (LifecycleCameraController) -> Unit,
    permisos: Permisos,
    onSolicitarPermisos: () -> Unit,
    onTomarFoto: (jpeg: ByteArray, miniatura: ByteArray) -> Unit,
    onIniciarAudio: () -> Unit,
    onDetenerAudio: () -> Unit,
    onDescartarAudio: () -> Unit,
    onConfirmarBorrar: (Medio) -> Unit,
    onReintentar: (medioId: String) -> Unit,
    onCerrarViaje: () -> Unit,
    onSolicitarDescarte: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(modifier = modifier) {
        BloqueEncabezado(
            placas = estado.reporte?.placasCapturadas.orEmpty(),
            contador = estado.medios.size,
            cerrandoViaje = estado.cerrandoViaje,
            descartandoViaje = estado.descartandoViaje,
            onCerrarViaje = onCerrarViaje,
            onSolicitarDescarte = onSolicitarDescarte,
        )

        if (permisos.camara) {
            VistaCamara(
                alTenerController = alTenerController,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f),
            )
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Para capturar se necesita la cámara.",
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onSolicitarPermisos) { Text("Permitir cámara") }
                }
            }
        }

        ControlesCaptura(
            capturando = estado.capturando,
            grabandoAudio = estado.grabandoAudio,
            segundosAudio = estado.segundosAudio,
            limiteAlcanzado = estado.limiteAlcanzado,
            camaraLista = permisos.camara && controllerCamara != null && !estado.capturando,
            micListo = permisos.mic,
            onTomarFoto = {
                val camara = controllerCamara ?: return@ControlesCaptura
                tomarFoto(camara, context.mainExecutor) { jpeg, miniatura ->
                    onTomarFoto(jpeg, miniatura)
                }
            },
            onIniciarAudio = onIniciarAudio,
            onDetenerAudio = onDetenerAudio,
            onDescartarAudio = onDescartarAudio,
        )

        RejillaMedios(
            medios = estado.medios,
            onConfirmarBorrar = onConfirmarBorrar,
            onReintentar = { onReintentar(it.id) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

@Composable
private fun BloqueEncabezado(
    placas: String,
    contador: Int,
    cerrandoViaje: Boolean,
    descartandoViaje: Boolean,
    onCerrarViaje: () -> Unit,
    onSolicitarDescarte: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Viaje", style = MaterialTheme.typography.labelMedium)
                Text(placas, style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = "$contador de 50",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 8.dp),
            )
            TextButton(onClick = onCerrarViaje, enabled = !cerrandoViaje && !descartandoViaje) {
                Text("Cerrar viaje")
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = onSolicitarDescarte,
                enabled = !cerrandoViaje && !descartandoViaje,
            ) {
                Text(
                    text = "Descartar viaje",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ControlesCaptura(
    capturando: Boolean,
    grabandoAudio: Boolean,
    segundosAudio: Long,
    limiteAlcanzado: Boolean,
    camaraLista: Boolean,
    micListo: Boolean,
    onTomarFoto: () -> Unit,
    onIniciarAudio: () -> Unit,
    onDetenerAudio: () -> Unit,
    onDescartarAudio: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (grabandoAudio) {
            Text(
                text = "${formatearDuracion(segundosAudio)} · GRABANDO",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
            )
            FilledIconButton(onClick = onDetenerAudio) {
                Icon(Icons.Filled.Close, contentDescription = "Detener grabación")
            }
            TextButton(onClick = onDescartarAudio) { Text("Descartar") }
        } else {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                onClick = onTomarFoto,
                enabled = camaraLista && !capturando && !limiteAlcanzado,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_foto),
                        contentDescription = "Tomar foto",
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            FilledIconButton(
                onClick = onIniciarAudio,
                enabled = micListo && !capturando && !limiteAlcanzado,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Icon(painterResource(R.drawable.ic_mic), contentDescription = "Grabar nota de voz")
            }
        }
    }
}

@Composable
private fun RejillaMedios(
    medios: List<Medio>,
    onConfirmarBorrar: (Medio) -> Unit,
    onReintentar: (Medio) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (medios.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "Aquí aparecerán tus capturas.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(medios, key = { it.id }) { medio ->
            CeldaMedio(
                medio = medio,
                onConfirmarBorrar = { onConfirmarBorrar(medio) },
                onReintentar = { onReintentar(medio) },
            )
        }
    }
}

@Composable
private fun CeldaMedio(
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
                    contentDescription = "Miniatura",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_mic),
                        contentDescription = "Nota",
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }

        if (medio.estadoSubida == EstadoSubida.PENDIENTE) {
            IconButton(
                onClick = onConfirmarBorrar,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(28.dp),
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Borrar captura",
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
                    text = "FALLIDA",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            FilledIconButton(
                onClick = onReintentar,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(28.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "Reintentar subida",
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

private fun formatearDuracion(segundos: Long): String {
    val min = segundos / 60
    val seg = segundos % 60
    return "%d:%02d".format(min, seg)
}