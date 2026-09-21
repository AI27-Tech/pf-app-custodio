package com.ai27.app.custodio.ui.captura

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai27.app.custodio.R
import com.ai27.app.custodio.data.local.media.FotosPublicas
import com.ai27.app.custodio.data.local.media.Grabadora
import com.ai27.app.custodio.data.ubicacion.ProveedorUbicacion
import com.ai27.app.custodio.data.worker.SubidaScheduler
import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.model.Custodio
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Etiqueta
import com.ai27.app.custodio.domain.model.Gps
import com.ai27.app.custodio.domain.model.Limites
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    private val fotosPublicas: FotosPublicas,
    private val programadorSubidas: SubidaScheduler,
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

    init {
        viewModelScope.launch {
            interno.update { it.copy(etiquetas = obtenerEtiquetas()) }
            // Repone la cola de subida de la sesión anterior (espera red por sí sola).
            programadorSubidas.reconciliar()
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
            val gps = obtenerGpsLinear()
            val resultado = capturarMedio(
                custodioUid = custodio.uid,
                reporteId = reporte.id,
                tipo = TipoMedio.FOTO,
                origen = OrigenMedio.CAMARA,
                contenido = jpeg,
                miniatura = miniatura,
                gps = gps,
            )
            interno.update {
                when (resultado) {
                    is Result.Success -> {
                        copiarAPublica(resultado.value.id, jpeg)
                        it.copy(
                            capturando = false,
                            ubicacionTextoEscrita = "",
                            medioPendienteDeEtiquetar = resultado.value,
                        )
                    }
                    is Result.Failure -> it.copy(capturando = false, error = resultado.error.aRecurso())
                }
            }
        }
    }

    fun iniciarGrabacionDeAudio() {
        if (interno.value.grabandoAudio || interno.value.capturando) return
        if (uiState.value.reporte == null) return
        if (!grabadora.iniciar()) {
            interno.update { it.copy(error = R.string.error_grabacion_no_inicio) }
            return
        }
        interno.update { it.copy(grabandoAudio = true, segundosAudio = 0L) }
        jobTimerAudio = viewModelScope.launch {
            while (interno.value.grabandoAudio) {
                delay(1_000L)
                if (!interno.value.grabandoAudio) break
                val segundos = grabadora.duracionActualMs() / 1_000L
                interno.update { it.copy(segundosAudio = segundos) }
                if (grabadora.duracionActualMs() >= Limites.AUDIO_MAX_MS) {
                    detenerGrabacionDeAudio()
                }
            }
        }
    }

    fun detenerGrabacionDeAudio() {
        if (!interno.value.grabandoAudio) return
        jobTimerAudio?.cancel()
        jobTimerAudio = null
        interno.update { it.copy(grabandoAudio = false, segundosAudio = 0L) }
        val custodio = uiState.value.custodio
        val reporte = uiState.value.reporte
        if (custodio == null || reporte == null) {
            grabadora.descartar()
            return
        }
        viewModelScope.launch {
            val grabacion = grabadora.detener()
            if (grabacion == null) return@launch
            interno.update { it.copy(capturando = true) }
            val gps = obtenerGpsLinear()
            val resultado = capturarMedio(
                custodioUid = custodio.uid,
                reporteId = reporte.id,
                tipo = TipoMedio.AUDIO,
                origen = OrigenMedio.GRABADORA,
                contenido = grabacion.bytes,
                durationMs = grabacion.durationMs,
                gps = gps,
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
        }
    }

    fun descartarAudio() {
        jobTimerAudio?.cancel()
        jobTimerAudio = null
        interno.update { it.copy(grabandoAudio = false, segundosAudio = 0L) }
        grabadora.descartar()
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
                        programadorSubidas.encolarSubidaMedio(resultado.value.reporteId, medioId)
                        it.copy(
                            medioPendienteDeEtiquetar = null,
                            ubicacionTextoEscrita = "",
                            avisoEncolado = it.avisoEncolado + 1,
                        )
                    }
                    is Result.Failure -> it.copy(error = resultado.error.aRecurso())
                }
            }
        }
    }

    fun descartarEtiquetado() {
        interno.update { it.copy(medioPendienteDeEtiquetar = null, ubicacionTextoEscrita = "") }
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

    private suspend fun obtenerGpsLinear(): Gps? = try {
        withTimeoutOrNull(TIEMPO_OBTENER_UBICACION_MS) {
            proveedorUbicacion.obtener()
        }
    } catch (_: SecurityException) {
        null
    } catch (_: Exception) {
        null
    }

    private fun copiarAPublica(medioId: String, jpeg: ByteArray) {
        viewModelScope.launch {
            try {
                fotosPublicas.guardar(medioId, jpeg)
            } catch (error: Exception) {
                // La copia pública es accesoria: la evidencia vive igual en la app y,
                // una vez etiquetada, en Firebase. Se deja rastro en LogCat pero no se
                // bloquea la captura ni se marca error de usuario.
                Log.w(TAG, "No se pudo guardar la foto en Pictures/AppCustodio: ${error.message}")
            }
        }
    }

    private companion object {
        const val CLIENTE_PROVISIONAL = "cliente-provisional"
        const val TIEMPO_OBTENER_UBICACION_MS = 8_000L
        const val TAG = "CapturaViewModel"
    }
}