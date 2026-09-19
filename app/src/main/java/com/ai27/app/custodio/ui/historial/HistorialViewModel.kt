package com.ai27.app.custodio.ui.historial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai27.app.custodio.domain.model.Reporte
import com.ai27.app.custodio.domain.repository.MedioRepository
import com.ai27.app.custodio.domain.repository.SesionRepositorio
import com.ai27.app.custodio.domain.usecase.ObtenerHistorial
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class HistorialViewModel @Inject constructor(
    sesionRepositorio: SesionRepositorio,
    medioRepository: MedioRepository,
    obtenerHistorial: ObtenerHistorial,
) : ViewModel() {

    private val filtro = MutableStateFlow("")

    private val historialYPendientes = sesionRepositorio.observarCustodio().flatMapLatest { custodio ->
        val uid = custodio?.uid ?: return@flatMapLatest flowOf(Pair(emptyList<Reporte>(), 0))
        combine(
            obtenerHistorial(uid),
            medioRepository.observarPendientes(uid),
        ) { historial, pendientes -> historial to pendientes }
    }

    val uiState: StateFlow<HistorialUiState> = combine(
        historialYPendientes,
        filtro,
    ) { (historial, pendientes), consulta ->
        HistorialUiState(
            reportesVisible = filtrarPorPlaca(historial, consulta),
            reportesTotales = historial.size,
            filtroPlaca = consulta,
            pendientesSinSubir = pendientes,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistorialUiState(),
    )

    fun onFiltroCambio(consulta: String) {
        filtro.value = consulta
    }
}