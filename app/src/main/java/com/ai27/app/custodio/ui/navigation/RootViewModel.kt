package com.ai27.app.custodio.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai27.app.custodio.domain.model.Custodio
import com.ai27.app.custodio.data.conectividad.ConectividadMonitor
import com.ai27.app.custodio.domain.repository.SesionRepositorio
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class EstadoSesion { CARGANDO, NO_SESION, CON_SESION }

data class EstadoRaiz(
    val estado: EstadoSesion = EstadoSesion.CARGANDO,
    val custodio: Custodio? = null,
)

@HiltViewModel
class RootViewModel @Inject constructor(
    sesionRepositorio: SesionRepositorio,
    conectividadMonitor: ConectividadMonitor,
) : ViewModel() {

    val conectado: StateFlow<Boolean> = conectividadMonitor.observadorConectividad()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true,
        )

    val estado: StateFlow<EstadoRaiz> = sesionRepositorio.observarCustodio()
        .map { custodio ->
            if (custodio == null) {
                EstadoRaiz(EstadoSesion.NO_SESION)
            } else {
                EstadoRaiz(EstadoSesion.CON_SESION, custodio)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = EstadoRaiz(EstadoSesion.CARGANDO),
        )
}