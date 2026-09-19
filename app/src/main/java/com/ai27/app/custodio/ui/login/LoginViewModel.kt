package com.ai27.app.custodio.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.aMensajeUsuario
import com.ai27.app.custodio.domain.repository.SesionRepositorio
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val sesionRepositorio: SesionRepositorio,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onCorreoCambio(correo: String) {
        _uiState.update { it.copy(correoEscrito = correo, error = null) }
    }

    fun onContrasenaCambio(contrasena: String) {
        _uiState.update { it.copy(contrasenaEscrita = contrasena, error = null) }
    }

    fun iniciarSesion() {
        if (_uiState.value.iniciando) return
        val correo = _uiState.value.correoEscrito.trim()
        val contrasena = _uiState.value.contrasenaEscrita
        if (correo.isEmpty()) {
            _uiState.update { it.copy(error = "Escribe tu correo.") }
            return
        }
        if (contrasena.isEmpty()) {
            _uiState.update { it.copy(error = "Escribe tu contraseña.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(iniciando = true, error = null) }
            when (val resultado = sesionRepositorio.iniciarSesion(correo, contrasena)) {
                is Result.Success -> _uiState.update { it.copy(iniciando = false) }
                is Result.Failure -> _uiState.update {
                    it.copy(iniciando = false, error = resultado.error.aMensajeUsuario())
                }
            }
        }
    }
}