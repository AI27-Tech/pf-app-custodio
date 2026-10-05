package com.ai27.app.custodio.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai27.app.custodio.R
import com.ai27.app.custodio.data.diagnostico.MetodoAuth
import com.ai27.app.custodio.data.diagnostico.RegistroDeEventos
import com.ai27.app.custodio.data.diagnostico.ResultadoLogin
import com.ai27.app.custodio.data.diagnostico.aResultadoLogin
import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.model.Custodio
import com.ai27.app.custodio.domain.repository.AllowlistRepositorio
import com.ai27.app.custodio.domain.repository.SesionRepositorio
import com.ai27.app.custodio.ui.error.aRecurso
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Comprobación mínima de formato de correo, antes de gastar una llamada al backend.
 *
 * No intenta ser un validador RFC 5322: el objetivo es catches el typo obvio (te faltó el
 * arroba, se pegó un espacio) y no dejar queFirebase lo rechace con un error genérico. La
 * autoridad sobre si el correo existe es la allowlist, no este predicado.
 *
 * Puro y sin dependencias de Android, para que se pueda probar en JVM.
 */
internal fun esCorreoValido(correo: String): Boolean =
    Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+").matches(correo)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val sesionRepositorio: SesionRepositorio,
    private val allowlistRepositorio: AllowlistRepositorio,
    private val registroDeEventos: RegistroDeEventos,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onCorreoCambio(correo: String) {
        _uiState.update { it.copy(correoEscrito = correo, error = null) }
    }

    fun onContrasenaCambio(contrasena: String) {
        _uiState.update { it.copy(contrasenaEscrita = contrasena, error = null) }
    }

    /**
     * Único punto de entrada del login: valida, autentica y valida la allowlist. Con
     * correo + contraseña ya no hay fases ni reenvíos, así que no hay más que un método.
     */
    fun iniciarSesion() {
        if (_uiState.value.autenticando) return
        val correo = _uiState.value.correoEscrito
        val contrasena = _uiState.value.contrasenaEscrita
        if (!esCorreoValido(correo)) {
            registrarLogin(ResultadoLogin.CORREO_INVALIDO)
            _uiState.update { it.copy(error = R.string.error_correo_invalido) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(autenticando = true, error = null) }
            when (val resultado = sesionRepositorio.iniciarSesion(correo, contrasena)) {
                is Result.Success -> validarAllowlist(resultado.value)
                is Result.Failure -> {
                    registrarLogin(resultado.error.aResultadoLogin())
                    _uiState.update {
                        it.copy(autenticando = false, error = resultado.error.aRecurso())
                    }
                }
            }
        }
    }

    /**
     * Único lugar donde se escribe el `method` del evento: los puntos de desenlace pasan
     * solo el resultado, para que un typo al registrar no parta la dimensión de la consola
     * en dos (p. ej. `EMAIL` contra `Email`).
     */
    private fun registrarLogin(resultado: ResultadoLogin) =
        registroDeEventos.registroLogin(MetodoAuth.EMAIL, resultado)

    /**
     * Allowlist (opción B del plan): se consulta DESPUÉS de autenticar, para que la regla
     * de lectura de Firestore pueda exigir sesión. Si el correo no está autorizado se
     * cierra la sesión recién abierta y se muestra el error.
     */
    private fun validarAllowlist(custodio: Custodio) {
        viewModelScope.launch {
            when (val resultado = allowlistRepositorio.contiene(custodio.correo)) {
                is Result.Success -> {
                    if (resultado.value) {
                        registrarLogin(ResultadoLogin.OK)
                        return@launch
                    }
                    // Antes del cierre: que la allowlist rechazó es el hecho; que además
                    // se cierre la sesión es consecuencia interna.
                    registrarLogin(ResultadoLogin.CORREO_NO_AUTORIZADO)
                    sesionRepositorio.cerrarSesion()
                    _uiState.update {
                        it.copy(
                            autenticando = false,
                            error = R.string.error_correo_no_autorizado,
                        )
                    }
                }

                is Result.Failure -> {
                    registrarLogin(resultado.error.aResultadoLogin())
                    _uiState.update {
                        it.copy(autenticando = false, error = resultado.error.aRecurso())
                    }
                }
            }
        }
    }
}
