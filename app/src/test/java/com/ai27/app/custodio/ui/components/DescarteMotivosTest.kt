package com.ai27.app.custodio.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La lógica de selección de motivo del [DialogoDescarte] (U12) es una decisión pura:
 * con qué chip se marca cada `motivo` y cuándo se habilita "Descartar". El composable
 * solo traduce cargado a string.
 */
class DescarteMotivosTest {

    // Mismos valores semánticos que los chips reales; la comparación es por igualdad.
    private val motivos = listOf(
        "Placa equivocada",
        "Reporte cancelado por el cliente",
        "Duplicado",
        "Prueba / error",
        "Otro",
    )

    @Test
    fun `motivo en blanco es NINGUNO y no confirma`() {
        assertEquals(SeleccionDeMotivo.NINGUNO, seleccionDeMotivo("", motivos))
        assertEquals(SeleccionDeMotivo.NINGUNO, seleccionDeMotivo("   ", motivos))
        assertFalse(descarteConfirmable(""))
    }

    @Test
    fun `un chip predefinido exacto se marca como PREDEFINIDO`() {
        assertEquals(
            SeleccionDeMotivo.PREDEFINIDO,
            seleccionDeMotivo("Duplicado", motivos),
        )
        assertTrue(descarteConfirmable("Duplicado"))
    }

    @Test
    fun `texto libre sin ser ajeno va como OTRO`() {
        assertEquals(
            SeleccionDeMotivo.OTRO,
            seleccionDeMotivo("Placa registrada equivocada.", motivos),
        )
        assertTrue(descarteConfirmable("Placa registrada equivocada."))
    }

    @Test
    fun `el chip otro no colisiona con un texto libre identico al chip`() {
        // Si el texto libre escribiera justo "Otro", sigue siendo el chip PREDEFINIDO
        // (y por tanto sin campo), no un texto libre huérfano.
        assertEquals(SeleccionDeMotivo.PREDEFINIDO, seleccionDeMotivo("Otro", motivos))
    }
}