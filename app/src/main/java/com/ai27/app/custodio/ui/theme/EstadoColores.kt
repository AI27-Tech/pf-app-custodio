package com.ai27.app.custodio.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colores de los estados de subida, que Material 3 no cubre.
 *
 * `EstadoSubida` tiene cuatro valores y el esquema de M3 sólo ofrece `error` y `tertiary`
 * como ranuras semánticas; forzarlos ahí deforma su significado. Se exponen aparte,
 * siguiendo la receta de Nocturne para chips de estado: el color al 12% como relleno, al
 * 25% como borde, y a plena intensidad como contenido.
 *
 * Recordatorio de accesibilidad: el color no debe ser el único portador del estado. Los
 * chips que consumen estos tokens acompañan el color con texto, y en la fase C2 también
 * con icono.
 */
@Immutable
data class ColorEstado(
    val contenido: Color,
    val contenedor: Color,
    val borde: Color,
)

@Immutable
data class ColoresEstado(
    val enviado: ColorEstado,
    val subiendo: ColorEstado,
    val pendiente: ColorEstado,
    val fallida: ColorEstado,
)

private fun receta(base: Color) = ColorEstado(
    contenido = base,
    contenedor = base.copy(alpha = 0.12f),
    borde = base.copy(alpha = 0.25f),
)

internal val ColoresEstadoOscuro = ColoresEstado(
    enviado = receta(DarkTokens.Success),
    subiendo = receta(DarkTokens.Warning),
    pendiente = ColorEstado(
        contenido = DarkTokens.TextMuted,
        contenedor = DarkTokens.SurfaceRaised,
        borde = DarkTokens.Border,
    ),
    fallida = receta(DarkTokens.Danger),
)

internal val ColoresEstadoClaro = ColoresEstado(
    enviado = receta(LightTokens.Success),
    subiendo = receta(LightTokens.Warning),
    pendiente = ColorEstado(
        contenido = LightTokens.TextMuted,
        contenedor = LightTokens.SurfaceHighest,
        borde = LightTokens.Border,
    ),
    fallida = receta(LightTokens.Danger),
)

internal val LocalColoresEstado = staticCompositionLocalOf { ColoresEstadoOscuro }

/**
 * Acceso a los tokens propios del tema, al lado de [androidx.compose.material3.MaterialTheme].
 */
object AppTheme {
    val coloresEstado: ColoresEstado
        @Composable @ReadOnlyComposable
        get() = LocalColoresEstado.current
}
