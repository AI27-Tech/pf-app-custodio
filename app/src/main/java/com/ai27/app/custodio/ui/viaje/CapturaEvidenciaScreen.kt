package com.ai27.app.custodio.ui.viaje

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ai27.app.custodio.R
import com.ai27.app.custodio.ui.captura.CapturaUiState
// RETIRO_ETIQUETADO (Task 4): HojaEtiqueta queda retirada de esta pantalla; se comenta su
// import para reactivarlo con el etiquetado.
// import com.ai27.app.custodio.ui.captura.HojaEtiqueta
import com.ai27.app.custodio.ui.captura.VistaCamara
import com.ai27.app.custodio.ui.captura.tomarFoto
import com.ai27.app.custodio.ui.components.AvisoSubidaFlotante
import com.ai27.app.custodio.ui.components.BarraSuperior
import com.ai27.app.custodio.ui.components.BotonSecundario
import com.ai27.app.custodio.ui.components.DialogoError
import com.ai27.app.custodio.ui.components.FranjaAviso
import com.ai27.app.custodio.ui.components.TicDeEncolado
import com.ai27.app.custodio.ui.components.Vitrina
import com.ai27.app.custodio.ui.components.abrirAjustesDelSistema
import com.ai27.app.custodio.ui.components.recordarRetroalimentacion
import com.ai27.app.custodio.ui.previews.DatosDemo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch

private data class PermisosCamara(
    val camara: Boolean,
    val mic: Boolean,
    // S8: hay que pedir y RE-CHECKEAR la ubicación aproximada; la fina sigue siendo la
    // ideal, pero la app funciona (con menor precisión) teniendo solo la COARSE.
    val ubicacion: Boolean,
)

/**
 * Pantalla de captura de evidencia a pantalla completa (`captura_de_evidencia_cámara_visor_negro`).
 *
 * La cámara llena el área; la barra superior y el disparador se dibujan encima como
 * overlays opacos. Al disparar, el medio queda persistido y pasa directo a la cola de
 * subida (RETIRO_ETIQUETADO Task 4: ya no se abre ninguna hoja de etiquetado).
 */
@Composable
fun CapturaEvidenciaScreen(
    estado: CapturaUiState,
    conectado: Boolean?,
    onAtras: () -> Unit,
    onTomarFoto: (crudo: ByteArray, rotacionGrados: Int) -> Unit,
    onFallaDeCaptura: (causa: Throwable) -> Unit,
    // RETIRO_ETIQUETADO (Task 4): los callbacks del etiquetado se retiran de la firma.
    // Se dejan comentados —no borrados— para reactivarlos junto con HojaEtiqueta.
    // onGuardarEtiqueta: (medioId: String, etiquetas: List<String>) -> Unit,
    // onUbicacionTextoCambio: (String) -> Unit,
    // onCerrarHoja: () -> Unit,
    onConsumirError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var controllerCamara by remember { mutableStateOf<LifecycleCameraController?>(null) }

    // Cerrojo del obturador, sincrónico con el toque. Es lo que impide que un segundo
    // toque lance otra captura mientras la primera todavía no llega de la cámara: el
    // `estado.capturando` del ViewModel llega demasiado tarde para eso (hallazgo E1).
    var disparoEnCurso by remember { mutableStateOf(false) }

    // El callback de la foto solo copia el búfer JPEG del HAL (sin decodificar), pero esa
    // copia de cientos de KB tampoco va en el hilo principal: corre en el pool por defecto para
    // que el visor no tropiece en el disparo. La miniatura y el revelado los hace RevelarFoto.
    val ejecutorImagen = remember { Dispatchers.Default.asExecutor() }

    // "Flash" del obturador: se pinta un velo negro a pantalla completa que se desvanece.
    // Se dispara en el toque, no al llegar la foto, para que el custodio tenga
    // confirmación inmediata de que la app registró la pulsación.
    val scope = rememberCoroutineScope()
    val flash = remember { Animatable(0f) }

    // En la vista previa (@Preview) no hay cámara ni diálogo de permisos que lanzar;
    // se simula el visor negro de la pantalla para que el HUD se pueda comparar contra
    // `captura_de_evidencia_c_mara_visor_negro/screen.png`.
    val inspeccion = LocalInspectionMode.current

    // U3: confirmación no visual del disparo y del encolado.
    val retroalimentacion = recordarRetroalimentacion()

    fun permisosActuales(): PermisosCamara = PermisosCamara(
        camara = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED,
        mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED,
        ubicacion = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED,
    )

    fun permisosSolicitados(): Array<String> = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        // S8: la aproximada da lo mismo pedirla junto en el mismo diálogo: si el
        // custodio solo concede la COARSE, la captura sigue funcionando degradada.
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    var permisos by remember {
        mutableStateOf(
            if (inspeccion) PermisosCamara(camara = true, mic = true, ubicacion = true) else permisosActuales(),
        )
    }
    // Los permisos se piden todos juntos tras el primer inicio de sesión (ver
    // `SolicitarPermisosTrasInicioDeSesion`); aquí solo se re-piden a petición del custodio
    // con el botón de permiso, nunca al abrir el visor.
    val lanzadorPermisos = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permisos = permisosActuales() }

    // S9: al volver de los Ajustes del sistema (o de un diálogo que ya pidió todo) el
    // estado de permisos pudo cambiar; se re-chequea en ON_RESUME para que la pantalla
    // reaccione sin obligar a navegar fuera y volver.
    val duenoDelCiclo = LocalLifecycleOwner.current
    DisposableEffect(duenoDelCiclo) {
        val observador = LifecycleEventObserver { _, evento ->
            if (evento == Lifecycle.Event.ON_RESUME) permisos = permisosActuales()
        }
        duenoDelCiclo.lifecycle.addObserver(observador)
        onDispose { duenoDelCiclo.lifecycle.removeObserver(observador) }
    }

    val disparoHabilitado = permisos.camara &&
        controllerCamara != null &&
        !disparoEnCurso &&
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
                    Spacer(Modifier.size(8.dp))
                    // S9: si el diálogo ya no puede volver a pedirse ("no volver a
                    // preguntar") el único camino es la pantalla de Ajustes del sistema;
                    // el re-chequeo en ON_RESUME actualiza el estado al regresar.
                    BotonSecundario(
                        texto = stringResource(R.string.captura_abrir_ajustes),
                        onClick = { abrirAjustesDelSistema(context) },
                    )
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
                    // Cerrojo SÍNCRONO: `estado.capturando` solo se pone en true cuando la
                    // foto cruda ya llegó de la cámara, así que por sí solo no impide el
                    // segundo toque (hallazgo E1). Se libera en los dos callbacks de
                    // tomarFoto, que son excluyentes, en cuanto llega la cruda.
                    disparoEnCurso = true
                    // U3: la confirmación háptica+sonido va EN el toque, como el flash, no en
                    // el callback de éxito (que llega cuando la cámara entrega la foto).
                    retroalimentacion.disparo()
                    // El flash va ANTES de disparar, no en el callback de éxito: el toque
                    // tiene que producir señal inmediata, porque la ausencia de señal es
                    // justo lo que hace que el custodio vuelva a tocar.
                    scope.launch {
                        flash.snapTo(1f)
                        flash.animateTo(0f, animationSpec = tween(durationMillis = 280))
                    }
                    tomarFoto(
                        controller = camara,
                        ejecutor = ejecutorImagen,
                        alTenerFoto = { crudo, rotacionGrados ->
                            disparoEnCurso = false
                            onTomarFoto(crudo, rotacionGrados)
                        },
                        alFallar = { causa ->
                            disparoEnCurso = false
                            onFallaDeCaptura(causa)
                        },
                    )
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

        // U9: la franja de sin conexión también vive sobre el visor, como el aviso de
        // encolado, para que capturar nunca se lea como "ya se envió".
        if (conectado == false) {
            FranjaAviso(
                texto = stringResource(R.string.reporte_sin_conexion),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 24.dp)
                    .padding(top = 100.dp),
            )
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

    TicDeEncolado(avisoEncolado = estado.avisoEncolado, retroalimentacion = retroalimentacion)

    // RETIRO_ETIQUETADO (Task 4): la hoja de etiquetado se retira. El medio capturado pasa
    // directo a la cola de subida; ya no se clasifica aquí. Comentado, no borrado.
    // estado.medioPendienteDeEtiquetar?.let {
    //     HojaEtiqueta(
    //         estado = estado,
    //         onGuardar = onGuardarEtiqueta,
    //         onUbicacionTextoCambio = onUbicacionTextoCambio,
    //         onCerrar = onCerrarHoja,
    //     )
    // }
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
            conectado = true,
            onAtras = {},
            onTomarFoto = { _, _ -> },
            onFallaDeCaptura = {},
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
            conectado = true,
            onAtras = {},
            onTomarFoto = { _, _ -> },
            onFallaDeCaptura = {},
            onConsumirError = {},
        )
    }
}