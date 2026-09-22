package com.ai27.app.custodio.ui.ajustes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai27.app.custodio.data.worker.SubidaScheduler
import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.model.Custodio
import com.ai27.app.custodio.domain.repository.MedioRepository
import com.ai27.app.custodio.domain.repository.SesionRepositorio
import com.ai27.app.custodio.ui.error.aRecurso
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
    val error: Int? = null,
)

@HiltViewModel
class AjustesViewModel @Inject constructor(
    private val sesionRepositorio: SesionRepositorio,
    medioRepository: MedioRepository,
    private val programadorSubidas: SubidaScheduler,
) : ViewModel() {

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
        // El conteo vive en el estado combinado, no en `interno`: leer `interno` hacía que
        // el guard valiera siempre 0, o sea, código muerto (hallazgo E7). Importa porque es
        // la invariante que impide dejar evidencia huérfana al rotar de custodio; que el
        // botón esté deshabilitado en la UI no basta como única defensa.
        if (uiState.value.pendientesSinSubir > 0) return
        interno.update { it.copy(confirmacionCerrarSesion = true, error = null) }
    }

    fun cancelarCerrarSesion() {
        interno.update { it.copy(confirmacionCerrarSesion = false) }
    }

    fun confirmarCerrarSesion() {
        interno.update { it.copy(confirmacionCerrarSesion = false) }
        if (interno.value.cerrandoSesion) return
        // Se revalida aquí porque entre solicitar y confirmar hay un diálogo de por medio,
        // y en ese hueco pueden entrar medios nuevos desde otra pestaña.
        if (uiState.value.pendientesSinSubir > 0) return
        viewModelScope.launch {
            interno.update { it.copy(cerrandoSesion = true, error = null) }
            // Cancelar ANTES del signOut (E6): un worker que siga corriendo con el token
            // del custodio saliente mientras entra el siguiente ve sus escrituras
            // denegadas por las reglas y deja la fila en FALLIDA. Lo que quede en
            // EN_PROGRESO lo rescata reconciliar() cuando ese custodio vuelva.
            programadorSubidas.cancelarTodo()
            when (val resultado = sesionRepositorio.cerrarSesion()) {
                is Result.Success -> interno.update { it.copy(cerrandoSesion = false) }
                is Result.Failure -> interno.update {
                    it.copy(cerrandoSesion = false, error = resultado.error.aRecurso())
                }
            }
        }
    }

    fun consumirError() {
        interno.update { it.copy(error = null) }
    }
}