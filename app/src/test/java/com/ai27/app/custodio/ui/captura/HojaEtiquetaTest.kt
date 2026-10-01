package com.ai27.app.custodio.ui.captura

// RETIRO_ETIQUETADO: TEST INERTE (comentado, no borrado).
//
// `hojaPuedeCerrarse` quedó comentado al inertizar HojaEtiqueta.kt (ver
// `docs/PLAN_RETIRO_ETIQUETADO.md`). Estos casos vuelven a tener sentido cuando se reactive
// el etiquetado; mientras tanto se comentan para no referenciar código inerte.
//
// import org.junit.Assert.assertFalse
// import org.junit.Assert.assertTrue
// import org.junit.Test
//
// class HojaEtiquetaTest {
//
//     @Test
//     fun `con catalogo sin situaciones la hoja es descartable sin seleccion`() {
//         // Sub-bug de U1: sin Situación en el catálogo, `tieneSituacion` no puede ser true
//         // nunca y el pin de salida quedaría atascado para siempre. Con idsSituacion vacío
//         // se debe poder cerrar aunque el medio quede sin clasificar.
//         assertTrue(hojaPuedeCerrarse(idsSituacion = emptySet(), tieneSituacion = false))
//     }
//
//     @Test
//     fun `con situaciones en el catalogo y sin seleccion la hoja no se cierra`() {
//         assertFalse(hojaPuedeCerrarse(idsSituacion = setOf("sin-novedad"), tieneSituacion = false))
//     }
//
//     @Test
//     fun `con una situacion elegida la hoja se cierra`() {
//         assertTrue(hojaPuedeCerrarse(idsSituacion = setOf("sin-novedad"), tieneSituacion = true))
//     }
// }
