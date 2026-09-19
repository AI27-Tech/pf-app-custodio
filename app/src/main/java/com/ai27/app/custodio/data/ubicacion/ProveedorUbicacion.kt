package com.ai27.app.custodio.data.ubicacion

import android.location.Location
import com.ai27.app.custodio.domain.model.Gps
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import android.os.SystemClock
import kotlin.coroutines.resume

interface ProveedorUbicacion {
    suspend fun obtener(): Gps?
}

private const val LIMITE_FIJAR_POSICION_MS = 8_000L

class FusedProveedorUbicacion(
    private val cliente: FusedLocationProviderClient,
) : ProveedorUbicacion {

    override suspend fun obtener(): Gps? = withTimeoutOrNull(LIMITE_FIJAR_POSICION_MS) {
        suspendCancellableCoroutine { cont ->
            val token = CancellationTokenSource()
            cont.invokeOnCancellation { token.cancel() }
            cliente.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token)
                .addOnCompleteListener { tarea ->
                    if (!cont.isActive) return@addOnCompleteListener
                    cont.resume(tarea.result?.aGps())
                }
        }
    }
}

private fun Location.aGps(): Gps = Gps(
    lat = latitude,
    lng = longitude,
    accuracyM = accuracy.toDouble(),
    ageMs = (SystemClock.elapsedRealtime() - elapsedRealtimeNanos / 1_000_000L).coerceAtLeast(0L),
)