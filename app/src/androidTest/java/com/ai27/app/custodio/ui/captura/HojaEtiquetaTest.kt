package com.ai27.app.custodio.ui.captura

// RETIRO_ETIQUETADO: TEST INSTRUMENTADO INERTE (comentado, no borrado).
//
// `HojaEtiqueta` se inertizó al retirar el etiquetado del flujo (ver
// `docs/PLAN_RETIRO_ETIQUETADO.md`). Estas pruebas de Compose vuelven a tener sentido
// cuando se reactive la hoja; mientras tanto se comentan para no referenciar código inerte.
//
// import androidx.compose.ui.test.ExperimentalTestApi
// import androidx.compose.ui.test.assertCountEquals
// import androidx.compose.ui.test.assertIsNotEnabled
// import androidx.compose.ui.test.getBoundsInRoot
// import androidx.compose.ui.test.isSelected
// import androidx.compose.ui.test.junit4.createComposeRule
// import androidx.compose.ui.test.onNodeWithText
// import androidx.compose.ui.test.performClick
// import com.ai27.app.custodio.ui.previews.DatosDemo
// import com.ai27.app.custodio.ui.theme.EchoTheme
// import org.junit.Assert.assertEquals
// import org.junit.Assert.assertTrue
// import org.junit.Rule
// import org.junit.Test
//
// /**
//  * Regresión: al elegir la primera etiqueta de Situación, la hoja se "volvía a mostrar"
//  * (volvía a animar desde abajo). La causa: rememberModalBottomSheetState enruta el
//  * `confirmValueChange` por `rememberSaveable`; al construirse una lambda nueva en cada
//  * recomposición, el SheetState se regeneraba con valor inicial Hidden y ModalBottomSheet
//  * volvía a lanzar `show()`.
//  */
// @OptIn(ExperimentalTestApi::class)
// class HojaEtiquetaTest {
//
//     @get:Rule
//     val composeRule = createComposeRule()
//
//     @Test
//     fun elegirLaPrimeraSituacion_noVuelveAMostrarLaHoja() {
//         composeRule.setContent {
//             EchoTheme {
//                 HojaEtiqueta(
//                     estado = CapturaUiState(
//                         custodio = DatosDemo.custodio,
//                         reporte = DatosDemo.reporteActivo,
//                         medioPendienteDeEtiquetar = DatosDemo.mediosViaje.first(),
//                         etiquetas = DatosDemo.etiquetas,
//                     ),
//                     onGuardar = { _, _ -> },
//                     onUbicacionTextoCambio = {},
//                     onCerrar = {},
//                 )
//             }
//         }
//         composeRule.waitForIdle()
//
//         val chip = composeRule.onNodeWithText("Sin novedad")
//         val reposo = chip.getBoundsInRoot().top
//
//         composeRule.mainClock.autoAdvance = false
//         chip.performClick()
//         val desplazamientoMaximo = (0 until 8)
//             .map {
//                 composeRule.mainClock.advanceTimeByFrame()
//                 (chip.getBoundsInRoot().top - reposo).value
//             }
//             .maxOrNull() ?: 0f
//         composeRule.mainClock.autoAdvance = true
//         composeRule.waitForIdle()
//
//         assertTrue(
//             "la hoja no debe volver a entrar: su borde se desplazó ${desplazamientoMaximo}px",
//             desplazamientoMaximo < 1f,
//         )
//     }
//
//     @Test
//     fun laHojaAbreSinNingunChipSeleccionado() {
//         composeRule.setContent {
//             EchoTheme {
//                 HojaEtiqueta(
//                     estado = CapturaUiState(
//                         custodio = DatosDemo.custodio,
//                         reporte = DatosDemo.reporteActivo,
//                         medioPendienteDeEtiquetar = DatosDemo.mediosViaje.first(),
//                         etiquetas = DatosDemo.etiquetas,
//                     ),
//                     onGuardar = { _, _ -> },
//                     onUbicacionTextoCambio = {},
//                     onCerrar = {},
//                 )
//             }
//         }
//         composeRule.waitForIdle()
//
//         composeRule.onAllNodes(isSelected()).assertCountEquals(0)
//     }
//
//     @Test
//     fun guardarSoloAbreConLaSituacionQueElCustodioElija() {
//         val etiquetasGuardadas = mutableListOf<List<String>>()
//         composeRule.setContent {
//             EchoTheme {
//                 HojaEtiqueta(
//                     estado = CapturaUiState(
//                         custodio = DatosDemo.custodio,
//                         reporte = DatosDemo.reporteActivo,
//                         medioPendienteDeEtiquetar = DatosDemo.mediosViaje.first(),
//                         etiquetas = DatosDemo.etiquetas,
//                     ),
//                     onGuardar = { _, etiquetas -> etiquetasGuardadas += etiquetas },
//                     onUbicacionTextoCambio = {},
//                     onCerrar = {},
//                 )
//             }
//         }
//         composeRule.waitForIdle()
//
//         composeRule.onNodeWithText("Guardar").assertIsNotEnabled()
//         composeRule.onNodeWithText("Con novedad").performClick()
//         composeRule.waitForIdle()
//         composeRule.onNodeWithText("Accidente").performClick()
//         composeRule.waitForIdle()
//         composeRule.onNodeWithText("Guardar").performClick()
//
//         assertEquals(listOf("con-novedad", "accidente"), etiquetasGuardadas.single())
//     }
// }
