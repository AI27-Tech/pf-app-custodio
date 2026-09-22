package com.ai27.app.custodio.ui.captura

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai27.app.custodio.R
import com.ai27.app.custodio.data.diagnostico.RegistroDeFallos
import com.ai27.app.custodio.data.local.media.EventoGrabacion
import com.ai27.app.custodio.data.local.media.Grabadora
import com.ai27.app.custodio.data.ubicacion.ProveedorUbicacion
import com.ai27.app.custodio.data.worker.SubidaScheduler
import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.model.Custodio
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Etiqueta
import com.ai27.app.custodio.domain.model.Gps
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.OrigenMedio
import com.ai27.app.custodio.domain.model.Reporte
import com.ai27.app.custodio.domain.model.TipoMedio
import com.ai27.app.custodio.domain.repository.MedioRepository
import com.ai27.app.custodio.domain.repository.ReporteRepository
import com.ai27.app.custodio.domain.repository.SesionRepositorio
import com.ai27.app.custodio.domain.usecase.CapturarMedio
import com.ai27.app.custodio.domain.usecase.CerrarReporte
import com.ai27.app.custodio.domain.usecase.CrearReporte
import com.ai27.app.custodio.domain.usecase.DescartarReporte
import com.ai27.app.custodio.domain.usecase.EtiquetarMedio
import com.ai27.app.custodio.domain.usecase.ObtenerEtiquetas
import com.ai27.app.custodio.domain.usecase.ReintentarSubida
import com.ai27.app.custodio.ui.error.aRecurso
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@HiltViewModel
class CapturaViewModel @Inject constructor(
    private val reporteRepository: ReporteRepository,
    private val medioRepository: MedioRepository,
    private val sesionRepositorio: SesionRepositorio,
    private val crearReporte: CrearReporte,
    private val capturarMedio: CapturarMedio,
    private val etiquetarMedio: EtiquetarMedio,
    private val cerrarReporte: CerrarReporte,
    private val descartarReporte: DescartarReporte,
    private val obtenerEtiquetas: ObtenerEtiquetas,
    private val reintentarSubida: ReintentarSubida,
    private val proveedorUbicacion: ProveedorUbicacion,
    private val grabadora: Grabadora,
    private val programadorSubidas: SubidaScheduler,
    private val registroDeFallos: RegistroDeFallos,
    @Named("deviceId") private val deviceId: String,
) : ViewModel() {

    private val interno = MutableStateFlow(CapturaUiState())

    private val custodioFlow = sesionRepositorio.observarCustodio()

    private val reporteYMedios: kotlinx.coroutines.flow.Flow<Pair<Reporte?, List<Medio>>> =
        custodioFlow.flatMapLatest { custodio ->
            val uid = custodio?.uid ?: return@flatMapLatest flowOf(null to emptyList())
            reporteRepository.observarActivo(uid).flatMapLatest { reporte ->
                if (reporte == null) {
                    flowOf(null to emptyList())
                } else {
                    medioRepository.observarDelReporte(reporte.id).map { reporte to it }
                }
            }
        }

    private val custodioYPendientes: kotlinx.coroutines.flow.Flow<Pair<Custodio?, Int>> =
        custodioFlow.flatMapLatest { custodio ->
            if (custodio == null) {
                flowOf(null to 0)
            } else {
                medioRepository.observarPendientes(custodio.uid).map { custodio to it }
            }
        }

    val uiState: StateFlow<CapturaUiState> = combine(
        reporteYMedios,
        custodioYPendientes,
        interno,
    ) { (reporte, medios), (custodio, pendientes), base ->
        base.copy(
            custodio = custodio,
            reporte = reporte,
            medios = medios,
            pendientesSinSubir = pendientes,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CapturaUiState(),
    )

    private var jobTimerAudio: Job? = null

    // Trabajos en vuelo que resuelven el GPS de un medio recién capturado, por medioId.
    // guardarEtiqueta los espera antes de encolar la subida; se limpian al etiquetar o
    // descartar el etiquetado.
    private val trabajosGps = mutableMapOf<String, Job>()

    init {
        viewModelScope.launch {
            interno.update { it.copy(etiquetas = obtenerEtiquetas()) }
        }
        viewModelScope.launch {
            // La reconciliación es por custodio (E6), así que espera a que la sesión
            // resuelva antes de reponer la cola. Si no hay sesión no hay nada que reponer.
            val uid = custodioFlow.filterNotNull().first().uid
            programadorSubidas.reconciliar(uid)
        }
        viewModelScope.launch {
            // Un .m4a suelto en caché es la prueba de que el proceso murió a media nota.
            // `pause()` no escribe el índice del MPEG-4, así que ese audio es
            // irrecuperable: lo único honesto es borrarlo y decirlo (R3 de §13).
            if (grabadora.barrerNotasHuerfanas() > 0) {
                interno.update { it.copy(avisoNotaPerdida = true) }
            }
        }
        viewModelScope.launch {
            // Sin recoger esto, un MEDIA_RECORDER_ERROR_SERVER_DIED dejaba la nota en
            // silencio sin que nadie se enterara (hallazgo E5).
            grabadora.eventos.collect { evento ->
                when (evento) {
                    is EventoGrabacion.LimiteAlcanzado -> detenerGrabacionDeAudio()
                    is EventoGrabacion.Fallo -> falloDeGrabacion(evento.codigo)
                }
            }
        }
    }

    fun onPlacaCambio(placa: String) {
        interno.update { it.copy(placaEscrita = placa, error = null) }
    }

    fun iniciarViaje() {
        val custodio = uiState.value.custodio ?: return
        val placa = interno.value.placaEscrita
        if (placa.isBlank()) {
            interno.update { it.copy(error = R.string.error_escribe_placa) }
            return
        }
        viewModelScope.launch {
            interno.update { it.copy(creandoViaje = true, error = null) }
            when (val resultado = crearReporte(
                custodio = custodio,
                deviceId = deviceId,
                clienteId = CLIENTE_PROVISIONAL,
                placasCapturadas = placa,
            )) {
                is Result.Success -> {
                    interno.update { it.copy(creandoViaje = false, placaEscrita = "" ) }
                    programadorSubidas.sincronizarReporte(resultado.value.id)
                }
                is Result.Failure -> interno.update {
                    it.copy(creandoViaje = false, error = resultado.error.aRecurso())
                }
            }
        }
    }

    fun capturarFoto(jpeg: ByteArray, miniatura: ByteArray?) {
        val custodio = uiState.value.custodio
        val reporte = uiState.value.reporte
        if (custodio == null || reporte == null) return
        if (interno.value.capturando || interno.value.grabandoAudio) return
        if (jpeg.isEmpty()) return
        viewModelScope.launch {
            interno.update { it.copy(capturando = true) }
            // La foto se persiste de inmediato SIN esperar el arreglo de GPS: así la hoja
            // de etiquetado aparece al instante. La ubicación se adjunta en segundo plano
            // (ver adjuntarGpsEnSegundoPlano) y guardarEtiqueta la espera antes de encolar.
            val resultado = capturarMedio(
                custodioUid = custodio.uid,
                reporteId = reporte.id,
                tipo = TipoMedio.FOTO,
                origen = OrigenMedio.CAMARA,
                contenido = jpeg,
                miniatura = miniatura,
                gps = null,
            )
            interno.update {
                when (resultado) {
                    is Result.Success -> it.copy(
                        capturando = false,
                        ubicacionTextoEscrita = "",
                        medioPendienteDeEtiquetar = resultado.value,
                    )
                    is Result.Failure -> it.copy(capturando = false, error = resultado.error.aRecurso())
                }
            }
            if (resultado is Result.Success) {
                adjuntarGpsEnSegundoPlano(resultado.value.id)
            }
        }
    }

    fun iniciarGrabacionDeAudio() {
        if (interno.value.grabandoAudio || interno.value.capturando) return
        if (uiState.value.reporte == null) return
        viewModelScope.launch {
            if (!grabadora.iniciar()) {
                interno.update { it.copy(error = R.string.error_grabacion_no_inicio) }
                return@launch
            }
            interno.update { it.copy(grabandoAudio = true, audioPausado = false, segundosAudio = 0L) }
            arrancarCronometro()
        }
    }

    /**
     * Pausa la nota al salir de la app. La alternativa —seguir grabando en segundo plano—
     * exige un servicio en primer plano con notificación permanente, y dejarla corriendo
     * sin él acaba en audio en silencio cuando el sistema le quita el micrófono (E5).
     */
    fun pausarGrabacionDeAudio() {
        if (!interno.value.grabandoAudio || interno.value.audioPausado) return
        jobTimerAudio?.cancel()
        jobTimerAudio = null
        viewModelScope.launch {
            grabadora.pausar()
            interno.update {
                it.copy(audioPausado = true, segundosAudio = grabadora.duracionActualMs() / 1_000L)
            }
        }
    }

    fun reanudarGrabacionDeAudio() {
        if (!interno.value.grabandoAudio || !interno.value.audioPausado) return
        viewModelScope.launch {
            if (!grabadora.reanudar()) {
                interno.update { it.copy(error = R.string.error_grabacion_no_reanudo) }
                return@launch
            }
            interno.update { it.copy(audioPausado = false) }
            arrancarCronometro()
        }
    }

    private fun arrancarCronometro() {
        jobTimerAudio?.cancel()
        jobTimerAudio = viewModelScope.launch {
            while (interno.value.grabandoAudio && !interno.value.audioPausado) {
                delay(1_000L)
                if (!interno.value.grabandoAudio || interno.value.audioPausado) break
                interno.update { it.copy(segundosAudio = grabadora.duracionActualMs() / 1_000L) }
            }
        }
    }

    fun detenerGrabacionDeAudio() {
        if (!interno.value.grabandoAudio) return
        jobTimerAudio?.cancel()
        jobTimerAudio = null
        interno.update { it.copy(grabandoAudio = false, audioPausado = false, segundosAudio = 0L) }
        val custodio = uiState.value.custodio
        val reporte = uiState.value.reporte
        if (custodio == null || reporte == null) {
            viewModelScope.launch { grabadora.descartar() }
            return
        }
        viewModelScope.launch {
            val grabacion = grabadora.detener()
            if (grabacion == null) {
                // El grabador no devolvió audio usable (archivo vacío o duración cero).
                // Callarse aquí sería el mismo defecto que E5 vino a cerrar: la nota
                // desaparecería y el custodio se iría creyendo que la dejó grabada.
                interno.update { it.copy(error = R.string.error_grabacion_vacia) }
                return@launch
            }
            interno.update { it.copy(capturando = true) }
            // Igual que la foto: se persiste sin esperar el GPS y la ubicación se adjunta
            // en segundo plano; guardarEtiqueta la espera antes de encolar la subida.
            val resultado = capturarMedio(
                custodioUid = custodio.uid,
                reporteId = reporte.id,
                tipo = TipoMedio.AUDIO,
                origen = OrigenMedio.GRABADORA,
                contenido = grabacion.bytes,
                durationMs = grabacion.durationMs,
                gps = null,
            )
            interno.update {
                when (resultado) {
                    is Result.Success -> it.copy(
                        capturando = false,
                        ubicacionTextoEscrita = "",
                        medioPendienteDeEtiquetar = resultado.value,
                    )
                    is Result.Failure -> it.copy(capturando = false, error = resultado.error.aRecurso())
                }
            }
            if (resultado is Result.Success) {
                adjuntarGpsEnSegundoPlano(resultado.value.id)
            }
        }
    }

    fun descartarAudio() {
        jobTimerAudio?.cancel()
        jobTimerAudio = null
        interno.update { it.copy(grabandoAudio = false, audioPausado = false, segundosAudio = 0L) }
        viewModelScope.launch { grabadora.descartar() }
    }

    /**
     * El grabador falló por su cuenta. Se descarta la nota —un `.m4a` que nunca se cerró no
     * es audio, es basura— y se avisa, en vez de entregar silencio como evidencia (E5).
     */
    private fun falloDeGrabacion(codigo: Int) {
        jobTimerAudio?.cancel()
        jobTimerAudio = null
        interno.update {
            it.copy(
                grabandoAudio = false,
                audioPausado = false,
                segundosAudio = 0L,
                error = R.string.error_grabacion_fallo,
            )
        }
        registroDeFallos.registrar(CONTEXTO_GRABACION, IllegalStateException("MediaRecorder error $codigo"))
        viewModelScope.launch { grabadora.descartar() }
    }

    fun consumirAvisoNotaPerdida() {
        interno.update { it.copy(avisoNotaPerdida = false) }
    }

    /**
     * Suelta el micrófono al salir del flujo de viaje. `Grabadora` es `@Singleton`, así que
     * sin esto el `MediaRecorder` se quedaba tomado y la siguiente grabación —o cualquier
     * otra app— fallaba en silencio (E5).
     *
     * Se usa un scope propio porque `viewModelScope` ya está cancelado aquí. La nota a
     * medias se pierde a propósito: es la última salida, y para entonces el custodio ya
     * abandonó la pantalla de audio con la nota pausada.
     */
    override fun onCleared() {
        // Sin `super.onCleared()`: el de ViewModel está vacío y lint lo marca como llamada
        // superflua. Si alguna versión de AndroidX le añade comportamiento, lint avisará de
        // que falta.
        CoroutineScope(Dispatchers.IO).launch { grabadora.descartar() }
    }

    fun guardarEtiqueta(medioId: String, etiquetaId: String) {
        if (etiquetaId.isBlank()) {
            interno.update { it.copy(error = R.string.error_etiqueta_obligatoria) }
            return
        }
        viewModelScope.launch {
            val resultado = etiquetarMedio(
                medioId = medioId,
                etiquetaId = etiquetaId,
                ubicacionTexto = interno.value.ubicacionTextoEscrita.trim().ifBlank { null },
            )
            interno.update {
                when (resultado) {
                    is Result.Success -> {
                        it.copy(
                            medioPendienteDeEtiquetar = null,
                            ubicacionTextoEscrita = "",
                            avisoEncolado = it.avisoEncolado + 1,
                        )
                    }
                    is Result.Failure -> it.copy(error = resultado.error.aRecurso())
                }
            }
            if (resultado is Result.Success) {
                // La UI ya se actualizó (hoja cerrada + aviso). Antes de encolar la subida
                // se espera a que termine de adjuntarse el GPS en segundo plano (tiene su
                // propio timeout, así que no cuelga) para que el documento remoto conserve
                // la ubicación, como cuando la captura la esperaba en línea.
                trabajosGps.remove(medioId)?.join()
                programadorSubidas.encolarSubidaMedio(resultado.value.reporteId, medioId)
            }
        }
    }

    fun descartarEtiquetado() {
        // El medio queda persistido y pendiente aunque se descarte el etiquetado; el
        // trabajo de GPS puede seguir y adjuntar la ubicación. Solo se suelta la referencia
        // del mapa para que no crezca sin límite. Desde §13 ese medio NO se sube hasta que
        // se etiquete, y la rejilla de Archivos lo señala para poder resolverlo
        // (ver retomarEtiquetado).
        interno.value.medioPendienteDeEtiquetar?.let { trabajosGps.remove(it.id) }
        interno.update { it.copy(medioPendienteDeEtiquetar = null, ubicacionTextoEscrita = "") }
    }

    /**
     * Reabre la hoja de etiquetado de un medio que ya está guardado sin etiqueta
     * (hallazgo E3). Es la única salida para la evidencia que quedó sin clasificar, porque
     * `medioPendienteDeEtiquetar` vive en memoria y no sobrevive a la muerte del proceso.
     *
     * Se invoca desde la rejilla de Archivos. Un medio ya etiquetado se ignora: reetiquetar
     * no está en el alcance y la subida pudo haber salido ya.
     */
    fun retomarEtiquetado(medioId: String) {
        val medio = uiState.value.medios.firstOrNull { it.id == medioId } ?: return
        if (medio.etiqueta != null) return
        interno.update { it.copy(medioPendienteDeEtiquetar = medio, ubicacionTextoEscrita = "") }
    }

    fun onUbicacionTextoCambio(texto: String) {
        interno.update { it.copy(ubicacionTextoEscrita = texto) }
    }

    fun borrarPendiente(medioId: String) {
        val medio = uiState.value.medios.firstOrNull { it.id == medioId } ?: return
        if (medio.estadoSubida != EstadoSubida.PENDIENTE) return
        viewModelScope.launch {
            val resultado = medioRepository.borrarPendiente(medioId)
            if (resultado is Result.Failure) {
                interno.update { it.copy(error = resultado.error.aRecurso()) }
            }
        }
    }

    fun reintentar(medioId: String) {
        val medio = uiState.value.medios.firstOrNull { it.id == medioId }
            ?: return
        if (medio.estadoSubida != EstadoSubida.FALLIDA) return
        viewModelScope.launch {
            val resultado = reintentarSubida(medioId)
            when (resultado) {
                is Result.Success -> programadorSubidas.encolarSubidaMedio(medio.reporteId, medioId)
                is Result.Failure -> interno.update { it.copy(error = resultado.error.aRecurso()) }
            }
        }
    }

    fun cerrarViaje(comentario: String?, hayNovedad: Boolean) {
        val reporte = uiState.value.reporte ?: return
        if (interno.value.cerrandoViaje) return
        viewModelScope.launch {
            interno.update { it.copy(cerrandoViaje = true, error = null) }
            val resultado = cerrarReporte(
                reporteId = reporte.id,
                comentario = comentario?.trim()?.ifEmpty { null },
                hayNovedad = hayNovedad,
            )
            interno.update {
                when (resultado) {
                    is Result.Success -> {
                        programadorSubidas.sincronizarReporte(resultado.value.id)
                        it.copy(cerrandoViaje = false)
                    }
                    is Result.Failure -> it.copy(cerrandoViaje = false, error = resultado.error.aRecurso())
                }
            }
        }
    }

    fun descartarViaje(motivo: String) {
        val reporte = uiState.value.reporte ?: return
        if (interno.value.descartandoViaje) return
        viewModelScope.launch {
            interno.update { it.copy(descartandoViaje = true, error = null) }
            val resultado = descartarReporte(reporte.id, motivo.trim())
            interno.update {
                when (resultado) {
                    is Result.Success -> {
                        programadorSubidas.sincronizarReporte(resultado.value.id)
                        it.copy(descartandoViaje = false)
                    }
                    is Result.Failure -> it.copy(descartandoViaje = false, error = resultado.error.aRecurso())
                }
            }
        }
    }

    fun consumirError() {
        interno.update { it.copy(error = null) }
    }

    /**
     * Un fallo de cámara o de procesamiento de imagen (hallazgo E2). Antes se perdía en
     * un `catch` vacío: el custodio tocaba, no pasaba nada, y no quedaba rastro para
     * diagnosticarlo. Ahora se le dice, y la causa va a Crashlytics como no fatal.
     */
    fun fallaDeCaptura(causa: Throwable) {
        registroDeFallos.registrar(CONTEXTO_CAPTURA_FOTO, causa)
        interno.update { it.copy(capturando = false, error = R.string.error_captura_fallo) }
    }

    private suspend fun obtenerGpsLinear(): Gps? = try {
        withTimeoutOrNull(TIEMPO_OBTENER_UBICACION_MS) {
            proveedorUbicacion.obtener()
        }
    } catch (_: SecurityException) {
        null
    } catch (_: Exception) {
        null
    }

    // Resuelve el GPS del medio en segundo plano y lo adjunta a la fila de Room. No
    // bloquea la captura: la hoja de etiquetado ya se mostró. actualizarGps solo pega la
    // ubicación mientras el medio sigue PENDIENTE, así que llegar tarde es inofensivo.
    private fun adjuntarGpsEnSegundoPlano(medioId: String) {
        trabajosGps[medioId] = viewModelScope.launch {
            val gps = obtenerGpsLinear() ?: return@launch
            medioRepository.actualizarGps(medioId, gps)
        }
    }

    private companion object {
        const val CLIENTE_PROVISIONAL = "cliente-provisional"
        const val TIEMPO_OBTENER_UBICACION_MS = 8_000L
        const val CONTEXTO_CAPTURA_FOTO = "captura_foto"
        const val CONTEXTO_GRABACION = "grabacion_audio"
    }
}