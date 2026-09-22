package com.ai27.app.custodio

import com.ai27.app.custodio.data.diagnostico.RegistroDeFallos
import com.ai27.app.custodio.data.local.media.EventoGrabacion
import com.ai27.app.custodio.data.local.media.Grabacion
import com.ai27.app.custodio.data.local.media.Grabadora
import com.ai27.app.custodio.data.ubicacion.ProveedorUbicacion
import com.ai27.app.custodio.data.worker.SubidaScheduler
import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
import com.ai27.app.custodio.domain.model.Custodio
import com.ai27.app.custodio.domain.model.Gps
import com.ai27.app.custodio.domain.repository.SesionRepositorio
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSesionRepositorio(custodio: Custodio?) : SesionRepositorio {
    private val flujo = MutableStateFlow(custodio)
    override fun observarCustodio(): Flow<Custodio?> = flujo
    override suspend fun iniciarSesion(correo: String, contraseña: String): Result<Custodio> =
        error("no usado en la prueba")

    /** Cierres de sesión efectuados, en orden, para poder afirmar qué pasó antes del signOut. */
    val cierres = mutableListOf<Unit>()

    /** Si se fija, `cerrarSesion` falla con este error en lugar de tener éxito. */
    var errorAlCerrar: DominioError? = null

    override suspend fun cerrarSesion(): Result<Unit> {
        errorAlCerrar?.let { return Result.Failure(it) }
        cierres.add(Unit)
        flujo.value = null
        return Result.Success(Unit)
    }
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

/**
 * Grabadora falsa controlable. [detener] devuelve [grabacionParaDetener] si se llamó
 * [iniciar] antes. Suficiente para la ruta de nota de voz de la ViewModel.
 */
class FakeGrabadora(
    var grabacionParaDetener: Grabacion? = Grabacion(ByteArray(64), durationMs = 3_000L),
    var duracionMs: Long = 3_000L,
) : Grabadora {
    private var activa = false
    private var enPausa = false
    override val grabando: Boolean get() = activa
    override val pausada: Boolean get() = enPausa

    private val flujoEventos = MutableSharedFlow<EventoGrabacion>(replay = 1, extraBufferCapacity = 4)
    override val eventos: Flow<EventoGrabacion> = flujoEventos

    /** Simula un fallo asíncrono del grabador, como `MEDIA_RECORDER_ERROR_SERVER_DIED`. */
    suspend fun emitirFallo(codigo: Int = 1) = flujoEventos.emit(EventoGrabacion.Fallo(codigo))

    /** Simula que el grabador llegó al tope de 5 minutos y se detuvo solo. */
    suspend fun emitirLimiteAlcanzado() = flujoEventos.emit(EventoGrabacion.LimiteAlcanzado)

    var puedeReanudar: Boolean = true
    var pausas = 0
    var reanudaciones = 0
    var descartes = 0

    /** Cuántas notas huérfanas "encuentra" el barrido de arranque. */
    var huerfanasEnCache: Int = 0

    override suspend fun iniciar(): Boolean {
        activa = true
        enPausa = false
        return true
    }

    override suspend fun pausar() {
        if (!activa || enPausa) return
        enPausa = true
        pausas++
    }

    override suspend fun reanudar(): Boolean {
        if (!activa || !enPausa) return false
        if (!puedeReanudar) return false
        enPausa = false
        reanudaciones++
        return true
    }

    override fun duracionActualMs(): Long = if (activa) duracionMs else 0L

    override suspend fun detener(): Grabacion? {
        activa = false
        enPausa = false
        return grabacionParaDetener
    }

    override suspend fun descartar() {
        activa = false
        enPausa = false
        descartes++
    }

    override suspend fun barrerNotasHuerfanas(): Int = huerfanasEnCache
}

/** Registra en orden las llamadas de encolado para poder afirmar el momento en que ocurren. */
class FakeSubidaScheduler : SubidaScheduler {
    val encolados = mutableListOf<Pair<String, String>>()
    val sincronizados = mutableListOf<String>()
    val reconciliadoPara = mutableListOf<String>()
    var cancelaciones = 0
    override fun encolarSubidaMedio(reporteId: String, medioId: String) {
        encolados.add(reporteId to medioId)
    }
    override fun sincronizarReporte(reporteId: String) {
        sincronizados.add(reporteId)
    }
    override suspend fun reconciliar(custodioUid: String) {
        reconciliadoPara.add(custodioUid)
    }
    override fun cancelarTodo() {
        cancelaciones++
    }
}


/**
 * Registro de fallos falso. Guarda lo registrado para poder afirmar que un fallo de
 * cámara sí se reportó (hallazgo E2), sin depender de Crashlytics en pruebas de JVM.
 */
class FakeRegistroDeFallos : RegistroDeFallos {
    val registrados = mutableListOf<Pair<String, Throwable>>()
    override fun registrar(contexto: String, causa: Throwable) {
        registrados.add(contexto to causa)
    }
}
