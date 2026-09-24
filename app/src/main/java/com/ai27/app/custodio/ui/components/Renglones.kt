package com.ai27.app.custodio.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ai27.app.custodio.R
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.ui.theme.TabularNumberStyle

/**
 * Renglón de la tarjeta de reporte del historial: placas, detalle y chip de estado.
 *
 * Es la única pieza de la biblioteca que conoce [EstadoSubida] por diseño, porque su
 * contrato es mostraba el estado del reporte junto a su chip.
 */
@Composable
fun RenglonReporte(
    placas: String,
    detalle: String,
    estado: EstadoSubida,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    distintivo: @Composable () -> Unit = {},
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = placas,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = detalle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChipEstadoSubida(estado = estado)
                    Spacer(Modifier.width(8.dp))
                    distintivo()
                }
            }
            Spacer(Modifier.width(12.dp))
            Icon(
                painter = painterResource(R.drawable.ic_chevron),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Renglón de archivo pendiente de subir: relleno, nombre, hora y botón de reintento.
 *
 * [esAudio] cambia el icono del recuadro; el reintento (icono de refresco) sólo se pinta
 * cuando [onReintentar] no es nulo. [detalle] es el estado de subida; [secundario] la
 * información adicional (p. ej. las etiquetas) debajo de él (U4). [enReintento] sustituye
 * el botón por un indicador de progreso para dar señal inmediata tras el toque (U10).
 */
@Composable
fun RenglonPendiente(
    titulo: String,
    detalle: String,
    hora: String,
    esAudio: Boolean,
    onReintentar: (() -> Unit)? = null,
    enReintento: Boolean = false,
    modifier: Modifier = Modifier,
    secundario: String? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(
                            if (esAudio) R.drawable.ic_mic else R.drawable.ic_adjunto,
                        ),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = detalle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (secundario != null) {
                    Text(
                        text = secundario,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = hora,
                style = TabularNumberStyle.copy(
                    fontSize = MaterialTheme.typography.labelMedium.fontSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                fontWeight = FontWeight.Medium,
            )
            if (enReintento) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else if (onReintentar != null) {
                IconButton(onClick = onReintentar) {
                    Icon(
                        painter = painterResource(R.drawable.ic_reintento),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/**
 * Renglón de resumen (par etiqueta/valor con unidad). El valor usa cifras tabulares para
 * que la columna no se mueva al cambiar de dígito.
 */
@Composable
fun RenglonResumen(
    etiqueta: String,
    valor: String,
    unidad: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = valor,
            style = TabularNumberStyle.copy(
                fontSize = MaterialTheme.typography.titleMedium.fontSize,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            fontWeight = FontWeight.SemiBold,
        )
        if (unidad != null) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = unidad,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Renglón con interruptor de dos estados. El track es un rectángulo de [MaterialTheme.shapes.small]
 * (56x32): Material 3 lo dibuja como píldora, y Nocturne lo prohíbe.
 */
@Composable
fun RenglonSwitch(
    titulo: String,
    descripcion: String? = null,
    marcado: Boolean,
    onCambiar: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable { onCambiar(!marcado) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            if (descripcion != null) {
                Text(
                    text = descripcion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Interruptor(
            marcado = marcado,
            onCambiar = onCambiar,
        )
    }
}

@Composable
private fun Interruptor(
    marcado: Boolean,
    onCambiar: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ancho = 56.dp
    val alto = 32.dp
    val grosorPulgar = 28.dp
    val corrida = (ancho - grosorPulgar) - 4.dp
    val pulgarX by animateDpAsState(
        targetValue = if (marcado) corrida else 2.dp,
        animationSpec = tween(160, delayMillis = 0),
        label = "pulgar",
    )
    Box(
        modifier = modifier
            .width(ancho)
            .height(alto)
            .clip(MaterialTheme.shapes.small)
            .background(
                if (marcado) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .clickable { onCambiar(!marcado) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = pulgarX)
                .size(grosorPulgar)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.inverseSurface),
        )
    }
}

/**
 * Encabezado de fecha del historial: texto en mayúsculas, tinta apagada y trazo inferior.
 */
@Composable
fun EncabezadoFecha(
    texto: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = texto.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Preview(name = "Oscuro", showBackground = true)
@Composable
private fun RenglonReporteVistaOscuro() {
    Vitrina(oscuro = true) {
        RenglonReporteMuestra()
    }
}

@Preview(name = "Claro", showBackground = true)
@Composable
private fun RenglonReporteVistaClaro() {
    Vitrina(oscuro = false) {
        RenglonReporteMuestra()
    }
}

@Composable
private fun RenglonReporteMuestra() {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RenglonReporte(
            placas = "ABEJO · CARGA",
            detalle = "Viaje del 20 sep · 3 de 5 enviados",
            estado = EstadoSubida.COMPLETA,
            onClick = {},
        )
        RenglonReporte(
            placas = "INCENDIO",
            detalle = "Viaje del 18 sep · 2 de 4 enviados",
            estado = EstadoSubida.FALLIDA,
            onClick = {},
        )
    }
}

@Preview(name = "Oscuro", showBackground = true)
@Composable
private fun RenglonPendienteVistaOscuro() {
    Vitrina(oscuro = true) {
        RenglonPendienteMuestra()
    }
}

@Preview(name = "Claro", showBackground = true)
@Composable
private fun RenglonPendienteVistaClaro() {
    Vitrina(oscuro = false) {
        RenglonPendienteMuestra()
    }
}

@Composable
private fun RenglonPendienteMuestra() {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RenglonPendiente(
            titulo = "IMG_20260920_104312.jpg",
            detalle = "Enviando…",
            secundario = "ABEJO",
            hora = "10:43",
            esAudio = false,
            onReintentar = {},
        )
        RenglonPendiente(
            titulo = "nota_carga_01.m4a",
            detalle = "En cola",
            secundario = "CARGA",
            hora = "10:45",
            esAudio = true,
        )
    }
}

@Preview(name = "Oscuro", showBackground = true)
@Composable
private fun RenglonResumenVistaOscuro() {
    Vitrina(oscuro = true) {
        RenglonResumenMuestra()
    }
}

@Preview(name = "Claro", showBackground = true)
@Composable
private fun RenglonResumenVistaClaro() {
    Vitrina(oscuro = false) {
        RenglonResumenMuestra()
    }
}

@Composable
private fun RenglonResumenMuestra() {
    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RenglonResumen(etiqueta = "Medios capturados", valor = "12")
        RenglonResumen(etiqueta = "Tiempo de captura", valor = "04:32", unidad = "min")
        RenglonResumen(etiqueta = "Peso total", valor = "48", unidad = "MB")
    }
}

@Preview(name = "Oscuro", showBackground = true)
@Composable
private fun RenglonSwitchVistaOscuro() {
    Vitrina(oscuro = true) {
        RenglonSwitchMuestra()
    }
}

@Preview(name = "Claro", showBackground = true)
@Composable
private fun RenglonSwitchVistaClaro() {
    Vitrina(oscuro = false) {
        RenglonSwitchMuestra()
    }
}

@Composable
private fun RenglonSwitchMuestra() {
    Column(Modifier.padding(24.dp)) {
        var autoSubida by remember { mutableStateOf(true) }
        var altoContraste by remember { mutableStateOf(false) }
        RenglonSwitch(
            titulo = "Subida automática",
            descripcion = "Sincroniza los medios al cerrar el viaje",
            marcado = autoSubida,
            onCambiar = { autoSubida = it },
        )
        RenglonSwitch(
            titulo = "Alto contraste",
            marcado = altoContraste,
            onCambiar = { altoContraste = it },
        )
    }
}

@Preview(name = "Oscuro", showBackground = true)
@Composable
private fun EncabezadoFechaVistaOscuro() {
    Vitrina(oscuro = true) {
        EncabezadoFechaMuestra()
    }
}

@Preview(name = "Claro", showBackground = true)
@Composable
private fun EncabezadoFechaVistaClaro() {
    Vitrina(oscuro = false) {
        EncabezadoFechaMuestra()
    }
}

@Composable
private fun EncabezadoFechaMuestra() {
    Column(Modifier.padding(16.dp)) {
        EncabezadoFecha(texto = "Septiembre 2026")
    }
}