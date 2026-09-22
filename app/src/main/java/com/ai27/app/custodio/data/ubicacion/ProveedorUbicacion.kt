package com.ai27.app.custodio.data.ubicacion

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
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
    private val context: Context,
    private val cliente: FusedLocationProviderClient,
) : ProveedorUbicacion {

    override suspend fun obtener(): Gps? {
        // Sin permiso, getCurrentLocation lanza SecurityException. Devolver null hace que
        // el medio quede sin GPS y la UI ofrezca el campo de ubicación por texto, que es
        // el respaldo previsto en PLAN §7, en lugar de tumbar la captura.
        if (!tienePermisoDeUbicacion()) return null

        return withTimeoutOrNull(LIMITE_FIJAR_POSICION_MS) {
            suspendCancellableCoroutine { cont ->
                val token = CancellationTokenSource()
                cont.invokeOnCancellation { token.cancel() }
                try {
                    cliente.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token)
                        .addOnCompleteListener { tarea ->
                            if (!cont.isActive) return@addOnCompleteListener
                            // tarea.result lanza RuntimeExecutionException si la Task falló,
                            // así que hay que preguntar por isSuccessful antes de leerlo.
                            cont.resume(if (tarea.isSuccessful) tarea.result?.aGps() else null)
                        }
                } catch (_: SecurityException) {
                    // El permiso se pudo revocar entre la comprobación y la llamada.
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }

    private fun tienePermisoDeUbicacion(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
}

private fun Location.aGps(): Gps = Gps(
    lat = latitude,
    lng = longitude,
    accuracyM = accuracy.toDouble(),
    ageMs = (SystemClock.elapsedRealtime() - elapsedRealtimeNanos / 1_000_000L).coerceAtLeast(0L),
)