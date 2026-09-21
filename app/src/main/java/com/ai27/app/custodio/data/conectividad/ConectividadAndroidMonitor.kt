package com.ai27.app.custodio.data.conectividad

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Monitor de conectividad real basado en [ConnectivityManager]. Registra el callback por
 * defecto de red ([ConnectivityManager.registerDefaultNetworkCallback]) y publica `true`
 * mientras la red tiene capacidad `INTERNET`; `false` cuando se pierde.
 *
 * El flujo arranca tan pronto como hay un suscriptor (a diferencia de un `stateIn` perezoso),
 * porque la franja offline del reporte debe pintarse desde el primer frame si el custodio
 * arrancó sin red.
 */
class ConectividadAndroidMonitor @Inject constructor(
    @ApplicationContext context: Context,
) : ConectividadMonitor {

    private val gestor = context.getSystemService(ConnectivityManager::class.java)

    override fun observadorConectividad(): Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(false)
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }

        val enLinea = gestor
            .getNetworkCapabilities(gestor.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            ?: false
        trySend(enLinea)

        gestor.registerDefaultNetworkCallback(callback)
        awaitClose { gestor.unregisterNetworkCallback(callback) }
    }
}
