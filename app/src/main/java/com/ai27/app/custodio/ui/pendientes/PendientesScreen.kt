package com.ai27.app.custodio.ui.pendientes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.TipoMedio
import com.ai27.app.custodio.ui.components.BarraSuperior
import com.ai27.app.custodio.ui.components.DialogoError
import com.ai27.app.custodio.ui.components.EstadoVacio
import com.ai27.app.custodio.ui.components.RenglonPendiente
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
    error: String?,
    onAtras: () -> Unit,
    onReintentar: (Medio) -> Unit,
    onConsumirError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        BarraSuperior(
            titulo = "Subir pendientes",
            onAtras = onAtras,
        )

        if (pendientes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                EstadoVacio(
                    titulo = "Todo sincronizado",
                    descripcion = "No hay archivos pendientes\nen esta sesión.",
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
                        titulo = if (medio.tipo == TipoMedio.AUDIO) "Nota de voz" else "Evidencia",
                        detalle = detalleDe(medio),
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

    error?.let { mensaje ->
        DialogoError(mensaje = mensaje, onCerrar = onConsumirError)
    }
}

private fun detalleDe(medio: Medio): String =
    medio.etiqueta ?: when (medio.estadoSubida) {
        EstadoSubida.FALLIDA -> "Subida fallida"
        EstadoSubida.EN_PROGRESO -> "Enviando…"
        else -> "En cola"
    }

private val formateadorHora: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun horaDe(instante: Instant): String =
    formateadorHora.format(instante.atZone(ZoneId.systemDefault()))