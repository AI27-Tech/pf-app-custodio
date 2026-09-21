package com.ai27.app.custodio.ui.historial

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai27.app.custodio.R
import com.ai27.app.custodio.domain.model.Etiqueta
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.Reporte
import com.ai27.app.custodio.domain.repository.MedioRepository
import com.ai27.app.custodio.domain.repository.ReporteRepository
import com.ai27.app.custodio.domain.usecase.ObtenerEtiquetas
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf

@HiltViewModel
class DetalleReporteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val reporteRepository: ReporteRepository,
    medioRepository: MedioRepository,
    obtenerEtiquetas: ObtenerEtiquetas,
) : ViewModel() {

    private val reporteId: String = checkNotNull(savedStateHandle["reporteId"]) {
        "Falta el reporteId en la ruta de detalle"
    }
    private val catalogoEtiquetas = MutableStateFlow<List<Etiqueta>>(emptyList())

    init {
        viewModelScope.launch {
            catalogoEtiquetas.value = obtenerEtiquetas()
        }
    }

    /** Contador de reintentos: cada incremento re-dispara la carga (falla → error → reintentar). */
    private val reintentos = MutableStateFlow(0)

    private val carga = reintentos.flatMapLatest { _ ->
        combine(
            reporteRepository.observarPorId(reporteId),
            medioRepository.observarDelReporte(reporteId),
            catalogoEtiquetas,
        ) { reporte, medios, etiquetas ->
            DetalleReporteUiState(
                reporte = reporte,
                medios = medios,
                etiquetas = etiquetas,
                cargando = false,
            )
        }.catch {
            emit(
                DetalleReporteUiState(
                    error = R.string.detalle_error_carga,
                ),
            )
        }
    }

    val uiState: StateFlow<DetalleReporteUiState> = carga
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DetalleReporteUiState(cargando = true),
        )

    fun onReintentar() {
        reintentos.value += 1
    }
}
