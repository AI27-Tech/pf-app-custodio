package com.ai27.app.custodio.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val EsquemaOscuro = darkColorScheme(
    primary = DarkTokens.Primary,
    onPrimary = DarkTokens.OnPrimary,
    primaryContainer = DarkTokens.Secondary,
    onPrimaryContainer = DarkTokens.Primary,
    inversePrimary = LightTokens.Primary,

    secondary = DarkTokens.Secondary,
    onSecondary = DarkTokens.OnSecondary,
    secondaryContainer = DarkTokens.SurfaceHighest,
    onSecondaryContainer = DarkTokens.Text,

    tertiary = DarkTokens.Warning,
    onTertiary = DarkTokens.Canvas,
    tertiaryContainer = DarkTokens.Warning.copy(alpha = 0.12f),
    onTertiaryContainer = DarkTokens.Warning,

    error = DarkTokens.Danger,
    onError = DarkTokens.OnDanger,
    errorContainer = DarkTokens.Danger.copy(alpha = 0.12f),
    onErrorContainer = DarkTokens.Danger,

    background = DarkTokens.Canvas,
    onBackground = DarkTokens.Text,

    surface = DarkTokens.Surface,
    onSurface = DarkTokens.Text,
    surfaceVariant = DarkTokens.SurfaceRaised,
    onSurfaceVariant = DarkTokens.TextMuted,
    surfaceTint = DarkTokens.Primary,
    surfaceBright = DarkTokens.SurfaceHighest,
    surfaceDim = DarkTokens.Canvas,

    surfaceContainerLowest = DarkTokens.SurfaceLowest,
    surfaceContainerLow = DarkTokens.SurfaceLow,
    surfaceContainer = DarkTokens.Surface,
    surfaceContainerHigh = DarkTokens.SurfaceRaised,
    surfaceContainerHighest = DarkTokens.SurfaceHighest,

    inverseSurface = DarkTokens.Text,
    inverseOnSurface = DarkTokens.Canvas,

    outline = DarkTokens.BorderStrong,
    outlineVariant = DarkTokens.Border,
    scrim = Color.Black,
)

private val EsquemaClaro = lightColorScheme(
    primary = LightTokens.Primary,
    onPrimary = LightTokens.OnPrimary,
    primaryContainer = LightTokens.Secondary,
    onPrimaryContainer = LightTokens.Primary,
    inversePrimary = DarkTokens.Primary,

    secondary = LightTokens.Secondary,
    onSecondary = LightTokens.OnSecondary,
    secondaryContainer = LightTokens.SurfaceHighest,
    onSecondaryContainer = LightTokens.Text,

    tertiary = LightTokens.Warning,
    onTertiary = LightTokens.OnPrimary,
    tertiaryContainer = LightTokens.Warning.copy(alpha = 0.12f),
    onTertiaryContainer = LightTokens.Warning,

    error = LightTokens.Danger,
    onError = LightTokens.OnDanger,
    errorContainer = LightTokens.Danger.copy(alpha = 0.10f),
    onErrorContainer = LightTokens.Danger,

    background = LightTokens.Canvas,
    onBackground = LightTokens.Text,

    surface = LightTokens.Surface,
    onSurface = LightTokens.Text,
    surfaceVariant = LightTokens.SurfaceHighest,
    onSurfaceVariant = LightTokens.TextMuted,
    surfaceTint = LightTokens.Primary,
    surfaceBright = LightTokens.Surface,
    surfaceDim = LightTokens.SurfaceHighest,

    surfaceContainerLowest = LightTokens.SurfaceLowest,
    surfaceContainerLow = LightTokens.SurfaceLow,
    surfaceContainer = LightTokens.Canvas,
    surfaceContainerHigh = LightTokens.SurfaceHighest,
    surfaceContainerHighest = LightTokens.SurfaceHighest,

    inverseSurface = LightTokens.Text,
    inverseOnSurface = LightTokens.Surface,

    outline = LightTokens.BorderStrong,
    outlineVariant = LightTokens.Border,
    scrim = Color.Black,
)

/**
 * Tema de la app: Nocturne, dark-first.
 *
 * **No usa color dinámico a propósito.** La plantilla original traía `dynamicColor = true`,
 * lo que en Android 12+ pintaba la app con el fondo de pantalla del custodio e impedía que
 * el diseño se viera nunca. Aquí el color lo define el sistema de diseño, no el dispositivo.
 *
 * **Tampoco sigue el tema del sistema.** El producto decidió que el aspecto por defecto es
 * oscuro; [oscuro] existe para que el conmutador de la pantalla de Ajustes lo controle
 * cuando esa pantalla exista (fase C4). Mientras no exista, la app se ve oscura siempre.
 */
@Composable
fun EchoTheme(
    oscuro: Boolean = true,
    content: @Composable () -> Unit,
) {
    val esquema = if (oscuro) EsquemaOscuro else EsquemaClaro
    val coloresEstado = if (oscuro) ColoresEstadoOscuro else ColoresEstadoClaro

    // Los iconos de la barra de estado y de navegación tienen que contrastar con el
    // lienzo, que cambia con el tema. Sin esto, en claro quedan blancos sobre blanco.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !oscuro
                isAppearanceLightNavigationBars = !oscuro
            }
        }
    }

    CompositionLocalProvider(LocalColoresEstado provides coloresEstado) {
        MaterialTheme(
            colorScheme = esquema,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
