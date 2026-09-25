package com.ai27.app.custodio.ui.captura

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ai27.app.custodio.ui.previews.DatosDemo
import com.ai27.app.custodio.ui.theme.EchoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Regresión: al elegir la primera etiqueta de Situación, la hoja se "volvía a mostrar"
 * (volvía a animar desde abajo). La causa: [androidx.compose.material3.rememberModalBottomSheetState]
 * enruta el `confirmValueChange` por `rememberSaveable`; al construirse una lambda nueva en cada
 * recomposición, el SheetState se regeneraba con valor inicial Hidden y ModalBottomSheet volvía
 * a lanzar `show()`.
 */
@OptIn(ExperimentalTestApi::class)
class HojaEtiquetaTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun elegirLaPrimeraSituacion_noVuelveAMostrarLaHoja() {
        composeRule.setContent {
            EchoTheme {
                HojaEtiqueta(
                    estado = CapturaUiState(
                        custodio = DatosDemo.custodio,
                        reporte = DatosDemo.reporteActivo,
                        medioPendienteDeEtiquetar = DatosDemo.mediosViaje.first(),
                        etiquetas = DatosDemo.etiquetas,
                    ),
                    onGuardar = { _, _ -> },
                    onUbicacionTextoCambio = {},
                    onCerrar = {},
                )
            }
        }
        composeRule.waitForIdle()

        val chip = composeRule.onNodeWithText("Sin novedad")
        val reposo = chip.getBoundsInRoot().top

        // Congelamos el reloj para observar la hoja *durante* el re-show (el bug la animaba
        // de vuelta desde abajo; tras dejarla reposar, ambos casos se verían idénticos).
        composeRule.mainClock.autoAdvance = false
        chip.performClick()
        val desplazamientoMaximo = (0 until 8)
            .map {
                composeRule.mainClock.advanceTimeByFrame()
                (chip.getBoundsInRoot().top - reposo).value
            }
            .maxOrNull() ?: 0f
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        assertTrue(
            "la hoja no debe volver a entrar: su borde se desplazó ${desplazamientoMaximo}px",
            desplazamientoMaximo < 1f,
        )
    }
}