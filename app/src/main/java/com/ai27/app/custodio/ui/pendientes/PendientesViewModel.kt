package com.ai27.app.custodio.ui.pendientes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai27.app.custodio.data.worker.SubidaScheduler
import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.repository.MedioRepository
import com.ai27.app.custodio.domain.repository.SesionRepositorio
import com.ai27.app.custodio.domain.usecase.ReintentarSubida
import com.ai27.app.custodio.ui.error.aRecurso
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PendientesViewModel @Inject constructor(
    private val sesionRepositorio: SesionRepositorio,
    private val medioRepository: MedioRepository,
    private val reintentarSubida: ReintentarSubida,
    private val programadorSubidas: SubidaScheduler,
) : ViewModel() {

    private val _error = MutableStateFlow<Int?>(null)
    private val _refrescando = MutableStateFlow(false)

    val pendientes: StateFlow<List<Medio>> = sesionRepositorio.observarCustodio()
        .flatMapLatest { custodio ->
            if (custodio == null) {
                flowOf(emptyList())
            } else {
                medioRepository.observarPendientesMedios(custodio.uid)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val error: StateFlow<Int?> = _error

    val refrescando: StateFlow<Boolean> = _refrescando

    fun recargar() {
        viewModelScope.launch {
            // La cola es por custodio (E6): sin sesión no hay nada que reponer.
            val uid = sesionRepositorio.observarCustodio().first()?.uid ?: return@launch
            _refrescando.value = true
            try {
                programadorSubidas.reconciliar(uid)
            } finally {
                _refrescando.value = false
            }
        }
    }

    fun reintentar(medio: Medio) {
        viewModelScope.launch {
            when (val resultado = reintentarSubida(medio.id)) {
                is Result.Success -> programadorSubidas.encolarSubidaMedio(medio.reporteId, medio.id)
                is Result.Failure -> _error.update { resultado.error.aRecurso() }
            }
        }
    }

    fun consumirError() {
        _error.update { null }
    }
}