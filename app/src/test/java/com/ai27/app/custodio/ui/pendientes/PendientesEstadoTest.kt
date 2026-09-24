package com.ai27.app.custodio.ui.pendientes

import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.OrigenMedio
import com.ai27.app.custodio.domain.model.TipoMedio
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * El estado que la cola de pendientes muestra es una decisión pura (U4): la función decide
 * qué dice el renglón, el composable solo traduce a string.
 */
class PendientesEstadoTest {

    private fun medio(
        estado: EstadoSubida,
        etiquetas: List<String> = listOf("sin-novedad"),
    ): Medio = Medio(
        id = "m1",
        reporteId = "r1",
        tipo = TipoMedio.FOTO,
        origen = OrigenMedio.CAMARA,
        createdAtDevice = Instant.parse("2026-09-21T15:00:00Z"),
        contentType = "image/jpeg",
        estadoSubida = estado,
        etiquetas = etiquetas,
    )

    @Test
    fun `sin etiquetas el estado es SIN_ETIQUETA aunque el medio este FALLIDA`() {
        assertEquals(
            EstadoPendiente.SIN_ETIQUETA,
            estadoPendienteDe(medio(EstadoSubida.FALLIDA, etiquetas = emptyList())),
        )
    }

    @Test
    fun `un medio PENDIENTE etiquetado esta EN_COLA`() {
        assertEquals(EstadoPendiente.EN_COLA, estadoPendienteDe(medio(EstadoSubida.PENDIENTE)))
    }

    @Test
    fun `un medio EN_PROGRESO se muestra como ENVIANDO`() {
        assertEquals(EstadoPendiente.ENVIANDO, estadoPendienteDe(medio(EstadoSubida.EN_PROGRESO)))
    }

    @Test
    fun `un medio FALLIDA etiquetado se muestra como FALLIDA`() {
        assertEquals(EstadoPendiente.FALLIDA, estadoPendienteDe(medio(EstadoSubida.FALLIDA)))
    }
}