package com.ai27.app.custodio.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai27.app.custodio.domain.model.Custodio
import com.ai27.app.custodio.data.conectividad.ConectividadMonitor
import com.ai27.app.custodio.domain.repository.ReporteRepository
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
    private val reporteRepository: ReporteRepository,
) : ViewModel() {

    // U9: la conectividad es TRI-ESTADO. `null` = aún desconocida: afirmar "conectado"
    // en el arranque (initialValue = true) hacía que las pantallas empezaran sin la franja
    // de sin conexión aunque el teléfono estuviera sin red; el monitor emite el primer
    // valor real al suscribirse, y hasta entonces el estado honesto es "no lo sé".
    val conectado: StateFlow<Boolean?> = conectividadMonitor.observadorConectividad()
        .map<Boolean, Boolean?> { it }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    val estado: StateFlow<EstadoRaiz> = sesionRepositorio.observarCustodio()
        .map { custodio ->
            when {
                // PHONE_AUTH: la identidad ES el teléfono. Un custodio sin `telefono`
                // (sesión de correo+contraseña heredada que Firebase restaura en el
                // arranque) no es una sesión válida: se abre el login, y el OTP por
                // teléfono reemplaza esa cuenta al autenticar.
                custodio == null || custodio.telefono.isBlank() ->
                    EstadoRaiz(EstadoSesion.NO_SESION)

                else -> EstadoRaiz(EstadoSesion.CON_SESION, custodio)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = EstadoRaiz(EstadoSesion.CARGANDO),
        )

    /**
     * Sonda de arranque (G3-A/R1): si al entrar con sesión ya hay un viaje activo, la app
     * abre de una en la cámara. Es una consulta única (`obtenerActivo`), no un stream: la
     * raíz solo necesita decidir el destino inicial, no pintar nada.
     */
    suspend fun hayReporteActivo(custodioUid: String): Boolean =
        reporteRepository.obtenerActivo(custodioUid) != null
}