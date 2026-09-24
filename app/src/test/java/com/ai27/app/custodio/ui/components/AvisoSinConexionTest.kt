package com.ai27.app.custodio.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regresión U9: la franja de "sin conexión" se pinta solo ante un `false` explícito. En
 * particular, `null` (conectividad aún desconocida en el arranque) NO debe mostrarla;
 * antes, RootViewModel arrancaba con `conectado = true` e iniciaba la pantalla "sin
 * aviso" a ciegas.
 */
class AvisoSinConexionTest {

    @Test
    fun `conectado nulo o verdadero no muestra la franja`() {
        assertFalse(franjaSinConexionVisible(null))
        assertFalse(franjaSinConexionVisible(true))
    }

    @Test
    fun `conectado falso muestra la franja`() {
        assertTrue(franjaSinConexionVisible(false))
    }
}