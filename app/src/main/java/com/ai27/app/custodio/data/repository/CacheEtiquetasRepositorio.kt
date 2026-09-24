package com.ai27.app.custodio.data.repository

import com.ai27.app.custodio.data.local.CatalogoEtiquetasAlmacen
import com.ai27.app.custodio.domain.model.Etiqueta
import com.ai27.app.custodio.domain.repository.EtiquetaRepository
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Catálogo de etiquetas con copia local (A4): se servía siempre con un `get()` de
 * Firestore que, con red, espera al servidor — los segundos que la `HojaEtiqueta` abría
 * sin chips. Ahora se lee en este orden:
 *
 * 1. **Memoria** (tras el calentamiento al abrir la app): cero I/O.
 * 2. **Copia persistente** (`CatalogoEtiquetasAlmacen`): se devuelve al instante y se
 *    refresca desde el remoto UNA sola vez por proceso, en segundo plano.
 * 3. **Remoto**: solo la primera vez del primer arranque (sin copia previa); el repo
 *    remoto ya cae al respaldo empaquetado si la red falla.
 *
 * Los fallos de lectura/escritura local se tragan a propósito: una prefs defectuosa no
 * puede impedir etiquetar ni subir evidencia.
 */
class CacheEtiquetasRepositorio(
    private val remoto: EtiquetaRepository,
    private val almacen: CatalogoEtiquetasAlmacen,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : EtiquetaRepository {

    private val mutex = Mutex()
    private val refrescando = AtomicBoolean(false)

    @Volatile
    private var memoria: List<Etiqueta>? = null

    override suspend fun obtener(): List<Etiqueta> {
        memoria?.let { return it }
        return mutex.withLock {
            memoria?.let { return it }
            val guardado = leerDeAlmacen()
            if (guardado != null) {
                memoria = guardado
                refrescarUnaVezDesdeRemoto()
                guardado
            } else {
                cargarDesdeRemoto()
            }
        }
    }

    private suspend fun cargarDesdeRemoto(): List<Etiqueta> {
        val etiquetas = remoto.obtener()
        memoria = etiquetas
        guardar(etiquetas)
        return etiquetas
    }

    // El catálogo es estable el día a día: una sola lectura remota por apertura basta para
    // propagar cambios (p. ej. los valores reales de R4) sin bloquear a nadie.
    private fun refrescarUnaVezDesdeRemoto() {
        if (!refrescando.compareAndSet(false, true)) return
        scope.launch {
            try {
                val etiquetas = remoto.obtener()
                memoria = etiquetas
                guardar(etiquetas)
            } finally {
                refrescando.set(false)
            }
        }
    }

    private fun guardar(etiquetas: List<Etiqueta>) {
        try {
            almacen.guardar(etiquetas)
        } catch (_: Exception) {
            // Se ignora: la copia local es una optimización, no un requisito.
        }
    }

    private fun leerDeAlmacen(): List<Etiqueta>? = try {
        almacen.leer()
    } catch (_: Exception) {
        null
    }
}