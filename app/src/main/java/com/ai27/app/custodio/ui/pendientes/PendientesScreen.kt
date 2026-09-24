package com.ai27.app.custodio.ui.pendientes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ai27.app.custodio.R
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.TipoMedio
import com.ai27.app.custodio.ui.components.BarraSuperior
import com.ai27.app.custodio.ui.components.DialogoError
import com.ai27.app.custodio.ui.components.EstadoVacio
import com.ai27.app.custodio.ui.components.RenglonPendiente
import com.ai27.app.custodio.ui.components.Vitrina
import com.ai27.app.custodio.ui.previews.DatosDemo
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Cola de sincronización pendiente (`pendientes_de_subida`).
 *
 * Lista los medios que aún no terminaron de subir — PENDIENTE espera su turno y FALLIDA
 * ofrece reintento manual. La cola es global a la sesión: incluye evidencia de reportes
 * ya cerrados. El badge de la barra inferior usa la misma fuente que esta pantalla.
 */
@Composable
fun PendientesScreen(
    pendientes: List<Medio>,
    error: Int?,
    refrescando: Boolean,
    onRecargar: () -> Unit,
    onAtras: () -> Unit,
    onReintentar: (Medio) -> Unit,
    onConsumirError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        BarraSuperior(
            titulo = stringResource(R.string.boton_pendientes_titulo),
            onAtras = onAtras,
        )

        PullToRefreshBox(
            isRefreshing = refrescando,
            onRefresh = onRecargar,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (pendientes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center,
                ) {
                    EstadoVacio(
                        titulo = stringResource(R.string.pendientes_todo_sincronizado),
                        descripcion = stringResource(R.string.pendientes_ninguno),
                        modifier = Modifier.padding(32.dp),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(pendientes, key = { it.id }) { medio ->
                        RenglonPendiente(
                            titulo = if (medio.tipo == TipoMedio.AUDIO) {
                                stringResource(R.string.nota_de_voz)
                            } else {
                                stringResource(R.string.evidencia)
                            },
                            detalle = detalleDe(medio),
                            secundario = etiquetasDe(medio),
                            hora = horaDe(medio.createdAtDevice),
                            esAudio = medio.tipo == TipoMedio.AUDIO,
                            onReintentar = if (medio.estadoSubida == EstadoSubida.FALLIDA) {
                                { onReintentar(medio) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }

    error?.let { codigo ->
        DialogoError(mensaje = stringResource(codigo), onCerrar = onConsumirError)
    }
}

/**
 * Estado que la cola de pendientes muestra para un medio (U4). Extraído como función
 * pura para poder aseverarlo sin Compose; el string lo decide la pantalla.
 */
enum class EstadoPendiente { EN_COLA, ENVIANDO, FALLIDA, SIN_ETIQUETA }

fun estadoPendienteDe(medio: Medio): EstadoPendiente = when {
    // Sin Situación el medio NO está en cola: desde §13 la cola lo excluye (E3) y exige al
    // menos una (decisión A del PLAN §14). Decir "en cola" sería mentir y el custodio
    // esperaría eternamente a que subiera solo.
    medio.etiquetas.isEmpty() -> EstadoPendiente.SIN_ETIQUETA
    medio.estadoSubida == EstadoSubida.FALLIDA -> EstadoPendiente.FALLIDA
    medio.estadoSubida == EstadoSubida.EN_PROGRESO -> EstadoPendiente.ENVIANDO
    else -> EstadoPendiente.EN_COLA
}

@Composable
private fun detalleDe(medio: Medio): String = when (estadoPendienteDe(medio)) {
    EstadoPendiente.SIN_ETIQUETA -> stringResource(R.string.pendientes_falta_etiqueta)
    EstadoPendiente.FALLIDA -> stringResource(R.string.pendientes_subida_fallida)
    EstadoPendiente.ENVIANDO -> stringResource(R.string.pendientes_enviando)
    EstadoPendiente.EN_COLA -> stringResource(R.string.pendientes_en_cola)
}

/**
 * La etiqueta acompaña al estado como información secundaria (D-D1), nunca en su lugar.
 */
private fun etiquetasDe(medio: Medio): String? =
    medio.etiquetas.takeIf { it.isNotEmpty() }?.joinToString(", ")

private val formateadorHora: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun horaDe(instante: Instant): String =
    formateadorHora.format(instante.atZone(ZoneId.systemDefault()))

@Preview(name = "Claro")
@Composable
private fun PendientesVistaClaro() {
    Vitrina(oscuro = false) {
        PendientesScreen(
            pendientes = DatosDemo.mediosPendientes,
            error = null,
            refrescando = false,
            onRecargar = {},
            onAtras = {},
            onReintentar = {},
            onConsumirError = {},
        )
    }
}

@Preview(name = "Oscuro")
@Composable
private fun PendientesVistaOscuro() {
    Vitrina(oscuro = true) {
        PendientesScreen(
            pendientes = DatosDemo.mediosPendientes,
            error = null,
            refrescando = false,
            onRecargar = {},
            onAtras = {},
            onReintentar = {},
            onConsumirError = {},
        )
    }
}

@Preview(name = "Vacío · Claro")
@Composable
private fun PendientesVacioVistaClaro() {
    Vitrina(oscuro = false) {
        PendientesScreen(
            pendientes = emptyList(),
            error = null,
            refrescando = false,
            onRecargar = {},
            onAtras = {},
            onReintentar = {},
            onConsumirError = {},
        )
    }
}

@Preview(name = "Vacío · Oscuro")
@Composable
private fun PendientesVacioVistaOscuro() {
    Vitrina(oscuro = true) {
        PendientesScreen(
            pendientes = emptyList(),
            error = null,
            refrescando = false,
            onRecargar = {},
            onAtras = {},
            onReintentar = {},
            onConsumirError = {},
        )
    }
}