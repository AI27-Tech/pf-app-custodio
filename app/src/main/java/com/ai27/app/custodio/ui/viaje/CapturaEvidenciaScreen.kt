package com.ai27.app.custodio.ui.viaje

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.LifecycleCameraController
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ai27.app.custodio.R
import com.ai27.app.custodio.ui.captura.CapturaUiState
import com.ai27.app.custodio.ui.captura.HojaEtiqueta
import com.ai27.app.custodio.ui.captura.VistaCamara
import com.ai27.app.custodio.ui.captura.tomarFoto
import com.ai27.app.custodio.ui.components.AvisoSubidaFlotante
import com.ai27.app.custodio.ui.components.BarraSuperior
import com.ai27.app.custodio.ui.components.DialogoError
import com.ai27.app.custodio.ui.components.Vitrina
import com.ai27.app.custodio.ui.previews.DatosDemo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch

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

    // El procesamiento de la foto (rotación + compresión JPEG del original y la
    // miniatura) es costoso y NO debe correr en el hilo principal: se ejecuta en el
    // pool por defecto para que el visor no se congele en el momento del disparo.
    val ejecutorImagen = remember { Dispatchers.Default.asExecutor() }

    // "Flash" del obturador: cuando la captura tiene éxito (dentro del callback de
    // `tomarFoto`, ya con el JPEG listo) se pinta un velo negro a pantalla completa
    // que se desvanece, para confirmar que la foto realmente se tomó aunque el
    // guardado y la ubicación tarden un instante más.
    val scope = rememberCoroutineScope()
    val flash = remember { Animatable(0f) }

    // En la vista previa (@Preview) no hay cámara ni diálogo de permisos que lanzar;
    // se simula el visor negro de la pantalla para que el HUD se pueda comparar contra
    // `captura_de_evidencia_c_mara_visor_negro/screen.png`.
    val inspeccion = LocalInspectionMode.current

    fun permisosActuales(): PermisosCamara = PermisosCamara(
        camara = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED,
        mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED,
    )

    fun permisosSolicitados(): Array<String> {
        val base = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
        return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            base + Manifest.permission.WRITE_EXTERNAL_STORAGE
        } else {
            base
        }
    }

    var permisos by remember {
        mutableStateOf(
            if (inspeccion) PermisosCamara(camara = true, mic = true) else permisosActuales(),
        )
    }
    val lanzadorPermisos = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permisos = permisosActuales() }

    LaunchedEffect(Unit) {
        if (!inspeccion && (!permisos.camara || !permisos.mic)) {
            lanzadorPermisos.launch(permisosSolicitados())
        }
    }

    val disparoHabilitado = permisos.camara &&
        controllerCamara != null &&
        !estado.capturando &&
        !estado.limiteAlcanzado

    Box(modifier.fillMaxSize()) {
        if (permisos.camara) {
            if (inspeccion) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                )
            } else {
                VistaCamara(
                    alTenerController = { controllerCamara = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
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
                        text = stringResource(R.string.captura_sin_camara),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.size(12.dp))
                    Button(
                        onClick = {
                            lanzadorPermisos.launch(permisosSolicitados())
                        },
                    ) { Text(stringResource(R.string.captura_permitir_camara)) }
                }
            }
        }

        BarraSuperior(
            titulo = stringResource(R.string.captura_titulo),
            onAtras = onAtras,
        ) {
            Text(
                text = stringResource(R.string.conteo_de_50, estado.medios.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp),
            )
        }

        if (permisos.camara) {
            Surface(
                onClick = {
                    val camara = controllerCamara ?: return@Surface
                    tomarFoto(camara, ejecutorImagen) { jpeg, miniatura ->
                        scope.launch {
                            flash.snapTo(1f)
                            flash.animateTo(0f, animationSpec = tween(durationMillis = 280))
                        }
                        onTomarFoto(jpeg, miniatura)
                    }
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                enabled = disparoHabilitado,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp)
                    .size(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_foto),
                        contentDescription = stringResource(R.string.captura_tomar_foto),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }

        AvisoSubidaFlotante(
            evento = estado.avisoEncolado,
            texto = stringResource(R.string.aviso_subida_en_breve),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 24.dp)
                .padding(top = 100.dp),
        )

        if (flash.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = flash.value)),
            )
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
    estado.error?.let { error ->
        DialogoError(mensaje = stringResource(error), onCerrar = onConsumirError)
    }
}

@Preview(name = "Claro")
@Composable
private fun CapturaEvidenciaVistaClaro() {
    Vitrina(oscuro = false) {
        CapturaEvidenciaScreen(
            estado = CapturaUiState(
                custodio = DatosDemo.custodio,
                reporte = DatosDemo.reporteActivo,
                etiquetas = DatosDemo.etiquetas,
            ),
            onAtras = {},
            onTomarFoto = { _, _ -> },
            onGuardarEtiqueta = { _, _ -> },
            onUbicacionTextoCambio = {},
            onCerrarHoja = {},
            onConsumirError = {},
        )
    }
}

@Preview(name = "Oscuro")
@Composable
private fun CapturaEvidenciaVistaOscuro() {
    Vitrina(oscuro = true) {
        CapturaEvidenciaScreen(
            estado = CapturaUiState(
                custodio = DatosDemo.custodio,
                reporte = DatosDemo.reporteActivo,
                etiquetas = DatosDemo.etiquetas,
            ),
            onAtras = {},
            onTomarFoto = { _, _ -> },
            onGuardarEtiqueta = { _, _ -> },
            onUbicacionTextoCambio = {},
            onCerrarHoja = {},
            onConsumirError = {},
        )
    }
}