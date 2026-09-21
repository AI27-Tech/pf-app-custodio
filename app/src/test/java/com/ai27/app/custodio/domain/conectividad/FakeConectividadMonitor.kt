package com.ai27.app.custodio.domain.conectividad

import com.ai27.app.custodio.data.conectividad.ConectividadMonitor

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Fake controlado de [ConectividadMonitor] para tests: el test decide cuándo hay red.
 * `conectado` arranca en `true`; el test lo gira a voluntad.
 */
class FakeConectividadMonitor(
    inicial: Boolean = true,
) : ConectividadMonitor {

    private val canal = Channel<Boolean>(Channel.BUFFERED).also { it.trySend(inicial) }

    fun fijar(conectado: Boolean) {
        canal.trySend(conectado)
    }

    override fun observadorConectividad(): Flow<Boolean> = canal.receiveAsFlow()
}
