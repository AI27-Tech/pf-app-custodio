package com.ai27.app.custodio.ui.historial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai27.app.custodio.domain.model.Reporte
import com.ai27.app.custodio.domain.repository.SesionRepositorio
import com.ai27.app.custodio.domain.usecase.ObtenerHistorial
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class HistorialViewModel @Inject constructor(
    sesionRepositorio: SesionRepositorio,
    private val obtenerHistorial: ObtenerHistorial,
) : ViewModel() {

    private val filtro = MutableStateFlow("")

    /**
     * Contador de reintentos: [onReintentar] lo incrementa para relanzar [obtenerHistorial]
     * tras un fallo. Hacerlo un `Int` en lugar de un booleano evita colisionar con la
     * emisión inicial y permite reintentar varias veces.
     */
    private val reintentos = MutableStateFlow(0)

    private val historial = sesionRepositorio.observarCustodio()
        .flatMapLatest { custodio ->
            val uid = custodio?.uid ?: return@flatMapLatest flowOf(emptyList<Reporte>())
            obtenerHistorial(uid)
        }

    private val historialConEstado = reintentos.flatMapLatest {
        historial
            .map { reportes -> HistorialConEstado(reportes = reportes) }
            .onStart { emit(HistorialConEstado(cargando = true)) }
            .catch { e ->
                emit(HistorialConEstado(error = e.message ?: "No se pudo cargar el historial."))
            }
    }

    val uiState: StateFlow<HistorialUiState> = combine(
        historialConEstado,
        filtro,
    ) { cargado, consulta ->
        HistorialUiState(
            reportesVisible = filtrarPorPlaca(cargado.reportes, consulta),
            reportesTotales = cargado.reportes.size,
            filtroPlaca = consulta,
            cargando = cargado.cargando,
            error = cargado.error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistorialUiState(cargando = true),
    )

    fun onFiltroCambio(consulta: String) {
        filtro.value = consulta
    }

    fun onReintentar() {
        reintentos.value += 1
    }
}

private data class HistorialConEstado(
    val reportes: List<Reporte> = emptyList(),
    val cargando: Boolean = false,
    val error: String? = null,
)
