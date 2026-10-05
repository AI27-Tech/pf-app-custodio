package com.ai27.app.custodio.data.mapa

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import com.ai27.app.custodio.data.diagnostico.RegistroDeFallos
import com.ai27.app.custodio.data.marcaDeAgua.BitmapImagenMapa
import com.ai27.app.custodio.data.marcaDeAgua.ImagenMapa
import com.ai27.app.custodio.domain.model.Gps
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Snapshot de mapa con el SDK de Google Maps, renderizado en un [MapView] **fuera de
 * pantalla** (nunca se adjunta a una ventana). El `MapView` tiene su propio ciclo de vida y
 * exige el hilo principal, así que todo el trabajo con él corre en [Dispatchers.Main].
 *
 * Degradación por diseño (requisito 3): cualquier fallo —sin API key, sin red para los
 * tiles, timeout o un snapshot en blanco— se traduce a `null`, no a una excepción. El punto
 * se marca con un pin para que el analista ubique la toma dentro del recuadro.
 */
class MapsSnapshotMapa(
    private val context: Context,
    private val registroDeFallos: RegistroDeFallos,
) : ProveedorSnapshotMapa {

    override suspend fun snapshot(gps: Gps, ladoPx: Int): ImagenMapa? = try {
        withTimeoutOrNull(TIEMPO_LIMITE_MS) {
            withContext(Dispatchers.Main) {
                renderizar(gps, ladoPx)?.let(::BitmapImagenMapa)
            }
        }
    } catch (causa: Throwable) {
        // El snapshot es accesorio: si revienta (p. ej. Maps no inicializa por falta de
        // Google Play Services), se registra y se degrada a null en vez de tumbar la
        // composición de la marca.
        registroDeFallos.registrar(CONTEXTO, causa)
        null
    }

    private suspend fun renderizar(gps: Gps, ladoPx: Int): Bitmap? {
        MapsInitializer.initialize(context)
        val opciones = GoogleMapOptions()
            .liteMode(true) // Modo lite: imagen estática, más barato y suficiente para un snapshot.
            .mapToolbarEnabled(false)
        val mapView = MapView(context, opciones)
        return try {
            mapView.onCreate(Bundle())
            mapView.onStart()
            mapView.onResume()
            // Medir y disponer el MapView al tamaño pedido, sin adjuntarlo a ninguna ventana.
            val medida = View.MeasureSpec.makeMeasureSpec(ladoPx, View.MeasureSpec.EXACTLY)
            mapView.measure(medida, medida)
            mapView.layout(0, 0, ladoPx, ladoPx)

            val mapa = obtenerMapa(mapView) ?: return null
            posicionar(mapa, gps)
            esperarCargaDeTiles(mapa)
            val bitmap = tomarSnapshot(mapa)
            bitmap?.takeUnless { esBitmapEnBlanco(it) }
        } finally {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    private suspend fun obtenerMapa(mapView: MapView): GoogleMap? =
        suspendCancellableCoroutine { cont ->
            mapView.getMapAsync { mapa -> if (cont.isActive) cont.resume(mapa) }
        }

    private fun posicionar(mapa: GoogleMap, gps: Gps) {
        val punto = LatLng(gps.lat, gps.lng)
        mapa.moveCamera(CameraUpdateFactory.newLatLngZoom(punto, ZOOM))
        mapa.addMarker(MarkerOptions().position(punto))
    }

    private suspend fun esperarCargaDeTiles(mapa: GoogleMap) {
        suspendCancellableCoroutine<Unit> { cont ->
            mapa.setOnMapLoadedCallback { if (cont.isActive) cont.resume(Unit) }
        }
    }

    private suspend fun tomarSnapshot(mapa: GoogleMap): Bitmap? =
        suspendCancellableCoroutine { cont ->
            mapa.snapshot { bitmap -> if (cont.isActive) cont.resume(bitmap) }
        }

    /**
     * Un snapshot "en blanco" (todos los píxeles del mismo color) aparece cuando los tiles
     * no cargaron —típico sin red—. Se muestrea una rejilla en vez de todo el bitmap para no
     * recorrer millones de píxeles. Si todas las muestras son idénticas, se trata como que
     * no hay mapa.
     */
    private fun esBitmapEnBlanco(bitmap: Bitmap): Boolean {
        val pasos = 8
        val primero = bitmap.getPixel(0, 0)
        for (i in 0 until pasos) {
            for (j in 0 until pasos) {
                val x = (bitmap.width - 1) * i / (pasos - 1)
                val y = (bitmap.height - 1) * j / (pasos - 1)
                if (bitmap.getPixel(x, y) != primero) return false
            }
        }
        return true
    }

    private companion object {
        const val TIEMPO_LIMITE_MS = 6_000L
        const val ZOOM = 16f
        const val CONTEXTO = "snapshot_mapa"
    }
}
