package com.ai27.app.custodio.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ai27.app.custodio.R
import com.ai27.app.custodio.ui.theme.TabularNumberStyle

/**
 * Botón principal del sistema: relleno de [MaterialTheme.colorScheme.primary] con texto
 * de [MaterialTheme.colorScheme.onPrimary], geometría de [MaterialTheme.shapes.small] y
 * altura de 48dp.
 *
 * Es la llamada a la acción dominante de cada pantalla — iniciar captura, guardar el
 * reporte, decidir destino — y por eso se usa una sola vez por superficie siempre que
 * sea posible.
 */
@Composable
fun BotonPrimario(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = habilitado,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        Text(
            text = texto,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Botón secundario del sistema: contorno de `primary` al 50%, texto de `primary`, misma
 * geometría y altura que [BotonPrimario].
 *
 * Acompaña al botón primario en decisiones con alternativa ("Cancelar", "Más tarde").
 */
@Composable
fun BotonSecundario(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = habilitado,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(
            1.dp,
            if (habilitado) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.50f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        Text(
            text = texto,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Tarjeta de pendientes de sincronización: el botón que reenvía a la cola de subida
 * cuando el gestor de medios acumula trabajos sin completar.
 *
 * El contador ámbar ([MaterialTheme.colorScheme.tertiary]) que se dibuja sobre el icono
 * es la segunda excepción circular que el sistema concede (junto a [BotonGrabacion]).
 * Cuando [pendientes] es cero el badge se oculta y la tarjeta queda como acción simple.
 */
@Composable
fun BotonPendientes(
    pendientes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    titulo: String = stringResource(R.string.boton_pendientes_titulo),
    detalle: String = stringResource(R.string.boton_pendientes_detalle),
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_subir),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    if (pendientes > 0) {
                        BadgePendientes(
                            conteo = pendientes,
                            modifier = Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 3.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = detalle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
 * Badge circular de conteo sobre fondos de icono (pendientes de subida, etc.). Tinta de
 * [MaterialTheme.colorScheme.onTertiary] sobre [MaterialTheme.colorScheme.tertiary].
 */
@Composable
internal fun BadgePendientes(
    conteo: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = conteo.toString(),
        color = MaterialTheme.colorScheme.onTertiary,
        style = TabularNumberStyle.copy(
            fontSize = MaterialTheme.typography.labelSmall.fontSize,
            color = MaterialTheme.colorScheme.onTertiary,
        ),
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .size(18.dp)
            .background(MaterialTheme.colorScheme.tertiary, CircleShape),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

/**
 * Botón redondo de grabación de nota de voz, de 96dp. Es la excepción circular principal
 * del sistema (D3): el cruce entre botón y control de medio en vivo.
 *
 * En reposo muestra el micrófono; durante la grabación, el glifo de alto (cuadro
 * redondeado) y la superficie pasa a [MaterialTheme.colorScheme.error]. El estilo
 * tabular del cronómetro vive en la pantalla, no aquí.
 */
@Composable
fun BotonGrabacion(
    grabando: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val descripcion = if (grabando) {
        stringResource(R.string.boton_grabacion_detener)
    } else {
        stringResource(R.string.boton_grabacion_grabar)
    }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier
            .size(96.dp)
            .semantics { contentDescription = descripcion },
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (grabando) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onError,
                            shape = MaterialTheme.shapes.small,
                        ),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_mic),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
    }
}

@Preview(name = "Oscuro", showBackground = true)
@Composable
private fun BotonPrimarioVistaOscuro() {
    Vitrina(oscuro = true) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BotonPrimario(texto = "BAJAR BÁGARO", onClick = {})
            BotonPrimario(texto = "BAJAR BÁGARO", onClick = {}, habilitado = false)
        }
    }
}

@Preview(name = "Claro", showBackground = true)
@Composable
private fun BotonPrimarioVistaClaro() {
    Vitrina(oscuro = false) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BotonPrimario(texto = "BAJAR BÁGARO", onClick = {})
            BotonPrimario(texto = "BAJAR BÁGARO", onClick = {}, habilitado = false)
        }
    }
}

@Preview(name = "Oscuro", showBackground = true)
@Composable
private fun BotonSecundarioVistaOscuro() {
    Vitrina(oscuro = true) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BotonSecundario(texto = "Cancelar", onClick = {})
            BotonSecundario(texto = "Cancelar", onClick = {}, habilitado = false)
        }
    }
}

@Preview(name = "Claro", showBackground = true)
@Composable
private fun BotonSecundarioVistaClaro() {
    Vitrina(oscuro = false) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BotonSecundario(texto = "Cancelar", onClick = {})
            BotonSecundario(texto = "Cancelar", onClick = {}, habilitado = false)
        }
    }
}

@Preview(name = "Oscuro", showBackground = true)
@Composable
private fun BotonPendientesVistaOscuro() {
    Vitrina(oscuro = true) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BotonPendientes(pendientes = 3, onClick = {})
            BotonPendientes(pendientes = 0, onClick = {})
        }
    }
}

@Preview(name = "Claro", showBackground = true)
@Composable
private fun BotonPendientesVistaClaro() {
    Vitrina(oscuro = false) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BotonPendientes(pendientes = 3, onClick = {})
        }
    }
}

@Preview(name = "Oscuro", showBackground = true)
@Composable
private fun BotonGrabacionVistaOscuro() {
    Vitrina(oscuro = true) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            var grabando by remember { mutableStateOf(false) }
            BotonGrabacion(grabando = grabando, onClick = { grabando = !grabando })
            BotonGrabacion(grabando = true, onClick = {})
        }
    }
}

@Preview(name = "Claro", showBackground = true)
@Composable
private fun BotonGrabacionVistaClaro() {
    Vitrina(oscuro = false) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            BotonGrabacion(grabando = false, onClick = {})
            BotonGrabacion(grabando = true, onClick = {})
        }
    }
}