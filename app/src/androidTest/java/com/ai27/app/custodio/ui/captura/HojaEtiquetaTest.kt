package com.ai27.app.custodio.ui.captura

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ai27.app.custodio.ui.previews.DatosDemo
import com.ai27.app.custodio.ui.theme.EchoTheme
import org.junit.Assert.assertEquals
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

    @Test
    fun laHojaAbreSinNingunChipSeleccionado() {
        // Regresión: la hoja arrastraba la última Situación confirmada (D-B1/U2), así que
        // bastaba tocar "Guardar" para clasificar la foto siguiente con la del medio anterior.
        // Cada medio se clasifica desde cero: ningún chip arranca seleccionado.
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

        composeRule.onAllNodes(isSelected()).assertCountEquals(0)
    }

    @Test
    fun guardarSoloAbreConLaSituacionQueElCustodioElija() {
        // La contrapartida de abrir vacío: "Guardar" sigue bloqueado hasta que haya una
        // Situación elegida, y entonces manda exactamente las que el custodio tocó.
        val etiquetasGuardadas = mutableListOf<List<String>>()
        composeRule.setContent {
            EchoTheme {
                HojaEtiqueta(
                    estado = CapturaUiState(
                        custodio = DatosDemo.custodio,
                        reporte = DatosDemo.reporteActivo,
                        medioPendienteDeEtiquetar = DatosDemo.mediosViaje.first(),
                        etiquetas = DatosDemo.etiquetas,
                    ),
                    onGuardar = { _, etiquetas -> etiquetasGuardadas += etiquetas },
                    onUbicacionTextoCambio = {},
                    onCerrar = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Guardar").assertIsNotEnabled()
        composeRule.onNodeWithText("Con novedad").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Accidente").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Guardar").performClick()

        assertEquals(listOf("con-novedad", "accidente"), etiquetasGuardadas.single())
    }
}