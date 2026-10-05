package com.ai27.app.custodio.ui.viaje

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ai27.app.custodio.R
import com.ai27.app.custodio.ui.captura.CapturaUiState
// RETIRO_ETIQUETADO (Task 7): HojaEtiqueta queda retirada de esta pantalla; se comenta su
// import para reactivarlo con el etiquetado.
// import com.ai27.app.custodio.ui.captura.HojaEtiqueta
import com.ai27.app.custodio.ui.components.AvisoSubidaFlotante
import com.ai27.app.custodio.ui.components.BarraSuperior
import com.ai27.app.custodio.ui.components.BotonGrabacion
import com.ai27.app.custodio.ui.components.BotonPrimario
import com.ai27.app.custodio.ui.components.BotonSecundario
import com.ai27.app.custodio.ui.components.ContenedorConAviso
import com.ai27.app.custodio.ui.components.DialogoError
import com.ai27.app.custodio.ui.components.FranjaAviso
import com.ai27.app.custodio.ui.components.TicDeEncolado
import com.ai27.app.custodio.ui.components.Vitrina
import com.ai27.app.custodio.ui.components.abrirAjustesDelSistema
import com.ai27.app.custodio.ui.components.recordarRetroalimentacion
import com.ai27.app.custodio.ui.previews.DatosDemo
import com.ai27.app.custodio.ui.theme.TabularNumberStyle
import kotlinx.coroutines.delay

/**
 * Pantalla de grabación de nota de voz (`grabaci_n_de_audio_nota_de_voz`).
 *
 * El botón redondo ([BotonGrabacion]) alterna iniciar/detener; el cronómetro usa el
 * estilo tabular del sistema y el tope de 5 minutos lo impone el propio grabador. Al
 * terminar, el medio pasa directo a la cola de subida (RETIRO_ETIQUETADO Task 7: ya no se
 * abre ninguna hoja de etiquetado).
 *
 * Al pasar la app a segundo plano (`ON_STOP`) la nota **se pausa sola**: seguir grabando
 * exigiría un servicio en primer plano, y dejarla corriendo sin él termina en audio en
 * silencio cuando el sistema le quita el micrófono (hallazgo E5). Al volver, el custodio
 * la reanuda con el mismo botón.
 */
@Composable
fun AudioScreen(
    estado: CapturaUiState,
    conectado: Boolean?,
    onAtras: () -> Unit,
    onIniciarAudio: () -> Unit,
    onDetenerAudio: () -> Unit,
    onPausarAudio: () -> Unit,
    onReanudarAudio: () -> Unit,
    onDescartarAudio: () -> Unit,
    // RETIRO_ETIQUETADO (Task 7): los callbacks del etiquetado se retiran de la firma.
    // Comentados —no borrados— para reactivarlos junto con HojaEtiqueta.
    // onGuardarEtiqueta: (medioId: String, etiquetas: List<String>) -> Unit,
    // onUbicacionTextoCambio: (String) -> Unit,
    // onCerrarHoja: () -> Unit,
    onConsumirError: () -> Unit,
    onConsumirAvisoNotaPerdida: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // S10: el micrófono se puede negar por separado y esta pantalla es el lugar donde
    // hace falta; se pide desde aquí (y se re-chequea al volver de Ajustes) en lugar de
    // dejar que el grabador falle con `error_grabacion_no_inicio`.
    val context = LocalContext.current
    val inspeccion = LocalInspectionMode.current
    // U3: confirmación no visual del encolado.
    val retroalimentacion = recordarRetroalimentacion()
    fun tieneMicrofono(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    var tienesMicrofono by remember { mutableStateOf(inspeccion || tieneMicrofono()) }
    val lanzadorMicrofono = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { concedido -> tienesMicrofono = concedido }

    val duenoDelCiclo = LocalLifecycleOwner.current
    DisposableEffect(duenoDelCiclo, estado.grabandoAudio) {
        val observador = LifecycleEventObserver { _, evento ->
            if (evento == Lifecycle.Event.ON_STOP) onPausarAudio()
            if (evento == Lifecycle.Event.ON_RESUME) tienesMicrofono = tieneMicrofono()
        }
        duenoDelCiclo.lifecycle.addObserver(observador)
        onDispose { duenoDelCiclo.lifecycle.removeObserver(observador) }
    }

    Box(modifier.fillMaxSize()) {
        // U9: la franja de sin conexión vive en todo el flujo, también aquí.
        ContenedorConAviso(
            conectado = conectado,
            sinConexion = stringResource(R.string.reporte_sin_conexion),
            modifier = Modifier.fillMaxSize(),
        ) {
            BarraSuperior(titulo = stringResource(R.string.nota_de_voz), onAtras = onAtras)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (estado.avisoNotaPerdida) {
                    FranjaAviso(texto = stringResource(R.string.audio_nota_perdida))
                    Spacer(Modifier.height(12.dp))
                }
                // U13: el texto de apoyo ("máximo 5:00") es informativo, no una advertencia:
                // se pinta como texto secundario y se deja FranjaAviso para los avisos reales
                // (sin micrófono, nota perdida, sin conexión).
                Text(
                    text = stringResource(R.string.audio_aviso),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(32.dp))

                if (estado.grabandoAudio) {
                    val pausada = estado.audioPausado
                    Text(
                        text = formatearDuracion(estado.segundosAudio),
                        style = TabularNumberStyle.copy(
                            fontSize = MaterialTheme.typography.headlineMedium.fontSize,
                        ),
                        fontWeight = FontWeight.Bold,
                        color = if (pausada) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (pausada) {
                            stringResource(R.string.audio_en_pausa)
                        } else {
                            stringResource(R.string.audio_grabando)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (pausada) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (pausada) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.audio_reanudar_indicacion),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (estado.capturando) {
                    Text(
                        text = stringResource(R.string.audio_guardando),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.audio_toca_para_iniciar),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!tienesMicrofono) {
                        Spacer(Modifier.height(12.dp))
                        FranjaAviso(texto = stringResource(R.string.audio_sin_microfono))
                        Spacer(Modifier.height(8.dp))
                        // S10: pedir el micrófono aquí; si el diálogo ya no vuelve a
                        // aparecer, la salida es la pantalla de Ajustes del sistema.
                        BotonPrimario(
                            texto = stringResource(R.string.audio_permitir_microfono),
                            onClick = {
                                if (!inspeccion) lanzadorMicrofono.launch(Manifest.permission.RECORD_AUDIO)
                            },
                        )
                        Spacer(Modifier.height(4.dp))
                        BotonSecundario(
                            texto = stringResource(R.string.captura_abrir_ajustes),
                            onClick = { abrirAjustesDelSistema(context) },
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))

                // En pausa el botón redondo reanuda; el guardado pasa a un botón aparte
                // para que no haya un solo control con tres significados.
                BotonGrabacion(
                    grabando = estado.grabandoAudio && !estado.audioPausado,
                    onClick = when {
                        !estado.grabandoAudio -> onIniciarAudio
                        estado.audioPausado -> onReanudarAudio
                        else -> onDetenerAudio
                    },
                )

                if (estado.grabandoAudio) {
                    Spacer(Modifier.height(24.dp))
                    if (estado.audioPausado) {
                        BotonPrimario(
                            texto = stringResource(R.string.audio_detener_y_guardar),
                            onClick = onDetenerAudio,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    BotonSecundario(
                        texto = stringResource(R.string.comun_cancelar),
                        onClick = onDescartarAudio,
                    )
                } else {
                    Spacer(Modifier.height(24.dp))
                    Spacer(Modifier.size(48.dp))
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
    }

    TicDeEncolado(avisoEncolado = estado.avisoEncolado, retroalimentacion = retroalimentacion)

    // RETIRO_ETIQUETADO (Task 7): la hoja de etiquetado se retira. La nota capturada pasa
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
    // El aviso de nota perdida se consume al entrar aquí: es el lugar donde tiene sentido
    // y no debe seguir apareciendo en cada visita.
    LaunchedEffect(estado.avisoNotaPerdida) {
        if (estado.avisoNotaPerdida) {
            delay(AVISO_NOTA_PERDIDA_MS)
            onConsumirAvisoNotaPerdida()
        }
    }
}

private const val AVISO_NOTA_PERDIDA_MS = 8_000L

private fun formatearDuracion(segundos: Long): String {
    val min = segundos / 60
    val seg = segundos % 60
    return "%d:%02d".format(min, seg)
}

@Preview(name = "Claro")
@Composable
private fun AudioVistaClaro() {
    Vitrina(oscuro = false) {
        AudioScreen(
            estado = CapturaUiState(
                custodio = DatosDemo.custodio,
                reporte = DatosDemo.reporteActivo,
                grabandoAudio = true,
                segundosAudio = 187L,
            ),
            conectado = true,
            onAtras = {},
            onIniciarAudio = {},
            onDetenerAudio = {},
            onPausarAudio = {},
            onReanudarAudio = {},
            onDescartarAudio = {},
            onConsumirError = {},
            onConsumirAvisoNotaPerdida = {},
        )
    }
}

@Preview(name = "Oscuro")
@Composable
private fun AudioVistaOscuro() {
    Vitrina(oscuro = true) {
        AudioScreen(
            estado = CapturaUiState(
                custodio = DatosDemo.custodio,
                reporte = DatosDemo.reporteActivo,
                grabandoAudio = true,
                segundosAudio = 187L,
            ),
            conectado = true,
            onAtras = {},
            onIniciarAudio = {},
            onDetenerAudio = {},
            onPausarAudio = {},
            onReanudarAudio = {},
            onDescartarAudio = {},
            onConsumirError = {},
            onConsumirAvisoNotaPerdida = {},
        )
    }
}

@Preview(name = "En pausa · Claro")
@Composable
private fun AudioPausadoVistaClaro() {
    Vitrina(oscuro = false) {
        AudioScreen(
            estado = CapturaUiState(
                custodio = DatosDemo.custodio,
                reporte = DatosDemo.reporteActivo,
                grabandoAudio = true,
                audioPausado = true,
                segundosAudio = 187L,
            ),
            conectado = true,
            onAtras = {},
            onIniciarAudio = {},
            onDetenerAudio = {},
            onPausarAudio = {},
            onReanudarAudio = {},
            onDescartarAudio = {},
            onConsumirError = {},
            onConsumirAvisoNotaPerdida = {},
        )
    }
}

@Preview(name = "En pausa · Oscuro")
@Composable
private fun AudioPausadoVistaOscuro() {
    Vitrina(oscuro = true) {
        AudioScreen(
            estado = CapturaUiState(
                custodio = DatosDemo.custodio,
                reporte = DatosDemo.reporteActivo,
                grabandoAudio = true,
                audioPausado = true,
                segundosAudio = 187L,
            ),
            conectado = true,
            onAtras = {},
            onIniciarAudio = {},
            onDetenerAudio = {},
            onPausarAudio = {},
            onReanudarAudio = {},
            onDescartarAudio = {},
            onConsumirError = {},
            onConsumirAvisoNotaPerdida = {},
        )
    }
}

@Preview(name = "Nota perdida · Oscuro")
@Composable
private fun AudioNotaPerdidaVistaOscuro() {
    Vitrina(oscuro = true) {
        AudioScreen(
            estado = CapturaUiState(
                custodio = DatosDemo.custodio,
                reporte = DatosDemo.reporteActivo,
                avisoNotaPerdida = true,
            ),
            conectado = true,
            onAtras = {},
            onIniciarAudio = {},
            onDetenerAudio = {},
            onPausarAudio = {},
            onReanudarAudio = {},
            onDescartarAudio = {},
            onConsumirError = {},
            onConsumirAvisoNotaPerdida = {},
        )
    }
}