package com.ai27.app.custodio.ui.ajustes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.aMensajeUsuario
import com.ai27.app.custodio.domain.model.Custodio
import com.ai27.app.custodio.domain.repository.MedioRepository
import com.ai27.app.custodio.domain.repository.SesionRepositorio
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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

data class AjustesUiState(
    val custodio: Custodio? = null,
    val pendientesSinSubir: Int = 0,
    val confirmacionCerrarSesion: Boolean = false,
    val cerrandoSesion: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AjustesViewModel @Inject constructor(
    sesionRepositorio: SesionRepositorio,
    medioRepository: MedioRepository,
) : ViewModel() {

    private val sesionRepositorio = sesionRepositorio
    private val interno = MutableStateFlow(AjustesUiState())

    private val custodioYPendientes = sesionRepositorio.observarCustodio()
        .flatMapLatest { custodio ->
            val uid = custodio?.uid ?: return@flatMapLatest flowOf(custodio to 0)
            medioRepository.observarPendientes(uid).map { custodio to it }
        }

    val uiState: StateFlow<AjustesUiState> = combine(
        custodioYPendientes,
        interno,
    ) { (custodio, pendientes), base ->
        base.copy(
            custodio = custodio,
            pendientesSinSubir = pendientes,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AjustesUiState(),
    )

    fun solicitarCerrarSesion() {
        if (interno.value.pendientesSinSubir > 0) return
        interno.update { it.copy(confirmacionCerrarSesion = true, error = null) }
    }

    fun cancelarCerrarSesion() {
        interno.update { it.copy(confirmacionCerrarSesion = false) }
    }

    fun confirmarCerrarSesion() {
        interno.update { it.copy(confirmacionCerrarSesion = false) }
        if (interno.value.cerrandoSesion) return
        viewModelScope.launch {
            interno.update { it.copy(cerrandoSesion = true, error = null) }
            when (val resultado = sesionRepositorio.cerrarSesion()) {
                is Result.Success -> interno.update { it.copy(cerrandoSesion = false) }
                is Result.Failure -> interno.update {
                    it.copy(cerrandoSesion = false, error = resultado.error.aMensajeUsuario())
                }
            }
        }
    }

    fun consumirError() {
        interno.update { it.copy(error = null) }
    }
}