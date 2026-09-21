package com.ai27.app.custodio

import com.ai27.app.custodio.data.local.media.FotosPublicas
import com.ai27.app.custodio.data.local.media.Grabacion
import com.ai27.app.custodio.data.local.media.Grabadora
import com.ai27.app.custodio.data.ubicacion.ProveedorUbicacion
import com.ai27.app.custodio.data.worker.SubidaScheduler
import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.model.Custodio
import com.ai27.app.custodio.domain.model.Gps
import com.ai27.app.custodio.domain.repository.SesionRepositorio
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSesionRepositorio(custodio: Custodio?) : SesionRepositorio {
    private val flujo = MutableStateFlow(custodio)
    override fun observarCustodio(): Flow<Custodio?> = flujo
    override suspend fun iniciarSesion(correo: String, contraseña: String): Result<Custodio> =
        error("no usado en la prueba")
    override suspend fun cerrarSesion(): Result<Unit> = error("no usado en la prueba")
}

/**
 * Proveedor de ubicación controlable: `obtener()` se suspende hasta que la prueba llama
 * a [completar] (o [completarSinFix]). Permite verificar que la captura no bloquea la UI
 * y que la subida solo se encola cuando el GPS ya se adjuntó.
 */
class FakeProveedorUbicacion : ProveedorUbicacion {
    private val puerta = CompletableDeferred<Gps?>()
    override suspend fun obtener(): Gps? = puerta.await()
    fun completar(gps: Gps) {
        puerta.complete(gps)
    }
    fun completarSinFix() {
        puerta.complete(null)
    }
}

class FakeFotosPublicas : FotosPublicas {
    val guardados = mutableListOf<String>()
    override suspend fun guardar(nombre: String, jpeg: ByteArray) {
        guardados.add(nombre)
    }
}

/**
 * Grabadora falsa controlable. [detener] devuelve [grabacionParaDetener] si se llamó
 * [iniciar] antes. Suficiente para la ruta de nota de voz de la ViewModel.
 */
class FakeGrabadora(
    var grabacionParaDetener: Grabacion? = Grabacion(ByteArray(64), durationMs = 3_000L),
    var duracionMs: Long = 3_000L,
) : Grabadora {
    private var activa = false
    override val grabando: Boolean get() = activa
    override fun iniciar(): Boolean {
        activa = true
        return true
    }
    override fun duracionActualMs(): Long = if (activa) duracionMs else 0L
    override fun detener(): Grabacion? {
        activa = false
        return grabacionParaDetener
    }
    override fun descartar() {
        activa = false
    }
}

/** Registra en orden las llamadas de encolado para poder afirmar el momento en que ocurren. */
class FakeSubidaScheduler : SubidaScheduler {
    val encolados = mutableListOf<Pair<String, String>>()
    val sincronizados = mutableListOf<String>()
    var reconciliaciones = 0
    override fun encolarSubidaMedio(reporteId: String, medioId: String) {
        encolados.add(reporteId to medioId)
    }
    override fun sincronizarReporte(reporteId: String) {
        sincronizados.add(reporteId)
    }
    override suspend fun reconciliar() {
        reconciliaciones++
    }
}
