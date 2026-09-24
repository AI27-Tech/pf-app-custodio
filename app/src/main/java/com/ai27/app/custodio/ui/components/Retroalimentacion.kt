package com.ai27.app.custodio.ui.components

import android.media.MediaActionSound
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView

/**
 * Canal de confirmación no visual (U3): háptica + sonido para el disparo de cámara y para
 * el encolado de una subida. La auditoría encontró CERO `performHapticFeedback`/`Vibrator`/
 * `MediaActionSound` en todo `app/src/main`; a pleno sol una píldora verde no basta y entre
 * el toque y cualquier confirmación visual pasan cientos de ms con la pantalla igual.
 *
 * Se dispara desde la UI y NO desde el ViewModel: necesita `View`/contexto. Ninguno de los
 * canales pide permiso (`performHapticFeedback` y `MediaActionSound`/`playSoundEffect` no
 * los requieren). Es efecto de plataforma: se verifica a mano (PENDIENTE-HW), el único
 * punto JVM-testable es el incremento de `avisoEncolado` que dispara el tic.
 */
class Retroalimentacion(
    private val vista: View,
) {
    // @Preview (modo de inspección) no tiene sonido ni vibración reales; el guard los apaga
    // y evita crear el recurso de `MediaActionSound` que luego habría que liberar.
    private val activa = !vista.isInEditMode

    private val gestos: MediaActionSound? = if (activa) MediaActionSound() else null

    init {
        gestos?.load(MediaActionSound.SHUTTER_CLICK)
    }

    /** Disparo de foto: háptica corta + obturador. Confirmación en el instante del toque. */
    fun disparo() {
        if (!activa) return
        vista.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        gestos?.play(MediaActionSound.SHUTTER_CLICK)
    }

    /** El archivo quedó encolado para subida: un tic corto. */
    fun encolado() {
        if (!activa) return
        vista.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        vista.playSoundEffect(SoundEffectConstants.CLICK)
    }

    /** Suelta el recurso de sonido cuando la pantalla sale de composición. */
    fun liberar() {
        gestos?.release()
    }
}

/**
 * Instancia única de [Retroalimentacion] para una pantalla, liberada al salir de
 * composición. Los `@Preview` también la crean: el guard interno de modo de edición la
 * deja inerte.
 */
@Composable
fun recordarRetroalimentacion(): Retroalimentacion {
    val vista = LocalView.current
    val retro = remember { Retroalimentacion(vista) }
    DisposableEffect(retro) {
        onDispose { retro.liberar() }
    }
    return retro
}

/**
 * Tic de encolado: cada vez que [avisoEncolado] crece —acaba de confirmarse una etiqueta y
 * la subida se encoló— el canal no visual lo confirma. El primer valor de composición no
 * cuenta, y se usa [remember] (no `rememberSaveable`) para no ticar al volver a la pantalla.
 */
@Composable
fun TicDeEncolado(
    avisoEncolado: Int,
    retroalimentacion: Retroalimentacion,
) {
    var ultimoEvento by remember { mutableStateOf(avisoEncolado) }
    LaunchedEffect(avisoEncolado) {
        if (avisoEncolado > 0 && avisoEncolado != ultimoEvento) {
            ultimoEvento = avisoEncolado
            retroalimentacion.encolado()
        }
    }
}