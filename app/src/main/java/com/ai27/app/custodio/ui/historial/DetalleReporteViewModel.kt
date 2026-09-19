package com.ai27.app.custodio.ui.historial

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai27.app.custodio.domain.model.Etiqueta
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.Reporte
import com.ai27.app.custodio.domain.repository.MedioRepository
import com.ai27.app.custodio.domain.repository.ReporteRepository
import com.ai27.app.custodio.domain.usecase.ObtenerEtiquetas
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DetalleReporteUiState(
    val reporte: Reporte? = null,
    val medios: List<Medio> = emptyList(),
    val etiquetas: List<Etiqueta> = emptyList(),
)

@HiltViewModel
class DetalleReporteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    reporteRepository: ReporteRepository,
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

    val uiState: StateFlow<DetalleReporteUiState> = combine(
        reporteRepository.observarPorId(reporteId),
        medioRepository.observarDelReporte(reporteId),
        catalogoEtiquetas,
    ) { reporte, medios, etiquetas ->
        DetalleReporteUiState(
            reporte = reporte,
            medios = medios,
            etiquetas = etiquetas,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DetalleReporteUiState(),
    )
}