package com.ai27.app.custodio.ui.viaje

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ai27.app.custodio.R
import com.ai27.app.custodio.ui.captura.CapturaUiState
import com.ai27.app.custodio.ui.captura.HojaEtiqueta
import com.ai27.app.custodio.ui.captura.VistaCamara
import com.ai27.app.custodio.ui.captura.tomarFoto
import com.ai27.app.custodio.ui.components.BarraSuperior
import com.ai27.app.custodio.ui.components.DialogoError

private data class PermisosCamara(
    val camara: Boolean,
    val mic: Boolean,
)

/**
 * Pantalla de captura de evidencia a pantalla completa (`captura_de_evidencia_cámara_visor_negro`).
 *
 * La cámara llena el área; la barra superior y el disparador se dibujan encima como
 * overlays opacos. Al disparar, el medio queda pendiente de etiquetar y [HojaEtiqueta]
 * se abre sobre esta pantalla.
 */
@Composable
fun CapturaEvidenciaScreen(
    estado: CapturaUiState,
    onAtras: () -> Unit,
    onTomarFoto: (jpeg: ByteArray, miniatura: ByteArray) -> Unit,
    onGuardarEtiqueta: (medioId: String, etiquetaId: String) -> Unit,
    onUbicacionTextoCambio: (String) -> Unit,
    onCerrarHoja: () -> Unit,
    onConsumirError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var controllerCamara by remember { mutableStateOf<LifecycleCameraController?>(null) }

    fun permisosActuales(): PermisosCamara = PermisosCamara(
        camara = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED,
        mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
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

    val disparoHabilitado = permisos.camara &&
        controllerCamara != null &&
        !estado.capturando &&
        !estado.limiteAlcanzado

    Box(modifier.fillMaxSize()) {
        if (permisos.camara) {
            VistaCamara(
                alTenerController = { controllerCamara = it },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Para capturar se necesita la cámara.",
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.size(12.dp))
                    Button(
                        onClick = {
                            lanzadorPermisos.launch(
                                arrayOf(
                                    Manifest.permission.CAMERA,
                                    Manifest.permission.RECORD_AUDIO,
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                ),
                            )
                        },
                    ) { Text("Permitir cámara") }
                }
            }
        }

        BarraSuperior(
            titulo = "Capturar evidencia",
            onAtras = onAtras,
        ) {
            Text(
                text = "${estado.medios.size} de 50",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp),
            )
        }

        if (permisos.camara) {
            Surface(
                onClick = {
                    val camara = controllerCamara ?: return@Surface
                    tomarFoto(camara, context.mainExecutor) { jpeg, miniatura ->
                        onTomarFoto(jpeg, miniatura)
                    }
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                enabled = disparoHabilitado,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .size(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_foto),
                        contentDescription = "Tomar foto",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp),
                    )
                }
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
    estado.error?.let { mensaje ->
        DialogoError(mensaje = mensaje, onCerrar = onConsumirError)
    }
}